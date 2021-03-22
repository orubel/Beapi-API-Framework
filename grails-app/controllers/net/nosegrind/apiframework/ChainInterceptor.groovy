/*
 * Copyright 2013-2019 Beapi.io
 * API Chaining(R) 2019 USPTO
 *
 * Licensed under the MPL-2.0 License;
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.nosegrind.apiframework

import net.nosegrind.apiframework.StatsService
import org.grails.web.json.JSONObject
import javax.annotation.Resource
import grails.core.GrailsApplication
import grails.plugin.springsecurity.SpringSecurityService
import grails.util.Metadata
import groovy.json.JsonSlurper
import net.nosegrind.apiframework.RequestMethod
import org.grails.web.util.WebUtils

import grails.util.Holders
import javax.servlet.http.HttpServletResponse
import groovy.transform.CompileStatic
import javax.servlet.http.HttpSession

/**
 *
 * HandlerInterceptor for Chained API Calls. Parses XML/JSON, handles authentication, rate limiting, caching, and statistics reporting
 * @author Owen Rubel
 *
 * @see ApiCommLayer
 * @see BatchInterceptor
 * @see ChainInterceptor
 *
 */
@CompileStatic
class ChainInterceptor extends ApiCommLayer implements grails.api.framework.RequestForwarder{

	int order = HIGHEST_PRECEDENCE + 996

	@Resource
	GrailsApplication grailsApplication

	ApiCacheService apiCacheService = new ApiCacheService()
	SpringSecurityService springSecurityService
	StatsService statsService

	// TODO: detect and assign apiObjectVersion from uri
	String entryPoint = "c${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
	String format
	List formats = ['XML', 'JSON']
	String mthdKey
	RequestMethod mthd
	List chainKeys = []
	List chainUris = []
	int chainLength
	LinkedHashMap chainOrder = [:]
	LinkedHashMap cache = [:]
	LinkedHashMap<String,LinkedHashMap<String,String>> chain
	boolean apiThrottle
	List acceptableMethod = ['GET']
	List unacceptableMethod = []
	//String cacheHash
	String contentType
	String apiObject
	String controller
	String action

	ApiDescriptor cachedEndpoint
	ArrayList receivesList = []

	List roles
	List authority
	Long userId
	String networkGrp

	ChainInterceptor(){
		match(uri:"/${entryPoint}/**")
	}

	boolean before() {
		//println('##### CHAININTERCEPTOR (BEFORE)')

		// REMOVE OLD TOKENS
		def tmp = request.getHeader("Authorization")
		if(tmp) {
			String[] auth = tmp.split(" ")
			String token = auth[1]
			apiTokenStorageService.removeOldTokens(token)
		}

		// TESTING: SHOW ALL FILTERS IN CHAIN
		//def filterChain = grailsApplication.mainContext.getBean('springSecurityFilterChain')
		//println(filterChain)

		format = (request?.format) ? (request.format).toUpperCase() : 'JSON'
		mthdKey = request.method.toUpperCase()
		mthd = (RequestMethod) RequestMethod[mthdKey]
		apiThrottle = Holders.grailsApplication.config.apiThrottle as boolean
		contentType = request.getContentType()

		// NEW
		if(!acceptableMethod.contains(mthdKey)){
			acceptableMethod.add(mthdKey)
		}


		// TODO: Check if user in USER roles and if this request puts user over 'rateLimit'

		// Init params
		if (formats.contains(format)) {
			LinkedHashMap attribs = [:]
			switch (format) {
				case 'XML':
					attribs = request.getAttribute('XML') as LinkedHashMap
					break
				case 'JSON':
				default:
					attribs = request.getAttribute('JSON') as LinkedHashMap
					break
			}
			if(attribs){
				attribs.each() { k, v ->
					if(k.toString()=='chain'){
						chain = v as LinkedHashMap
					}else{ params.put(k, v) }
				}
			}
		}


		// INITIALIZE CACHE
		HttpSession session = request.getSession()
		cache = session['cache'] as LinkedHashMap
		controller = params?.controller

		if(cache) {
			apiObject = (params.apiObjectVersion) ? params.apiObjectVersion : cache['currentStable']['value']
			action = (params.action == null) ? cache[apiObject]['defaultAction'] : params.action
		}else{
			action = params?.action
		}

		// INIT local Chain Variables
		if(chain==null){
			statsService.setStatsCache(this.userId, 400, request.requestURI)
			render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Expected chain variables not sent')
			return false
		}

		//HashMap order = [:]
		int inc = 0
		chainKeys[0] = chain['key']
		chainUris[0] = request.forwardURI
		Iterator iter = chain.order.iterator()

		while(iter.hasNext()) {
			Map.Entry entry = iter.next() as Map.Entry
			String key = entry.getKey()
			String val = entry.getValue()
			//order[key]=val
			chainOrder[key] = val
			inc++
			chainKeys[inc] = val
			chainUris[inc] = key
		}

		chainLength = inc

		// TODO : test for where chain data was sent
		if(!isChain(contentType)){
			statsService.setStatsCache(this.userId, 400, request.requestURI)
			render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Expected request variables for endpoint do not match sent variables')
			return false
		}

		this.roles = cache[apiObject][action]['roles'] as List
		this.authority = getUserRole(this.roles)
		this.networkGrp = cache[apiObject][action]['networkGrp']

		// CHECK REQUEST VARIABLES MATCH ENDPOINTS EXPECTED VARIABLES
		//String path = "${params.controller}/${params.action}".toString()
		//println(path)

		try{
			if (controller == 'apidoc') {
				statsService.setStatsCache(this.userId, 400, request.requestURI)
				render(status: 400, text: "API Docs cannot be Chained. Pleased called via the normal method (ie v0.1)")
				return false
			}

			params.max = (params.max==null)?0:params.max
			params.offset = (params.offset==null)?0:params.offset

			if (cache) {
				boolean restAlt = RequestMethod.isRestAlt(mthd.getKey())

				// CHECK REQUEST METHOD FOR ENDPOINT
				// NOTE: expectedMethod must be capitolized in IO State file
				String expectedMethod = cache[apiObject][action.toString()]['method'] as String

				if (!acceptableMethod.contains(mthdKey) || !acceptableMethod.contains(mthdKey)){
					if (!unacceptableMethod.contains(mthdKey)) {
						unacceptableMethod.add(mthdKey)
						statsService.setStatsCache(this.userId, 400, request.requestURI)
						render(status: HttpServletResponse.SC_BAD_REQUEST, text: "Sent request method '${mthdKey}' does not match expected keys : '${acceptableMethod}'")
						return false
					}
				}

				//cacheHash = createCacheHash(params, receivesList)

				// CHECK FOR REST ALTERNATIVES
				if (restAlt) {
					return false
				}

				if (request?.getAttribute('chainInc') == null) {
					request.setAttribute('chainInc', 0)
				} else {
					Integer newBI = (Integer) request?.getAttribute('chainInc')
					request.setAttribute('chainInc', newBI + 1)

					def temp = cache[apiObject][action]['receives'] as LinkedHashMap
					List userRoles = []
					for (Map.Entry<String, String> entry : temp.entrySet()) {
						String key = entry.getKey() as String
						userRoles.add(key)
					}

					boolean checkAuth = checkAuth(userRoles)
					if(!checkAuth){
						statsService.setStatsCache(userId, 400, request.requestURI)
						//errorResponse([400,'Unauthorized Access attempted'])
						return false
					}

				}

				// is this being called
				setChainParams(params)


				// CHECK REQUEST VARIABLES MATCH ENDPOINTS EXPECTED VARIABLES
				LinkedHashMap receives = cache[apiObject][action.toString()]['receives'] as LinkedHashMap
				ArrayList receivesList = []
				this.authority.each(){
					if(receives[it]) {
						receivesList.addAll(receives[it].collect(){ it2 -> it2['name'] })
					}
				}


				//boolean requestKeysMatch = checkURIDefinitions(params, receives)
				if (!checkURIDefinitions(params, receivesList, this.authority)) {
					statsService.setStatsCache(this.userId, 400, request.requestURI)
					errorResponse([400,"Sent params {${params}} do not match expected params {${receivesList}}"])
					return false
				}

				// SET PARAMS AND TEST ENDPOINT ACCESS (PER APIOBJECT)
				cachedEndpoint = cache[apiObject][action] as ApiDescriptor
				boolean result = handleRequest(cachedEndpoint['deprecated'] as List)
				if(result){
					return result
				}
			}
			return false

		} catch (Exception e ) {
			throw new Exception("[ChainInterceptor :: before] : Exception - full stack trace follows:", e)
			return false
		}

	}

	boolean after(){
		//println('##### CHAININTERCEPTOR (AFTER)')

		// getChainVars and reset Chain
		LinkedHashMap<String,LinkedHashMap<String,String>> chain = params.apiChain as LinkedHashMap

		int chainInc = (int) request.getAttribute('chainInc')

		try{
			LinkedHashMap newModel = [:]

			if (!model) {
				statsService.setStatsCache(this.userId, 404, request.requestURI)
				render(status:HttpServletResponse.SC_NOT_FOUND , text: 'No resource returned')
				return false
			} else {
				newModel = convertModel(model)
			}

			//LinkedHashMap cache = apiCacheService.getApiCache(params.controller.toString())
			//LinkedHashMap content

			ApiDescriptor cachedEndpoint = cache[apiObject][action] as ApiDescriptor

			// TEST FOR NESTED MAP; WE DON'T CACHE NESTED MAPS
			boolean isNested = false
			if (newModel != [:]) {
				Object key = newModel?.keySet()?.iterator()?.next()
				if (newModel[key].getClass().getName() == 'java.util.LinkedHashMap') {
					isNested = true
				}


				//if(chainEnabled && params?.apiChain?.order){

				params.id = ((chainInc + 1) == 1) ? chainKeys[0] : chainKeys[(chainInc)]
				if (chainEnabled && (chainLength >= (chainInc + 1)) && params.id!='return') {
					WebUtils.exposeRequestAttributesAndReturnOldValues(request, params);
					// this will work fine when we upgrade to newer version that has fix in it
					String forwardUri = "/${entryPoint}/${chainUris[chainInc + 1]}/${newModel.get(params.id)}"
					statsService.setStatsCache(this.userId, response.status, request.requestURI)
					forward(URI: forwardUri, params: [apiObject: apiObject, apiChain: params.apiChain])
					return false
				} else {
					String content = handleChainResponse(this.authority, cachedEndpoint['returns'] as LinkedHashMap, cachedEndpoint['roles'] as List, mthd, format, response, newModel, params)

					byte[] contentLength = content.getBytes( "ISO-8859-1" )
					if(content) {
						// STORE CACHED RESULT
						String format = request.format.toUpperCase()

						//if (!newModel && request.method.toUpperCase()=='GET') {
						//	apiCacheService.setApiCachedResult(cacheHash,(String) params.controller, (String) params.apiObject, (String) params.action, authority, format, content)
						//}

						if (apiThrottle) {
							if (checkLimit(contentLength.length, this.authority)) {
								statsService.setStatsCache(this.userId, response.status, request.requestURI)
								render(text: content, contentType: contentType)
								return false
							} else {
								statsService.setStatsCache(this.userId, 400, request.requestURI)
								render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
								return false
							}
						} else {
							statsService.setStatsCache(this.userId, response.status, request.requestURI)
							render(text: content, contentType: contentType)
							return false
						}
					}
				}

				return false
			}

			return false
		}catch(Exception e){
			throw new Exception("[ChainInterceptor :: after] : Exception - full stack trace follows:", e)
			return false
		}

	}

}
