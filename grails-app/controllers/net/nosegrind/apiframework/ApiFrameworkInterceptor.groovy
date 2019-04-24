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
	SpringSecurityService springSecurityService
	HookService hookService
	StatsService statsService
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
	String authority
	Long userId


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
		//println('##### INTERCEPTOR (BEFORE)')

		// TESTING: SHOW ALL FILTERS IN CHAIN
		//def filterChain = grailsApplication.mainContext.getBean('springSecurityFilterChain')
		//println("FILTERCHAIN : "+filterChain)

		authority = getUserRole() as String
		format = (request?.format)?request.format.toUpperCase():'JSON'
		mthdKey = request.method.toUpperCase()
		mthd = (RequestMethod) RequestMethod[mthdKey]
		apiThrottle = Holders.grailsApplication.config.apiThrottle as boolean
		contentType = request.getContentType()
		userId = springSecurityService.principal['id'] as Long

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
			action = (params.action == null) ? cache[params.apiObject]['defaultAction'] : params.action
		}else{
			action = params?.action
		}

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
				String expectedMethod = cache[apiObject][action]['method'] as String
				if(!checkRequestMethod(mthd,expectedMethod, restAlt)){
					response.status = 400
					response.setHeader('ERROR', 'Expected request method for endpoint does not match sent method')
					response.writer.flush()
					return false
				}

				LinkedHashMap receives = cachedEndpoint['receives'] as LinkedHashMap
				cacheHash = createCacheHash(params, receives)
				if(!checkURIDefinitions(params, receives)){
					response.status = 400
					response.setHeader('ERROR', 'Expected request variables for endpoint do not match sent variables')
					response.writer.flush()
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
								statsService.setStatsCache(userId, response.status, request.requestURI)
								render(text: getContent(result, contentType), contentType: contentType)
							}
						}else{
							statsService.setStatsCache(userId, response.status, request.requestURI)
							render(text: getContent(result, contentType), contentType: contentType)
						}
						return false
					}
				}

				// RETRIEVE CACHED RESULT (only if using get method); DON'T CACHE LISTS
				if (cachedEndpoint.cachedResult && mthdKey=='GET' && cacheHash !=null) {
					LinkedHashMap cachedResult = cachedEndpoint['cachedResult'][cacheHash][this.authority][format] as LinkedHashMap
					if(cachedResult){
						//String authority = getUserRole() as String
						String domain = ((String) controller).capitalize()


						Integer version = cachedResult['version'] as Integer

						if (!cachedResult || cachedResult == null) {
							return false
						} else {
							if (cachedResult.size() > 0) {
								if(isCachedResult(apiObject as Integer, domain)) {
									String output
									switch (format) {
										case 'XML':
											output = cachedResult as XML
											break
										case 'JSON':
										default:
											output = cachedResult as JSON
											break
									}
									byte[] contentLength = output.getBytes('ISO-8859-1')
									if (apiThrottle) {
										if (checkLimit(contentLength.length)) {
											statsService.setStatsCache(userId, response.status, request.requestURI)
											render(text: output, contentType: contentType)
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
										String output
										switch (format) {
											case 'XML':
												output = cachedResult as XML
												break
											case 'JSON':
											default:
												output = cachedResult as JSON
												break
										}
										byte[] contentLength = output.getBytes('ISO-8859-1')
										if (apiThrottle) {
											if (checkLimit(contentLength.length)) {
												statsService.setStatsCache(userId, response.status, request.requestURI)
												render(text: output, contentType: contentType)
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
					}else{
						statsService.setStatsCache(userId, response.status, request.requestURI)
						response.status = 404
						response.setHeader('ERROR', 'No Content found')
						response.writer.flush()
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
					checkAuth(roles)

					boolean result = handleRequest(cachedEndpoint['deprecated'] as List)
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
		//println('##### INTERCEPTOR (AFTER)')

		if(model) {
			//List unsafeMethods = ['PUT', 'POST', 'DELETE']
			Object vals = model.values()
			try {
				LinkedHashMap newModel = [:]
				if (params.controller != 'apidoc') {
					if (!model || vals[0] == null) {
						statsService.setStatsCache(userId, response.status, request.requestURI)
						response.status = 400
						response.setHeader('ERROR', 'No resource returned; query was empty')
						response.writer.flush()
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
					String content
					LinkedHashMap result
					ArrayList responseList = []

					if(controller=='apidoc') {
						content = newModel
					}else{
						List roles = cachedEndpoint['roles'] as List
						LinkedHashMap requestDefinitions = cachedEndpoint['returns'] as LinkedHashMap
						String authority = getUserRole() as String
						response.setHeader('Authorization', roles.join(', '))

						ArrayList<LinkedHashMap> temp = new ArrayList()
						if(requestDefinitions[authority.toString()]) {
							ArrayList<LinkedHashMap> temp1 = requestDefinitions[authority.toString()] as ArrayList<LinkedHashMap>
							temp.addAll(temp1)
						}else{
							ArrayList<LinkedHashMap> temp2 = requestDefinitions['permitAll'] as ArrayList<LinkedHashMap>
							temp.addAll(temp2)
						}

						responseList = (ArrayList)temp?.collect(){ if(it!=null){it.name} }

						result = parseURIDefinitions(newModel, responseList)
						// will parse empty map the same as map with content

						content = parseResponseMethod(mthd, format, params, result)
					}

					//

					byte[] contentLength = content.getBytes('ISO-8859-1')
					if (content) {

						// STORE CACHED RESULT
						//String authority = getUserRole() as String
						String role
						if(request.method.toUpperCase()=='GET') {

							role = (controller == 'apidoc')? 'permitAll' : this.authority


							apiCacheService.setApiCachedResult(cacheHash, controller, apiObject, action, role, this.format, result)
						}

						if (apiThrottle) {
							if(checkLimit(contentLength.length)) {
								statsService.setStatsCache(userId, response.status, request.requestURI)
								render(text: getContent(content, contentType), contentType: contentType)
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
					if(cachedEndpoint['hookRoles']) {
						List hookRoles = cachedEndpoint['hookRoles'] as List
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
