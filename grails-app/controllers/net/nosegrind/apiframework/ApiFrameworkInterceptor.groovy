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

import javax.annotation.Resource
import grails.core.GrailsApplication
import grails.plugin.springsecurity.SpringSecurityService

import grails.util.Metadata
import grails.converters.JSON
import grails.converters.XML
import org.grails.web.json.JSONObject

import grails.util.Holders

import javax.servlet.http.HttpServletResponse
import groovy.transform.CompileStatic
import net.nosegrind.apiframework.HookService
import net.nosegrind.apiframework.StatsService
import javax.servlet.http.HttpSession



/**
 *
 * HandlerInterceptor for Basic API Calls. Parses XML/JSON, handles authentication, rate limiting, caching, and statistics reporting
 * @author Owen Rubel
 *
 * @see ApiCommLayer
 * @see BatchInterceptor
 * @see ChainInterceptor
 *
 */
@CompileStatic
class ApiFrameworkInterceptor extends ApiCommLayer{

	int order = HIGHEST_PRECEDENCE + 999


	@Resource
	GrailsApplication grailsApplication
	ApiCacheService apiCacheService = new ApiCacheService()

	// sec checks done in extended classes but service needs to be HERE
	SpringSecurityService springSecurityService

	HookService hookService
	StatsService statsService
	IpSecService ipSecService

	boolean apiThrottle
	String cacheHash

	// TODO: detect and assign apiObjectVersion from uri
	String entryPoint = "v${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
	String format
	List formats = ['XML', 'JSON']
	String mthdKey
	RequestMethod mthd
	LinkedHashMap cache = [:]

	LinkedHashMap stat = [:]
	String contentType
	String apiObject
	String controller
	String action

	ApiDescriptor cachedEndpoint

	List roles
	List authority
	Long userId
	String networkGrp

	/**
	 * Constructor for ApiFrameworkInterceptor. Matches on entrypoint (ie v0.1 for example)
	 * @return
	 */
	ApiFrameworkInterceptor(){
		match(uri:"/${entryPoint}/**")
		match(uri:"/${entryPoint}-1/**")
		match(uri:"/${entryPoint}-2/**")
		match(uri:"/${entryPoint}-3/**")
		match(uri:"/${entryPoint}-4/**")
		match(uri:"/${entryPoint}-5/**")
		match(uri:"/${entryPoint}-6/**")
		match(uri:"/${entryPoint}-7/**")
		match(uri:"/${entryPoint}-8/**")
		match(uri:"/${entryPoint}-9/**")
	}

	/**
	 * PreHandler for the HandlerInterceptor
	 * @return
	 */
	boolean before(){
		//println("##### INTERCEPTOR (BEFORE) - ${params.controller}/${params.action}")

		// IPSEC BLOCK
		String ip = getClientIp()
		ipSecService.check(ip)


		
		// TESTING: SHOW ALL FILTERS IN CHAIN
		//def filterChain = grailsApplication.mainContext.getBean('springSecurityFilterChain')
		//println("FILTERCHAIN : "+filterChain)

		format = (request?.format)?request.format.toUpperCase():'JSON'
		mthdKey = request.method.toUpperCase()
		mthd = (RequestMethod) RequestMethod[mthdKey]
		apiThrottle = grailsApplication.config.apiThrottle as boolean
		contentType = request.getContentType()

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
			attribs.each() { k, v ->
				params.put(k, v)
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

		this.cachedEndpoint = cache[apiObject][action] as ApiDescriptor
		this.roles = cache[apiObject][action]['roles'] as List
		this.authority = getUserRole(this.roles)
		this.networkGrp = cache[apiObject][action]['networkGrp']


		try{
			//Test For APIDoc
			if(controller=='apidoc') { return true }

			params.max = (params.max==null)?0:params.max
			params.offset = (params.offset==null)?0:params.offset


			if(cache) {
				boolean restAlt = RequestMethod.isRestAlt(mthd.getKey())

				//CHECK REQUEST METHOD FOR ENDPOINT
				// NOTE: expectedMethod must be capitolized in IO State file
				String expectedMethod = this.cachedEndpoint['method'] as String
				if(!checkRequestMethod(mthd,expectedMethod, restAlt)){
					response.writer.flush()
					return false
				}

				/**
				 * Walk through authoprities and get list of 'receives' params from
				 * IO state for endpoint being called
				 */
				LinkedHashMap receives = this.cachedEndpoint['receives'] as LinkedHashMap
				ArrayList receivesList = []
				this.authority.each(){
					if(receives[it]) {
						receivesList.addAll(receives[it].collect(){ it2 -> it2['name'] })
					}
				}

				cacheHash = createCacheHash(params, receivesList)
				
				/**
				 * Only do this for GET, PUT, POST,DELETE; NOT PATCH!!!
				 */
				if(!checkURIDefinitions(params, receivesList, this.authority)){
					statsService.setStatsCache(userId, response.status, request.requestURI)
					return false
				}


				// CHECK FOR REST ALTERNATIVES
				if (restAlt) {
					// PARSE REST ALTS (TRACE, OPTIONS, ETC)
					String result = parseRequestMethod(mthd, params)

					if (result) {
						byte[] contentLength = result.getBytes('ISO-8859-1')
						if (apiThrottle) {
							if (checkLimit(contentLength.length, this.authority)) {

								statsService.setStatsCache(userId, response.status, request.requestURI)
								render(text: getContent(result, contentType), contentType: contentType)
							}else{
								statsService.setStatsCache(userId, 404, request.requestURI)
								return false
							}
						}else{
							statsService.setStatsCache(userId, response.status, request.requestURI)
							render(text: getContent(result, contentType), contentType: contentType)
						}
						return false
					}
				}


				// RETRIEVE CACHED RESULT (only if using get method); DON'T CACHE LISTS
				if (mthdKey=='GET' && this.cachedEndpoint?.cachedResult &&  cacheHash !=null && cachedEndpoint['cachedResult'][cacheHash]) {
						LinkedHashMap cachedResult = this.cachedEndpoint['cachedResult'][cacheHash][this.networkGrp][format] as LinkedHashMap
						String domain = ((String) controller).capitalize()

						Integer version = cachedResult['version'] as Integer

						if (!cachedResult || cachedResult == null) {
							return false
						} else {
							if (cachedResult.size() > 0) {
								if(isCachedResult(apiObject as Integer, domain)) {
									String output = (format == 'XML')?cachedResult as XML:cachedResult as JSON

									byte[] contentLength = output.getBytes('ISO-8859-1')
									if (apiThrottle) {
										if (checkLimit(contentLength.length, this.authority)) {
											statsService.setStatsCache(userId, response.status, request.requestURI)
											render(text: output, contentType: contentType)
											return false
										}else{
											statsService.setStatsCache(userId, 404, request.requestURI)
											return false
										}
									} else {
										statsService.setStatsCache(userId, response.status, request.requestURI)
										render(text: output, contentType: contentType)
										return false
									}
								}
							} else {
								if (version != null) {
									if (isCachedResult(apiObject as Integer, domain)) {
										String output = (format == 'XML')?cachedResult as XML:cachedResult as JSON

										byte[] contentLength = output.getBytes('ISO-8859-1')
										if (apiThrottle) {
											if (checkLimit(contentLength.length, this.authority)) {
												statsService.setStatsCache(userId, response.status, request.requestURI)
												render(text: output, contentType: contentType)
												return false
											}else{
												statsService.setStatsCache(userId, 404, request.requestURI)
												return false
											}
										} else {
											statsService.setStatsCache(userId, response.status, request.requestURI)
											render(text: output, contentType: contentType)
											return false
										}
									}
								}
							}
						}

				} else {
					if (action == null || !action) {
						String methodAction = mthd.toString()
						if (!cache[apiObject][methodAction]) {
							params.action = cache[apiObject]['defaultAction']
						} else {
							params.action = mthd.toString()

							// FORWARD FOR REST DEFAULTS WITH NO ACTION
							String[] tempUri = request.getRequestURI().split('/')
							if (tempUri[2].contains('dispatch') && "${controller}.dispatch" == tempUri[2]) {
								forward(controller: controller, action: action)
								return false
							}
						}
						action = params.action.toString()
					}


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

					boolean result = handleRequest(this.cachedEndpoint['deprecated'] as List)
					if(result){
						return result
					}
				}
			}

			// no cache found

			return false

		}catch(Exception e){
			throw new Exception('[ApiToolkitFilters :: preHandler] : Exception - full stack trace follows:', e)
			return false
		}
	}

	/**
	 * PostHandler
	 * @return
	 */
	boolean after() {
		//println("##### INTERCEPTOR (AFTER) - ${params.controller}/${params.action}")

		if(model) {
			//List unsafeMethods = ['PUT', 'POST', 'DELETE']

			try {
				// convert model to standardized object
				LinkedHashMap newModel = [:]
				if (params.controller != 'apidoc') {
					Object vals = model.values()
					if (!model || vals==null) {
						statsService.setStatsCache(userId, 400, request.requestURI)
						errorResponse([400,'No resource returned; query was empty'])
						return false
					} else {
						newModel = convertModel(model)
					}
				} else {
					newModel = model as LinkedHashMap
				}

				//ApiDescriptor cachedEndpoint
				//if(cache) {
				//	cachedEndpoint = cache[apiObject][action] as ApiDescriptor
				//}

				// TEST FOR NESTED MAP; WE DON'T CACHE NESTED MAPS
				//boolean isNested = false
				if (newModel != [:]) {
					String content
					LinkedHashMap result
					ArrayList responseList = []

					if(controller=='apidoc') {
						content = newModel
					}else{
						List roles = this.cachedEndpoint['roles'] as List
						LinkedHashMap requestDefinitions = this.cachedEndpoint['returns'] as LinkedHashMap
						response.setHeader('Authorization', roles.join(', '))

						this.authority.each(){
							if(requestDefinitions[it]) {
								responseList.addAll(requestDefinitions[it].collect(){ it2 ->  if(it2!=null){ it2['name'] }})
							}
						}

						result = parseURIDefinitions(newModel, responseList)
						// will parse empty map the same as map with content

						content = parseResponseMethod(mthd, format, params, result)
					}


					byte[] contentLength = content.getBytes('ISO-8859-1')
					if (content) {

						// STORE CACHED RESULT
						//String authority = getUserRole(this.roles) as String
						String role
						if(request.method.toUpperCase()=='GET') {
							role = (controller == 'apidoc')? 'permitAll' : this.authority
							apiCacheService.setApiCachedResult(cacheHash, controller, apiObject, action, this.networkGrp, this.format, result)
						}

						if (apiThrottle) {
							if(checkLimit(contentLength.length,this.authority)) {
								statsService.setStatsCache(userId, response.status, request.requestURI)
								render(text: getContent(content, contentType), contentType: contentType)
								return false
							}else{
								statsService.setStatsCache(userId, 404, request.requestURI)
								return false
							}
						} else {
							if(controller=='apidoc') {
								render(text: newModel as JSON, contentType: contentType)
							}else {
								render(text: content, contentType: contentType)
							}
							if(cachedEndpoint['hookRoles']) {
								List hookRoles = cachedEndpoint['hookRoles'] as List
								String service = "${controller}/${action}"
								hookService.postData(service, content, hookRoles, this.mthdKey)
							}
						}
					}
				} else {
					String content = parseResponseMethod(mthd, format, params, newModel)
					statsService.setStatsCache(userId, response.status, request.requestURI)

					render(text: getContent(content, contentType), contentType: contentType)
					if(this.cachedEndpoint['hookRoles']) {
						List hookRoles = this.cachedEndpoint['hookRoles'] as List
						String service = "${controller}/${action}"
						hookService.postData(service, content, hookRoles, this.mthdKey)
					}
					//response.flushBuffer()
				}
				return false

			} catch (Exception e) {
				throw new Exception("[ApiToolkitFilters :: apitoolkit.after] : Exception - full stack trace follows:", e)
				return false
			}

		}
		return false
	}


}
