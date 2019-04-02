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
import org.springframework.http.HttpStatus
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
	SpringSecurityService springSecurityService
	HookService hookService
	//StatsService statsService
	boolean apiThrottle
	String cacheHash

	// TODO: detect and assign apiObjectVersion from uri
	String entryPoint = "v${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
	String format
	List formats = ['XML', 'JSON']
	String mthdKey
	RequestMethod mthd
	LinkedHashMap cache = [:]
	grails.config.Config conf = Holders.grailsApplication.config
	LinkedHashMap stat = [:]
	String contentType
	String apiObject
	String controller
	String action
	ApiDescriptor cachedEndpoint

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
		//println('##### FILTER (BEFORE)')

		// TESTING: SHOW ALL FILTERS IN CHAIN
		//def filterChain = grailsApplication.mainContext.getBean('springSecurityFilterChain')
		//println("FILTERCHAIN : "+filterChain)


		format = (request?.format)?request.format.toUpperCase():'JSON'
		mthdKey = request.method.toUpperCase()
		mthd = (RequestMethod) RequestMethod[mthdKey]
		apiThrottle = this.conf.apiThrottle as boolean
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
			if(attribs){
				attribs.each() { k, v ->
					params.put(k, v)
				}
			}
		}

		// INITIALIZE CACHE
		HttpSession session = request.getSession()
		cache = session['cache'] as LinkedHashMap

		if(cache) {
			apiObject = (params.apiObjectVersion) ? params.apiObjectVersion : cache['currentStable']['value']
			action = (params.action == null) ? cache[params.apiObject]['defaultAction'] : params.action

			//params.apiObject = (params.apiObjectVersion) ? params.apiObjectVersion : cache['currentStable']['value']
			//params.action = (params.action == null) ? cache[params.apiObject]['defaultAction'] : params.action
		}else{
			action = params?.action
		}
		controller = params?.controller

		cachedEndpoint = cache[apiObject][action] as ApiDescriptor

		try{
			//Test For APIDoc
			if(controller=='apidoc') { return true }

			params.max = (params.max==null)?0:params.max
			params.offset = (params.offset==null)?0:params.offset

			if(cache) {
				boolean restAlt = RequestMethod.isRestAlt(mthd.getKey())

				//CHECK REQUEST METHOD FOR ENDPOINT
				// NOTE: expectedMethod must be capitolized in IO State file
				String expectedMethod = cachedEndpoint['method'] as String
				if (!checkRequestMethod(mthd,expectedMethod, restAlt)) {
					//statsService.setStatsCache(getUserId(), 400)
					render(status: 400, text: "Expected request method '${expectedMethod}' does not match sent method '${mthd.getKey()}'")
					return false
				}


				// CHECK FOR REST ALTERNATIVES
				if (restAlt) {
					// PARSE REST ALTS (TRACE, OPTIONS, ETC)
					String result = parseRequestMethod(mthd, params)

					if (result) {
						byte[] contentLength = result.getBytes('ISO-8859-1')

						if (apiThrottle) {
							if (checkLimit(contentLength.length)) {
								//statsService.setStatsCache(getUserId(), response.status)
								render(text: result, contentType: contentType)
							} else {
								//statsService.setStatsCache(getUserId(), 400)
								render(status: 400, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
							}
						}else{
							//statsService.setStatsCache(getUserId(), response.status)
							render(text: result, contentType: contentType)
						}
						return false
					}
				}

				LinkedHashMap receives = cachedEndpoint['receives'] as LinkedHashMap
				cacheHash = createCacheHash(params, receives)

				//boolean requestKeysMatch = checkURIDefinitions(params, receives)

				if (!checkURIDefinitions(params, receives)) {
					//statsService.setStatsCache(getUserId(), HttpStatus.BAD_REQUEST.value())
					render(status: HttpStatus.BAD_REQUEST.value(), text: 'Expected request variables for endpoint do not match sent variables')
					response.flushBuffer()
					return false
				}

				// RETRIEVE CACHED RESULT (only if using get method); DON'T CACHE LISTS

				if (cachedEndpoint['cachedResult'] && mthdKey=='GET' ) {
					if(cachedEndpoint['cachedResult'][cacheHash]){

						String authority = getUserRole() as String
						String domain = ((String) controller).capitalize()

						//List roles = cache['roles'] as List
						//List roles = cache[apiObject][action]['roles'] as List
						//if(!checkAuth(roles)){
						//	response.status = 401
						//	response.setHeader('ERROR','Unauthorized Access attempted')
						//	return false
						//}

						JSONObject json = (JSONObject) cachedEndpoint['cachedResult'][cacheHash][authority][format]
						if (!json || json == null) {
							return false
						} else {
							Set keys = json.keySet()
							String temp = keys.iterator().next()
							boolean first = json.get(temp)

							// is a List of objects
							if (first instanceof JSONObject && first.size() > 0 && !first.isEmpty()) {

								JSONObject jsonObj = ((JSONObject) json.get('0'))
								int version = jsonObj.get('version') as Integer

								if (isCachedResult((Integer) version, domain)) {
									LinkedHashMap result = cachedEndpoint['cachedResult'][cacheHash][authority][format] as LinkedHashMap
									String content = new groovy.json.JsonBuilder(result).toString()
									byte[] contentLength = content.getBytes('ISO-8859-1')
									if (apiThrottle) {
										if (checkLimit(contentLength.length)) {
											//statsService.setStatsCache(getUserId(), response.status)
											render(text: result as JSON, contentType: contentType)
											return false
										} else {
											//statsService.setStatsCache(getUserId(), 400)
											render(status: 400, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
											response.flushBuffer()
											return false
										}
									} else {
										//statsService.setStatsCache(getUserId(), response.status)
										render(text: result as JSON, contentType: contentType)
										return false
									}
								}
							} else {
								if (json.version != null) {
									if (isCachedResult((Integer) json.get('version'), domain)) {
										LinkedHashMap result = cachedEndpoint['cachedResult'][cacheHash][authority][format] as LinkedHashMap
										String content = new groovy.json.JsonBuilder(result).toString()
										byte[] contentLength = content.getBytes('ISO-8859-1')
										if (apiThrottle) {
											if (checkLimit(contentLength.length)) {
												//statsService.setStatsCache(getUserId(), response.status)
												render(text: result as JSON, contentType: contentType)
												return false
											} else {
												//statsService.setStatsCache(getUserId(), 400)
												render(status: 400, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
												response.flushBuffer()
												return false
											}
										} else {
											//statsService.setStatsCache(getUserId(), response.status)
											render(text: result as JSON, contentType: contentType)
											return false
										}
									}
								}
							}
						}
					}else{
						render(status: 404, text: 'No content found')
						response.flushBuffer()
						return false
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

					//List roles = cache['roles'] as List
					List roles = cachedEndpoint['roles'] as List
					if(!checkAuth(roles)){
						response.status = 401
						response.setHeader('ERROR','Unauthorized Access attempted')
						return false
					}

					// SET PARAMS AND TEST ENDPOINT ACCESS (PER APIOBJECT)
					//ApiDescriptor cachedEndpoint = cache[apiObject][action] as ApiDescriptor
					boolean result = handleApiRequest(cachedEndpoint['deprecated'] as List, (cachedEndpoint['method'])?.toString(), mthd, response, params)
					return result
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
		//println('##### FILTER (AFTER)')

		if(model) {
			//List unsafeMethods = ['PUT', 'POST', 'DELETE']
			Object vals = model.values()
			try {
				LinkedHashMap newModel = [:]
				if (params.controller != 'apidoc') {
					if (!model || vals[0] == null) {
						//statsService.setStatsCache(getUserId(), HttpServletResponse.SC_NOT_FOUND)
						render(status: HttpServletResponse.SC_NOT_FOUND, text: 'No resource returned / domain is empty')
						response.flushBuffer()
						return false
					} else {
						newModel = convertModel(model)
					}
				} else {
					newModel = model as LinkedHashMap
				}

				//ApiDescriptor cachedEndpoint
				if(cache) {
					cachedEndpoint = cache[apiObject][action] as ApiDescriptor
				}

				// TEST FOR NESTED MAP; WE DON'T CACHE NESTED MAPS
				//boolean isNested = false
				if (newModel != [:]) {

					String content = handleApiResponse(cachedEndpoint['returns'] as LinkedHashMap, cachedEndpoint['roles'] as List, mthd, format, response, newModel, params)

					byte[] contentLength = content.getBytes('ISO-8859-1')
					if (content) {

						// STORE CACHED RESULT
						//String format = request.format.toUpperCase()
						String authority = getUserRole() as String
						String role
						if(request.method.toUpperCase()=='GET') {
							role = (controller == 'apidoc')? 'permitAll' : authority
							apiCacheService.setApiCachedResult(cacheHash, controller, apiObject, action, role, this.format, content)
						}

						if (apiThrottle) {
							if (checkLimit(contentLength.length)) {
								//statsService.setStatsCache(getUserId(), response.status)
								render(text: content, contentType: contentType)
								response.flushBuffer()
								return false
							} else {
								//statsService.setStatsCache(getUserId(), HttpServletResponse.SC_BAD_REQUEST)
								render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
								response.flushBuffer()
								return false
							}
						} else {
							//statsService.setStatsCache(getUserId(), response.status)
							render(text: content, contentType: contentType)
							if(cachedEndpoint['hookRoles']) {
								List hookRoles = cachedEndpoint['hookRoles'] as List
								String service = "${controller}/${action}"
								hookService.postData(service, content, hookRoles, this.mthdKey)
							}
						}
					}
				} else {
					//statsService.setStatsCache(getUserId(), response.status)
					String content = parseResponseMethod(mthd, format, params, newModel)
					render(text: content, contentType: contentType)
					if(cachedEndpoint['hookRoles']) {
						List hookRoles = cachedEndpoint['hookRoles'] as List
						String service = "${controller}/${action}"
						hookService.postData(service, content, hookRoles, this.mthdKey)
					}
					response.flushBuffer()
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
