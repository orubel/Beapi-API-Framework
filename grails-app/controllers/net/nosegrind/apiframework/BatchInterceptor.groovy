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

	BatchInterceptor(){
		match(uri:"/${entryPoint}/**")
	}

	boolean before(){
		//println("##### BATCHINTERCEPTOR (BEFORE): ${params.action}")

		format = (request?.format)?request.format.toUpperCase():'JSON'
		mthdKey = request.method.toUpperCase()
		mthd = (RequestMethod) RequestMethod[mthdKey]

		apiThrottle = this.conf.apiThrottle as boolean
		boolean restAlt = RequestMethod.isRestAlt(mthd.getKey())
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

		//try{
			//Test For APIDoc
			if(controller=='apidoc') {
				render(status: 400, text: "API Docs cannot be Batched. Pleased called via the normal method (ie v0.1)")
				return false
			}

			params.max = (params.max==null)?0:params.max
			params.offset = (params.offset==null)?0:params.offset

			if(cache) {
				//CHECK REQUEST METHOD FOR ENDPOINT
				// NOTE: expectedMethod must be capitolized in IO State file

				String expectedMethod = cache[apiObject][action]['method'] as String
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
				LinkedHashMap receives = cache[apiObject][action]['receives'] as LinkedHashMap

				//boolean requestKeysMatch = checkURIDefinitions(params, receives)
				if (!checkURIDefinitions(params, receives)) {
					render(status: HttpStatus.BAD_REQUEST.value(), text: 'Expected request variables for endpoint do not match sent variables')
					response.flushBuffer()
					return false
				}


				// RETRIEVE CACHED RESULT
				if (cache[apiObject][action]['cachedResult']) {
					String authority = getUserRole() as String
					String domain = (controller).capitalize()

					JSONObject json = (JSONObject) cache[apiObject][action]['cachedResult'][authority][request.format.toUpperCase()]
					if(!json){
						return false
					}else{
						if (isCachedResult((Integer) json.get('version'), domain)) {
							String result = cache[apiObject][action]['cachedResult'][authority][request.format.toUpperCase()] as String
							byte[] contentLength = result.getBytes( 'ISO-8859-1' )
							if(apiThrottle) {
								if (checkLimit(contentLength.length)) {
									render(text: result, contentType: request.getContentType())
									return false
								} else {
									render(status: 400, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
									response.flushBuffer()
									return false
								}
							}else{
								render(text: result, contentType: request.getContentType())
								return false
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
							if (tempUri[2].contains('dispatch') && "${params.controller}.dispatch" == tempUri[2] && !cache[params.apiObject]['domainPackage']) {
								forward(controller: params.controller, action: params.action)
								return false
							}
						}
						action = params.action.toString()
					}

					// SET PARAMS AND TEST ENDPOINT ACCESS (PER APIOBJECT)
					ApiDescriptor cachedEndpoint = cache[apiObject][action] as ApiDescriptor
					boolean result = handleApiRequest(cachedEndpoint['deprecated'] as List, cachedEndpoint['method']?.toString().trim(), mthd, response, params)

					return result
				}
			}

			return false

		//}catch(Exception e) {
		//	throw new Exception('[BatchInterceptor :: before] : Exception - full stack trace follows:', e)
		//	return false
		//}
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

			ApiDescriptor cachedEndpoint
			if(cache) {
				cachedEndpoint = cache[apiObject][action] as ApiDescriptor
			}

			LinkedHashMap content = handleBatchResponse(cachedEndpoint['returns'] as LinkedHashMap,cachedEndpoint['roles'] as List,mthd,format,response,newModel,params) as LinkedHashMap

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
