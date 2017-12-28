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
import org.springframework.web.context.request.RequestContextHolder as RCH
import javax.servlet.http.HttpSession

@CompileStatic
class ApiFrameworkInterceptor extends ApiCommLayer{

	int order = HIGHEST_PRECEDENCE + 999

	@Resource
	GrailsApplication grailsApplication
	ApiCacheService apiCacheService
	SpringSecurityService springSecurityService
	HookService hookService
	boolean apiThrottle

	// TODO: detect and assign apiObjectVersion from uri
	String entryPoint = "v${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
	String format
	List formats = ['XML', 'JSON']
	String mthdKey
	RequestMethod mthd
	LinkedHashMap cache = [:]


	ApiFrameworkInterceptor(){
		match(uri:"/${entryPoint}/**")
	}

	boolean before(){
		//println('##### FILTER (BEFORE)')

		// TESTING: SHOW ALL FILTERS IN CHAIN
		//def filterChain = grailsApplication.mainContext.getBean('springSecurityFilterChain')
		//println("FILTERCHAIN : "+filterChain)

		format = (request?.format)?request.format.toUpperCase():'JSON'
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

		// INITIALIZE CACHE
		def session = request.getSession()
		cache = session['cache'] as LinkedHashMap

		if(cache) {
			params.apiObject = (params.apiObjectVersion) ? params.apiObjectVersion : cache['currentStable']['value']
			params.action = (params.action == null) ? cache[params.apiObject]['defaultAction'] : params.action
		}

		// CHECK REQUEST VARIABLES MATCH ENDPOINTS EXPECTED VARIABLES
		//String path = "${params.controller}/${params.action}".toString()
		//println(path)


		try{
			// IS APIDOC??
			if(params.controller=='apidoc'){
				if(cache){
					return true
				}
				return false
			}else{
				if(cache) {
					// CHECK REQUEST METHOD FOR ENDPOINT
					// NOTE: expectedMethod must be capitolized in IO State file
					String expectedMethod = cache[params.apiObject][params.action.toString()]['method'] as String
					if (!checkRequestMethod(mthd,expectedMethod, restAlt)) {
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

					LinkedHashMap receives = cache[params.apiObject][params.action.toString()]['receives'] as LinkedHashMap
					//boolean requestKeysMatch = checkURIDefinitions(params, receives)

					if (!checkURIDefinitions(params, receives)) {
						render(status: HttpStatus.BAD_REQUEST.value(), text: 'Expected request variables for endpoint do not match sent variables')
						response.flushBuffer()
						return false
					}

					// RETRIEVE CACHED RESULT; DON'T CACHE LISTS
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
						boolean result = handleApiRequest(cachedEndpoint['deprecated'] as List, (cachedEndpoint['method'])?.toString(), mthd, response, params)
						return result
					}
				}
			}
			// no cache found

			return false

		}catch(Exception e){
			throw new Exception("[ApiToolkitFilters :: preHandler] : Exception - full stack trace follows:", e)
			return false
		}
	}

	boolean after(){
		//println('##### FILTER (AFTER)')

		List unsafeMethods = ['PUT','POST','DELETE']
		def vals = model.values()

		//try {
			LinkedHashMap newModel = [:]
			if (params.controller != 'apidoc') {
				if (!model || vals[0]==null) {
					render(status: HttpServletResponse.SC_NOT_FOUND, text: 'No resource returned / domain is empty')
					response.flushBuffer()
					return false
				} else {
					newModel = convertModel(model)
				}
			} else {
				newModel = model as LinkedHashMap
			}

			// store webhook
			if(unsafeMethods.contains(request.method.toUpperCase())) {
				// if controller/action HOOK has roles, is HOOKABLE
				//LinkedHashMap cache = apiCacheService.getApiCache(params.controller.toString())
				if (cache) {
					List hookRoles = cache[params.apiObject]["${params.action}"]['hookRoles'] as List
					if(hookRoles.size()>0) {
						hookService.postData(params.controller.toString(), newModel, params.action.toString())
					}
				}
			}


			ApiDescriptor cachedEndpoint = cache[params.apiObject][(String) params.action] as ApiDescriptor

			// TEST FOR NESTED MAP; WE DON'T CACHE NESTED MAPS
			//boolean isNested = false
			if (newModel != [:]) {

				//Object key = newModel?.keySet()?.iterator()?.next()
				//if (newModel[key].getClass().getName() == 'java.util.LinkedHashMap') {
				//	isNested = true
				//}

				String content = handleApiResponse(cachedEndpoint['returns'] as LinkedHashMap, cachedEndpoint['roles'] as List, mthd, format, response, newModel, params)

				byte[] contentLength = content.getBytes( "ISO-8859-1" )
				if (content) {
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
			}else{
				String content = parseResponseMethod(mthd, format, params, newModel)
				render(text: content, contentType: request.getContentType())
			}

			return false
		//}catch(Exception e){
		//	throw new Exception("[ApiToolkitFilters :: apitoolkit.after] : Exception - full stack trace follows:", e)
		//	return false
		//}
	}

}
