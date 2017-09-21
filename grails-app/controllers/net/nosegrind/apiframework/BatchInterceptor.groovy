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


@CompileStatic
class BatchInterceptor extends ApiCommLayer{

	int order = HIGHEST_PRECEDENCE + 998

	@Resource
	GrailsApplication grailsApplication

	ApiCacheService apiCacheService
	SpringSecurityService springSecurityService

	// TODO: detect and assign apiObjectVersion from uri
	String entryPoint = "b${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
	String format
	List formats = ['XML', 'JSON']
	String mthdKey
	RequestMethod mthd
	LinkedHashMap cache = [:]
	boolean apiThrottle

	BatchInterceptor(){
		match(uri:"/${entryPoint}/**")
	}

	boolean before(){
		//println('##### BATCHINTERCEPTOR (BEFORE)')

		// TESTING: SHOW ALL FILTERS IN CHAIN
		//def filterChain = grailsApplication.mainContext.getBean('springSecurityFilterChain')
		//println(filterChain)

		format = (request?.format)?request.format:'JSON';
		mthdKey = request.method.toUpperCase()
		mthd = (RequestMethod) RequestMethod[mthdKey]

		apiThrottle = Holders.grailsApplication.config.apiThrottle as boolean

		//Map methods = ['GET':'show','PUT':'update','POST':'create','DELETE':'delete']
		boolean restAlt = RequestMethod.isRestAlt(mthd.getKey())

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


		try{
			// INITIALIZE CACHE
			def session = request.getSession()
			cache = session['cache'] as LinkedHashMap


			if(cache) {

				params.apiObject = (params.apiObjectVersion) ? params.apiObjectVersion : cache['currentStable']['value']
				params.action = (params.action == null) ? cache[params.apiObject]['defaultAction'] : params.action

				// CHECK REQUEST METHOD FOR ENDPOINT
				// NOTE: expectedMethod must be capitolized in IO State file
				String expectedMethod = cache[params.apiObject][params.action.toString()]['method'] as String
				if (!checkRequestMethod(mthd,expectedMethod, restAlt)) {
					render(status: HttpServletResponse.SC_BAD_REQUEST, text: "Expected request method '${expectedMethod}' does not match sent method '${mthd.getKey()}'")
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
								render(text: result, contentType: request.getContentType())
								return false
							} else {
								render(status: 400, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
								return false
							}
						}else{
							render(text: result, contentType: request.getContentType())
							return false
						}
					}
				}

				if (request?.getAttribute('batchInc')==null) {
					request.setAttribute('batchInc',0)
				}else{
					Integer newBI = (Integer) request?.getAttribute('batchInc')
					request.setAttribute('batchInc',newBI+1)
				}


				int batchInc = (int) request.getAttribute('batchInc')
				if(params.max!=null) {
					List max = params.max as List
					params.max = max[batchInc]
				}else{
					params.max = 0
				}
				if(params.offset!=null) {
					List offset = params.offset as List
					params.offset = offset[batchInc]
				}else{
					params.offset = 0
				}

				setBatchParams(params)

				// CHECK REQUEST VARIABLES MATCH ENDPOINTS EXPECTED VARIABLES
				LinkedHashMap receives = cache[params.apiObject][params.action.toString()]['receives'] as LinkedHashMap
				//boolean requestKeysMatch = checkURIDefinitions(params, receives)
				if (!checkURIDefinitions(params, receives)) {
					render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Expected request variables for endpoint do not match sent variables')
					return false
				}

				boolean requestKeysMatch = checkURIDefinitions(params,receives)

				if(!requestKeysMatch){
					render(status:HttpServletResponse.SC_BAD_REQUEST, text: 'Expected request variables for endpoint do not match sent variables')
					return false
				}


				// RETRIEVE CACHED RESULT
				if (cache[params.apiObject][params.action.toString()]['cachedResult']) {
					String authority = getUserRole() as String
					String domain = ((String) params.controller).capitalize()

					JSONObject json = (JSONObject) cache[params.apiObject][params.action.toString()]['cachedResult'][authority][request.format.toUpperCase()]
					if(!json){
						return false
					}else{
						if (isCachedResult((Integer) json.get('version'), domain)) {
							String result = cache[params.apiObject][params.action.toString()]['cachedResult'][authority][request.format.toUpperCase()] as String
							byte[] contentLength = result.getBytes( "ISO-8859-1" )
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
					boolean result = handleApiRequest(cachedEndpoint['deprecated'] as List, cachedEndpoint['method']?.toString().trim(), mthd, response, params)

					return result
				}
			}

			return false

		}catch(Exception e) {
			throw new Exception("[BatchInterceptor :: before] : Exception - full stack trace follows:", e)
			return false
		}
	}

	boolean after(){
		//println('##### BATCHFILTER (AFTER)')
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
			int batchLength = (int) request.getAttribute('batchLength')
			int batchInc = (int) request.getAttribute('batchInc')
			if(batchEnabled && (batchLength > batchInc+1)){
				WebUtils.exposeRequestAttributes(request, params);
				// this will work fine when we upgrade to newer version that has fix in it
				params.uri = request.forwardURI.toString()
				forward(params)
				return false
			}

			ApiDescriptor cachedEndpoint = cache[params.apiObject][(String)params.action] as ApiDescriptor
			String content = handleBatchResponse(cachedEndpoint['returns'] as LinkedHashMap,cachedEndpoint['roles'] as List,mthd,format,response,newModel,params) as LinkedHashMap

			//content = handleBatchResponse(cache[params.apiObject][params.action.toString()],request,response,newModel,params) as LinkedHashMap

			byte[] contentLength = content.getBytes( "ISO-8859-1" )
			if(content){
				// STORE CACHED RESULT
				String format = request.format.toUpperCase()
				String authority = getUserRole() as String

				if (!newModel) {
					apiCacheService.setApiCachedResult((String) params.controller, (String) params.apiObject, (String) params.action, authority, format, content)
				}

				if(apiThrottle) {
					if (checkLimit(contentLength.length)) {
						render(text: content, contentType: request.getContentType())
						return false
					} else {
						render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Rate Limit exceeded. Please wait' + getThrottleExpiration() + 'seconds til next request.')
						return false
					}
				}else{
					render(text: content, contentType: request.getContentType())
					return false
				}
			}

			return false
		}catch(Exception e){
			throw new Exception("[BatchInterceptor :: after] : Exception - full stack trace follows:", e)
			return false
		}

	}

}
