/*
 * Copyright 2013-2019 Beapi.io
 * API Chaining(R) 2019 USPTO
 *
 * Licensed under the MIT License;
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.nosegrind.apiframework

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
	String cacheHash
	String contentType
	String apiObject
	String controller
	String action
	String networkGrp
	String authority
	Long userId

	ChainInterceptor(){
		match(uri:"/${entryPoint}/**")
	}

	boolean before() {
		//println('##### CHAININTERCEPTOR (BEFORE)')

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

		//Map methods = ['GET':'show','PUT':'update','POST':'create','DELETE':'delete']


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

		session['cache'] = apiCacheService.getApiCache(params.controller.toString())
		cache = session['cache'] as LinkedHashMap

		// INIT local Chain Variables
		if(chain==null){
			render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Expected chain variables not sent')
			return false
		}
		int inc = 0
		chainKeys[0] = chain['key']
		chainUris[0] = request.forwardURI
		HashMap order = chain.order as HashMap
		order.each(){ key, val ->
			chainOrder[key] = val
			inc++
			chainKeys[inc] = val
			chainUris[inc] = key
		}
		chainLength = inc

		// TODO : test for where chain data was sent
		if(!isChain(contentType)){
			render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Expected request variables for endpoint do not match sent variables')
			return false
		}

		if(cache) {
			params.apiObject = (params.apiObjectVersion) ? params.apiObjectVersion : cache['currentStable']['value']
			apiObject = params.apiObject
			params.action = (params.action == null) ? cache[params.apiObject]['defaultAction'] : params.action
			action = params.action
		}

		this.networkGrp = cache[apiObject][action]['networkGrp']
		this.authority = getUserRole(this.networkGrp) as String

		// CHECK REQUEST VARIABLES MATCH ENDPOINTS EXPECTED VARIABLES
		//String path = "${params.controller}/${params.action}".toString()
		//println(path)


		try{
			if (params.controller == 'apidoc') {
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
						render(status: HttpServletResponse.SC_BAD_REQUEST, text: "Sent request method '${mthdKey}' does not match expected keys : '${acceptableMethod}'")
						return false
					}
				}

				// CHECK FOR REST ALTERNATIVES
				if (restAlt) {
					// PARSE REST ALTS (TRACE, OPTIONS, ETC)
					String result = parseRequestMethod(mthd, params)
					if (result) {
						byte[] contentLength = result.getBytes("ISO-8859-1")
						if (apiThrottle) {
							if (checkLimit(contentLength.length, this.authority)) {
								render(text: result, contentType: contentType)
								return false
							} else {
								render(status: 400, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
								return false
							}
						}else{
							render(text: result, contentType: contentType)
							return false
						}
					}
				}


				if (request?.getAttribute('chainInc') == null) {
					request.setAttribute('chainInc', 0)
				} else {
					Integer newBI = (Integer) request?.getAttribute('chainInc')
					request.setAttribute('chainInc', newBI + 1)

                    List roles = cache[apiObject][action]['roles'] as List
                    if(!checkAuth(roles)){
                    	response.status = 401
                    	response.setHeader('ERROR',"Unauthorized Access attempted at '${entryPoint}/${params.controller}/${params.action}'. This user does not have proper permissions or URL does not exist.")
                    	return false
                    }
				}


				setChainParams(params)

				// CHECK REQUEST VARIABLES MATCH ENDPOINTS EXPECTED VARIABLES
				LinkedHashMap receives = cache[params.apiObject][params.action.toString()]['receives'] as LinkedHashMap
				cacheHash = createCacheHash(params, receives, this.authority)

				//boolean requestKeysMatch = checkURIDefinitions(params, receives)
				if (!checkURIDefinitions(params, receives, this.authority)) {
					render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Expected request variables for endpoint do not match sent variables')
					return false
				}

				// RETRIEVE CACHED RESULT; DON'T CACHE LISTS
				if (cache[apiObject][action]['cachedResult'] && request.method.toUpperCase()=='GET' ) {
					if (cache[apiObject][action]['cachedResult'][cacheHash]) {

						String domain = ((String) params.controller).capitalize()

						JSONObject json = (JSONObject) cache[apiObject][action]['cachedResult'][cacheHash][this.authority][request.format.toUpperCase()]
						if (!json) {
							return false
						} else {
							if (isCachedResult((Integer) json.get('version'), domain)) {

								String result = cache[apiObject][action]['cachedResult'][cacheHash][this.authority][request.format.toUpperCase()] as String
								byte[] contentLength = result.getBytes("ISO-8859-1")
								if (apiThrottle) {
									if (checkLimit(contentLength.length, this.authority)) {
										render(text: result, contentType: contentType)
										return false
									} else {
										render(status: 400, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
										response.flushBuffer()
										return false
									}
								} else {
									render(text: result, contentType: contentType)
									return false
								}
							}
						}
					} else {
						// SET PARAMS AND TEST ENDPOINT ACCESS (PER APIOBJECT)
						ApiDescriptor cachedEndpoint = cache[apiObject][action] as ApiDescriptor
						boolean result = handleRequest(cachedEndpoint['deprecated'] as List)
						return result
					}
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
					WebUtils.exposeRequestAttributes(request, params);
					// this will work fine when we upgrade to newer version that has fix in it
					String forwardUri = "/${entryPoint}/${chainUris[chainInc + 1]}/${newModel.get(params.id)}"
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
								render(text: content, contentType: contentType)
								return false
							} else {
								render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
								return false
							}
						} else {
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
