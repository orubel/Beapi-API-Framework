package net.nosegrind.apiframework

import javax.annotation.Resource
import grails.core.GrailsApplication
//import net.nosegrind.apiframework.ApiDescriptor
import grails.plugin.springsecurity.SpringSecurityService

//import net.nosegrind.apiframework.RequestMethod

import javax.servlet.ServletInputStream
import com.google.common.io.CharStreams
import groovy.json.JsonSlurper
import java.io.InputStreamReader

import grails.util.Metadata
//import groovy.json.internal.LazyMap
import grails.converters.JSON
import grails.converters.XML
import org.grails.web.json.JSONObject

import grails.util.Holders
//import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import groovy.transform.CompileStatic
import org.springframework.http.HttpStatus
import net.nosegrind.apiframework.HookService
import net.nosegrind.apiframework.StatsService
import org.springframework.web.context.request.RequestContextHolder as RCH
import javax.servlet.http.HttpSession

/**
 *
 * HandlerInterceptor for Basic API Calls. Parses XML/JSON, handles authentication, rate limiting, caching, and statistics reporting
 *
 *
 * @see ApiCommLayer
 * @see BatchkInterceptor
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
	boolean notApiDoc=true
	LinkedHashMap stat = [:]
	String remoteAddr

	/**
	 * Constructor for ApiFrameworkInterceptor. Matches on entrypoint (ie v0.1 for example)
	 * @return
	 */
	ApiFrameworkInterceptor(){
		match(uri:"/${entryPoint}/**")
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

		remoteAddr = getRemoteAddr(request)
		format = (request?.format)?request.format.toUpperCase():'JSON'
		mthdKey = request.method.toUpperCase()
		mthd = (RequestMethod) RequestMethod[mthdKey]

		apiThrottle = this.conf.apiThrottle as boolean


		boolean restAlt = RequestMethod.isRestAlt(mthd.getKey())

		// TODO: Check if user in USER roles and if this request puts user over 'rateLimit'

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
		def session = request.getSession()
		cache = session['cache'] as LinkedHashMap

		if(cache) {
			params.apiObject = (params.apiObjectVersion) ? params.apiObjectVersion : cache['currentStable']['value']
			params.action = (params.action == null) ? cache[params.apiObject]['defaultAction'] : params.action
		}

		try{
			//Test For APIDoc
			if(params.controller=='apidoc') {
				notApiDoc=false
				return true
			}

			if(cache) {
				//CHECK REQUEST METHOD FOR ENDPOINT
				// NOTE: expectedMethod must be capitolized in IO State file
				String expectedMethod = cache[params.apiObject][params.action.toString()]['method'] as String
				if (!checkRequestMethod(mthd,expectedMethod, restAlt)) {
					//statsService.setStatsCache(getUserId(), 400)
					render(status: 400, text: "Expected request method '${expectedMethod}' does not match sent method '${mthd.getKey()}'")
					return false
				}

				params.max = (params.max!=null)?params.max:0
				params.offset = (params.offset!=null)?params.offset:0

				// CHECK FOR REST ALTERNATIVES
				if (restAlt) {
					// PARSE REST ALTS (TRACE, OPTIONS, ETC)
					String result = parseRequestMethod(mthd, params)

					if (result) {
						byte[] contentLength = result.getBytes("ISO-8859-1")

						if (apiThrottle) {
							if (checkLimit(contentLength.length)) {
								//statsService.setStatsCache(getUserId(), response.status)
								render(text: result, contentType: request.getContentType())
								return false
							} else {
								//statsService.setStatsCache(getUserId(), 400)
								render(status: 400, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
								return false
							}
						}else{
							//statsService.setStatsCache(getUserId(), response.status)
							render(text: result, contentType: request.getContentType())
							return false
						}
					}
				}

				LinkedHashMap receives = cache[params.apiObject][params.action.toString()]['receives'] as LinkedHashMap
				cacheHash = createCacheHash(params, receives)

				//boolean requestKeysMatch = checkURIDefinitions(params, receives)

				if (!checkURIDefinitions(params, receives)) {
					//statsService.setStatsCache(getUserId(), HttpStatus.BAD_REQUEST.value())
					render(status: HttpStatus.BAD_REQUEST.value(), text: 'Expected request variables for endpoint do not match sent variables')
					response.flushBuffer()
					return false
				}

				// RETRIEVE CACHED RESULT (only if using get method); DON'T CACHE LISTS

				if (cache[params.apiObject][params.action.toString()]['cachedResult'] && request.method.toUpperCase()=='GET' ) {
					if(cache[params.apiObject][params.action.toString()]['cachedResult'][cacheHash]){
						println("187 :" + cache[params.apiObject][params.action.toString()]['cachedResult'])
						String authority = getUserRole() as String
						String domain = ((String) params.controller).capitalize()

						JSONObject json = (JSONObject) cache[params.apiObject][params.action.toString()]['cachedResult'][cacheHash][authority][format]
						if (!json || json == null) {
							return false
						} else {
							Set keys = json.keySet()
							def temp = keys.iterator().next()

							def first = json.get(temp)

							// is a List of objects
							if (first instanceof JSONObject && first.size() > 0 && !first.isEmpty()) {

								JSONObject jsonObj = ((JSONObject) json.get('0'))
								Integer version = jsonObj.get('version') as Integer

								if (isCachedResult((Integer) version, domain)) {
									LinkedHashMap result = cache[params.apiObject][params.action.toString()]['cachedResult'][cacheHash][authority][format] as LinkedHashMap
									String content = new groovy.json.JsonBuilder(result).toString()
									byte[] contentLength = content.getBytes("ISO-8859-1")
									if (apiThrottle) {
										if (checkLimit(contentLength.length)) {
											//statsService.setStatsCache(getUserId(), response.status)
											render(text: result as JSON, contentType: request.getContentType())
											return false
										} else {
											//statsService.setStatsCache(getUserId(), 400)
											render(status: 400, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
											response.flushBuffer()
											return false
										}
									} else {
										//statsService.setStatsCache(getUserId(), response.status)
										render(text: result as JSON, contentType: request.getContentType())
										return false
									}
								}
							} else {
								if (json.version != null) {
									if (isCachedResult((Integer) json.get('version'), domain)) {
										LinkedHashMap result = cache[params.apiObject][params.action.toString()]['cachedResult'][cacheHash][authority][format] as LinkedHashMap
										String content = new groovy.json.JsonBuilder(result).toString()
										byte[] contentLength = content.getBytes("ISO-8859-1")
										if (apiThrottle) {
											if (checkLimit(contentLength.length)) {
												//statsService.setStatsCache(getUserId(), response.status)
												render(text: result as JSON, contentType: request.getContentType())
												return false
											} else {
												//statsService.setStatsCache(getUserId(), 400)
												render(status: 400, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
												response.flushBuffer()
												return false
											}
										} else {
											//statsService.setStatsCache(getUserId(), response.status)
											render(text: result as JSON, contentType: request.getContentType())
											return false
										}
									}
								}
							}
						}
					}
				} else {
					if (params.action == null || !params.action) {
						String methodAction = mthd.toString()
						if (!cache[(String) params.apiObject][methodAction]) {
							params.action = cache[(String) params.apiObject]['defaultAction']
						} else {
							params.action = mthd.toString()

							// FORWARD FOR REST DEFAULTS WITH NO ACTION
							String[] tempUri = request.getRequestURI().split("/")
							if (tempUri[2].contains('dispatch') && "${params.controller}.dispatch" == tempUri[2] && !cache[params.apiObject]['domainPackage']) {
								forward(controller: params.controller, action: params.action)
								return false
							}
						}
					}

					// SET PARAMS AND TEST ENDPOINT ACCESS (PER APIOBJECT)
					ApiDescriptor cachedEndpoint = cache[(String) params.apiObject][(String) params.action] as ApiDescriptor
					boolean result = handleApiRequest(cachedEndpoint['deprecated'] as List, (cachedEndpoint['method'])?.toString(), mthd, response, params)
					return result
				}
			}

			// no cache found

			return false

		}catch(Exception e){
			throw new Exception("[ApiToolkitFilters :: preHandler] : Exception - full stack trace follows:", e)
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
			List unsafeMethods = ['PUT', 'POST', 'DELETE']
			def vals = model.values()

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

				ApiDescriptor cachedEndpoint
				if(cache) {
					cachedEndpoint = cache[params.apiObject][(String) params.action] as ApiDescriptor
				}

				// TEST FOR NESTED MAP; WE DON'T CACHE NESTED MAPS
				//boolean isNested = false
				if (newModel != [:]) {

					String content = handleApiResponse(cachedEndpoint['returns'] as LinkedHashMap, cachedEndpoint['roles'] as List, mthd, format, response, newModel, params)

					byte[] contentLength = content.getBytes("ISO-8859-1")
					if (content) {

						// STORE CACHED RESULT
						String format = request.format.toUpperCase()
						String authority = getUserRole() as String

						if(request.method.toUpperCase()=='GET') {
							if (params.controller == 'apidoc') {
								apiCacheService.setApiCachedResult(cacheHash,(String) params.controller, (String) params.apiObject, (String) params.action, 'permitAll', format, content)
							} else {
								apiCacheService.setApiCachedResult(cacheHash,(String) params.controller, (String) params.apiObject, (String) params.action, authority, format, content)
							}
						}

						if (apiThrottle) {
							if (checkLimit(contentLength.length)) {
								//statsService.setStatsCache(getUserId(), response.status)
								render(text: content, contentType: request.getContentType())
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
							render(text: content, contentType: request.getContentType())
							if(cache[params.apiObject]["${params.action}"]['hookRoles']) {
								List hookRoles = cache[params.apiObject]["${params.action}"]['hookRoles'] as List
								String service = "${params.controller}/${params.action}"
								hookService.postData(service, content, hookRoles, this.mthdKey)
							}
						}
					}
				} else {
					//statsService.setStatsCache(getUserId(), response.status)
					String content = parseResponseMethod(mthd, format, params, newModel)
					render(text: content, contentType: request.getContentType())
					if(cache[params.apiObject]["${params.action}"]['hookRoles']) {
						List hookRoles = cache[params.apiObject]["${params.action}"]['hookRoles'] as List
						String service = "${params.controller}/${params.action}"
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
