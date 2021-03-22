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
import javax.servlet.http.HttpSession

@CompileStatic
class ProfilerInterceptor extends ProfilerCommLayer{

	int order = HIGHEST_PRECEDENCE + 997

	@Resource
	GrailsApplication grailsApplication

	@Resource
	TraceService traceService

	ApiCacheService apiCacheService = new ApiCacheService()
	SpringSecurityService springSecurityService

	boolean apiThrottle
	String cacheHash

	// TODO: detect and assign apiObjectVersion from uri
	String entryPoint = "p${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
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
	 * Constructor for ProfilerInterceptor. Matches on entrypoint (ie v0.1 for example)
	 * @return
	 */
	ProfilerInterceptor(){
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
		// println("##### PROFILERINTERCEPTOR (BEFORE) - ${params.controller}/${params.action}")

		// REMOVE OLD TOKENS
		def tmp = request.getHeader("Authorization")
		if(tmp) {
			String[] auth = tmp.split(" ")
			String token = auth[1]
			apiTokenStorageService.removeOldTokens(token)
		}

		traceService.startTrace('ProfilerInterceptor','before')

		// TESTING: SHOW ALL FILTERS IN CHAIN
		//def filterChain = grailsApplication.mainContext.getBean('springSecurityFilterChain')
		//println("FILTERCHAIN : "+filterChain)

		format = (request?.format)?request.format.toUpperCase():'JSON'
		mthdKey = request.method.toUpperCase()
		mthd = (RequestMethod) RequestMethod[mthdKey]
		apiThrottle = grailsApplication.config.apiThrottle as boolean
		contentType = request.getContentType()
		this.userId = springSecurityService.principal['id'] as Long

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
					traceService.endTrace('ProfilerInterceptor','before')
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

				cacheHash = createCacheHash(params, receivesList, this.authority)
				if(!checkURIDefinitions(params, receivesList, this.authority)){
					traceService.endTrace('ProfilerInterceptor','before')
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
								traceService.endTrace('ProfilerInterceptor','before')
								LinkedHashMap traceContent = traceService.endAndReturnTrace('ProfilerInterceptor','before')
								String tcontent = traceContent as JSON
								render(text: tcontent, contentType: "application/json")
							}else{
								traceService.endTrace('ProfilerInterceptor','before')
								return false
							}
						}else{
							traceService.endTrace('ProfilerInterceptor','before')

							LinkedHashMap traceContent = traceService.endAndReturnTrace('ProfilerInterceptor','before')
							String tcontent = traceContent as JSON
							render(text: tcontent, contentType: "application/json")
						}
						traceService.endTrace('ProfilerInterceptor','before')
						return false
					}
				}


				// RETRIEVE CACHED RESULT (only if using get method); DON'T CACHE LISTS
				if (this.cachedEndpoint?.cachedResult && mthdKey=='GET' && cacheHash !=null && cachedEndpoint['cachedResult'][cacheHash]) {

						LinkedHashMap cachedResult = this.cachedEndpoint['cachedResult'][cacheHash][this.networkGrp][format] as LinkedHashMap
						String domain = ((String) controller).capitalize()


						Integer version = cachedResult['version'] as Integer

						if (!cachedResult || cachedResult == null) {
							traceService.endTrace('ProfilerInterceptor','before')
							return false
						} else {
							if (cachedResult.size() > 0) {
								if(isCachedResult(apiObject as Integer, domain)) {
									String output = (format == 'XML')?cachedResult as XML:cachedResult as JSON

									byte[] contentLength = output.getBytes('ISO-8859-1')
									if (apiThrottle) {
										if (checkLimit(contentLength.length, this.authority)) {
											traceService.endTrace('ProfilerInterceptor','before')
											LinkedHashMap traceContent = traceService.endAndReturnTrace('ProfilerInterceptor','before')
											String tcontent = traceContent as JSON
											render(text: tcontent, contentType: "application/json")
											return false
										}else{
											traceService.endTrace('ProfilerInterceptor','before')
											return false
										}
									} else {
										traceService.endTrace('ProfilerInterceptor','before')

										LinkedHashMap traceContent = traceService.endAndReturnTrace('ProfilerInterceptor','before')
										String tcontent = traceContent as JSON
										render(text: tcontent, contentType: "application/json")
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
												traceService.endTrace('ProfilerInterceptor','after')
												LinkedHashMap traceContent = traceService.endAndReturnTrace('ProfilerInterceptor','after')
												String tcontent = traceContent as JSON
												render(text: getContent(tcontent, "application/json"), contentType: "application/json")
												return false
											}else{
												traceService.endTrace('ProfilerInterceptor','before')
												return false
											}
										} else {
											traceService.endTrace('ProfilerInterceptor','before')
											LinkedHashMap traceContent = traceService.endAndReturnTrace('ProfilerInterceptor','before')
											String tcontent = traceContent as JSON
											render(text: getContent(tcontent, "application/json"), contentType: "application/json")
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
								traceService.endTrace('ProfilerInterceptor','before')
								forward(controller: controller, action: action)
								return false
							}
						}
						action = params.action.toString()
					}

					//List roles = cache['roles'] as List
					List roles = this.cachedEndpoint['roles'] as List
					boolean checkAuth = checkAuth(roles)
					if(!checkAuth){
						traceService.endTrace('ProfilerInterceptor','before')
						errorResponse([400,'Unauthorized Access attempted'])
						return false
					}

					boolean result = handleRequest(this.cachedEndpoint['deprecated'] as List)
					if(result){
						traceService.endTrace('ProfilerInterceptor','before')
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
		traceService.startTrace('ProfilerInterceptor','after')

		if(model) {
			//List unsafeMethods = ['PUT', 'POST', 'DELETE']

			try {
				// convert model to standardized object
				LinkedHashMap newModel = [:]
				if (params.controller != 'apidoc') {
					Object vals = model.values()
					if (!model || vals==null) {
						traceService.endAndReturnTrace('ProfilerInterceptor','after')
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
								LinkedHashMap traceContent = traceService.endAndReturnTrace('ProfilerInterceptor','after')
								String tcontent = traceContent as JSON
								render(text: tcontent, contentType: "application/json")
								return false
							}else{
								LinkedHashMap traceContent = traceService.endAndReturnTrace('ProfilerInterceptor','after')
								String tcontent = traceContent as JSON
								render(text: tcontent, contentType: "application/json")
								return false
							}
						} else {
							LinkedHashMap traceContent = traceService.endAndReturnTrace('ProfilerInterceptor','after')
							String tcontent = traceContent as JSON
							//render(text: getContent(tcontent, "application/json"), contentType: "application/json")
							render(text: tcontent, contentType: "application/json")
							return false
						}
					}
				} else {
					LinkedHashMap traceContent = traceService.endAndReturnTrace('ProfilerInterceptor','after')
					String tcontent = traceContent as JSON
					render(text: tcontent, contentType: "application/json")
					//response.flushBuffer()
				}
				//traceService.endTrace('ProfilerInterceptor','after')
				return false

			} catch (Exception e) {
				throw new Exception("[ApiToolkitFilters :: apitoolkit.after] : Exception - full stack trace follows:", e)
				return false
			}

		}

		//traceService.endTrace('ProfilerInterceptor','after')
		return false
	}


}
