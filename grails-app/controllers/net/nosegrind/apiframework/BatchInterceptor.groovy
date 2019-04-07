package net.nosegrind.apiframework

import net.nosegrind.apiframework.HookService
import org.grails.web.json.JSONObject

import javax.annotation.Resource
import grails.core.GrailsApplication
import grails.plugin.springsecurity.SpringSecurityService
import grails.util.Metadata
import groovy.json.JsonSlurper
import net.nosegrind.apiframework.RequestMethod

import org.grails.web.util.WebUtils
import grails.converters.JSON
import grails.converters.XML
import grails.util.Holders
import javax.servlet.http.HttpServletResponse
import groovy.transform.CompileStatic
import org.springframework.http.HttpStatus
import groovy.json.JsonSlurper
import javax.servlet.http.HttpSession


/**
 *
 * HandlerInterceptor for Batch API Calls. Parses XML/JSON, handles authentication, rate limiting, caching, and statistics reporting
 * @author Owen Rubel
 *
 * @see ApiCommLayer
 * @see BatchInterceptor
 * @see ChainInterceptor
 *
 */
@CompileStatic
class BatchInterceptor extends ApiCommLayer{

	int order = HIGHEST_PRECEDENCE + 998

	@Resource
	GrailsApplication grailsApplication
	ApiCacheService apiCacheService = new ApiCacheService()
	SpringSecurityService springSecurityService
	HookService hookService
	boolean apiThrottle
	String cacheHash

	// TODO: detect and assign apiObjectVersion from uri
	String entryPoint = "b${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
	String format
	List formats = ['XML', 'JSON']
	String mthdKey
	RequestMethod mthd
	LinkedHashMap cache = [:]
	grails.config.Config conf = Holders.grailsApplication.config
	String contentType
	String apiObject
	String controller
	String action
	ApiDescriptor cachedEndpoint

	BatchInterceptor(){
		match(uri:"/${entryPoint}/**")
	}

	boolean before(){
		//println("##### BATCHINTERCEPTOR (BEFORE): ${params.action}")

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
			if(controller=='apidoc') {
				render(status: 400, text: "API Docs cannot be Batched. Pleased called via the normal method (ie v0.1)")
				return false
			}

			params.max = (params.max==null)?0:params.max
			params.offset = (params.offset==null)?0:params.offset

			if(cache) {
				boolean restAlt = RequestMethod.isRestAlt(mthd.getKey())

				//CHECK REQUEST METHOD FOR ENDPOINT
				// NOTE: expectedMethod must be capitolized in IO State file

				String expectedMethod = cachedEndpoint['method'] as String
				if (!checkRequestMethod(mthd,expectedMethod, restAlt)) {
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
								render(text: result, contentType: request.getContentType())
							} else {
								render(status: 400, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
							}
						}else{
							render(text: result, contentType: request.getContentType())
						}
						return false
					}
				}

				// START HANDLE BATCH PARAMS
				if (request?.getAttribute('batchInc')==null) {
					request.setAttribute('batchInc',0)
				}else{
					Integer newBI = (Integer) request?.getAttribute('batchInc')
					request.setAttribute('batchInc',newBI+1)
				}
				Integer batchInc = (Integer) request.getAttribute('batchInc')


				setBatchParams(params)
				// END HANDLE BATCH PARAMS

				// CHECK REQUEST VARIABLES MATCH ENDPOINTS EXPECTED VARIABLES
				LinkedHashMap receives = cachedEndpoint['receives'] as LinkedHashMap
				cacheHash = createCacheHash(params, receives)

				//boolean requestKeysMatch = checkURIDefinitions(params, receives)
				if (!checkURIDefinitions(params, receives)) {
					render(status: HttpStatus.BAD_REQUEST.value(), text: 'Expected request variables for endpoint do not match sent variables')
					response.flushBuffer()
					return false
				}

				// RETRIEVE CACHED RESULT
				if (cachedEndpoint['cachedResult']) {
					if(cachedEndpoint['cachedResult'][cacheHash]){
						String authority = getUserRole() as String
						String domain = (controller).capitalize()
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
							if (tempUri[2].contains('dispatch') && "${params.controller}.dispatch" == tempUri[2] && !cache[params.apiObject]['domainPackage']) {
								forward(controller: params.controller, action: params.action)
								return false
							}
						}
						action = params.action.toString()
					}

					// SET PARAMS AND TEST ENDPOINT ACCESS (PER APIOBJECT)
					//ApiDescriptor cachedEndpoint = cache[apiObject][action] as ApiDescriptor
					boolean result = handleRequest(cachedEndpoint['deprecated'] as List, response)

					return result
				}
			}

			return false

		}catch(Exception e) {
			throw new Exception('[BatchInterceptor :: before] : Exception - full stack trace follows:', e)
			return false
		}
	}

	boolean after(){
		//println("##### BATCHINTERCEPTOR (AFTER): ${params.action}")

		try{
			String format = request.format.toUpperCase()
			LinkedHashMap newModel = [:]

			if (!model) {
				render(status:HttpServletResponse.SC_NOT_FOUND , text: 'No resource returned')
				return false
			} else {
				newModel = convertModel(model)
			}

			//ApiDescriptor cachedEndpoint
			if(cache) {
				cachedEndpoint = cache[apiObject][action] as ApiDescriptor
			}

			LinkedHashMap content = handleBatchResponse(cachedEndpoint['returns'] as LinkedHashMap,cachedEndpoint['roles'] as List,mthd,format,response,newModel) as LinkedHashMap

			int batchLength = (int) request.getAttribute('batchLength')
			int batchInc = (int) request.getAttribute('batchInc')
			String output
			if(batchEnabled && (batchLength > batchInc+1)){
				WebUtils.exposeRequestAttributes(request, params);
				// this will work fine when we upgrade to newer version that has fix in it

				List temp = []
				if(!session['apiResult']){
					session['apiResult'] = ''
				}else {
					temp = session['apiResult'] as List
				}

				String data = ((format=='XML')? (content as XML) as String:(content as JSON) as String)
				temp.add(data)
				session['apiResult'] = temp
				forward('uri':request.forwardURI.toString(),'params':params)
				return false
			}else{
				List temp = session['apiResult'] as List
				String data = ((format=='XML')? (content as XML) as String:(content as JSON) as String)
				temp.add(data)
				session['apiResult'] = temp

				output = (params?.combine==true)?((format=='XML')?(session['apiResult'] as XML) as String:(session['apiResult'] as JSON) as String):data as String
			}


			byte[] contentLength = output.getBytes( 'ISO-8859-1' )
			if(output){
				if(apiThrottle) {
					if (checkLimit(contentLength.length)) {
						render(text: output, contentType: request.getContentType())
						if(cachedEndpoint['hookRoles']) {
							List hookRoles = cachedEndpoint['hookRoles'] as List
							String service = "${controller}/${action}"
							hookService.postData(service, output, hookRoles, this.mthdKey)
						}
					} else {
						render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
					}
				}else{
					render(text: output, contentType: request.getContentType())
					if(cachedEndpoint['hookRoles']) {
						List hookRoles = cachedEndpoint['hookRoles'] as List
						String service = "${controller}/${action}"
						hookService.postData(service, output, hookRoles, this.mthdKey)
					}
				}
			}

			return false
		}catch(Exception e){
			throw new Exception('[BatchInterceptor :: after] : Exception - full stack trace follows:', e)
			return false
		}

	}

}
