package net.nosegrind.apiframework

import grails.converters.JSON
import javax.annotation.Resource
import grails.core.GrailsApplication
//import net.nosegrind.apiframework.ApiDescriptor
import grails.plugin.springsecurity.SpringSecurityService
import groovy.json.JsonSlurper
import groovy.util.XmlSlurper;
import grails.util.Metadata
import grails.util.Holders

//import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import groovy.transform.CompileStatic

@CompileStatic
class ProfilerInterceptor extends ProfilerCommLayer{

	int order = HIGHEST_PRECEDENCE + 997

	@Resource
	GrailsApplication grailsApplication

	//ApiRequestService apiRequestService
	//ApiResponseService apiResponseService
	ApiCacheService apiCacheService
	TraceService traceService
	SpringSecurityService springSecurityService
	LinkedHashMap cache = [:]

	// TODO: detect and assign apiObjectVersion from uri
	String entryPoint = "p${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"

	ProfilerInterceptor(){
		// TODO: detect and assign apiObjectVersion from uri
		match(uri:"/${entryPoint}/**")
	}

	boolean before(){
		//println('##### PROFILE FILTER (BEFORE)')

		// Check Profiler Roles
		String authority = getUserRole() as String
		LinkedHashMap apitoolkit = Holders.grailsApplication.config.apitoolkit as LinkedHashMap
		List profilerRoles = apitoolkit['profilerRoles'] as List
		if(!profilerRoles.contains(authority)){
			render(status: HttpServletResponse.SC_UNAUTHORIZED , text: "Unauthorized Request. User does not have privileges for Profiling Services.'")
			return false
		}

		traceService.startTrace('ProfilerInterceptor','before')

		String format = (request?.format)?request.format:'JSON';
		Map methods = ['GET':'show','PUT':'update','POST':'create','DELETE':'delete']
		boolean restAlt = (['OPTIONS','TRACE','HEAD'].contains(request.method))?true:false

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


			if(params.controller=='apidoc'){
				if(cache){
					params.apiObject = (params.apiObjectVersion) ? params.apiObjectVersion : cache['currentStable']['value']
					params.action = (params.action == null) ? cache[params.apiObject]['defaultAction'] : params.action
					traceService.endTrace('ProfilerInterceptor','before')
					return true
				}
				// TODO : return false and render message/error code ???
			}else{
				if(cache) {
					params.apiObject = (params.apiObjectVersion) ? params.apiObjectVersion : cache['currentStable']['value']
					params.action = (params.action == null) ? cache[params.apiObject]['defaultAction'] : params.action

					String expectedMethod = cache[params.apiObject][params.action.toString()]['method'] as String
					if(!checkRequestMethod(expectedMethod,restAlt)) {
						render(status: HttpServletResponse.SC_BAD_REQUEST, text: "Expected request method '${expectedMethod}' does not match sent method '${request.method}'")
						traceService.endTrace('ProfilerInterceptor','before')
						return false
					}

					params.max = (params.max)?params.max:0
					params.offset = (params.offset)?params.offset:0

					// Check for REST alternatives
					if (!restAlt) {

						// Check that sent request params match expected endpoint params for principal ROLE
						LinkedHashMap receives = cache[params.apiObject][params.action.toString()]['receives'] as LinkedHashMap
						boolean requestKeysMatch = checkURIDefinitions(params, receives)

						if (!requestKeysMatch) {
							render(status: HttpServletResponse.SC_BAD_REQUEST, text: 'Expected request variables do not match sent variables')
							traceService.endTrace('ProfilerInterceptor','before')
							return false
						}
					}else{
						LinkedHashMap result = parseRequestMethod(request, params)
						if(result){
							render(text:result.apiToolkitContent, contentType:request.getContentType(), encoding:result.apiToolkitEncoding)
						}
						traceService.endTrace('ProfilerInterceptor','before')
						return false
					}

					if(params.action==null || !params.action){
						String methodAction = methods[request.method]
						if(!cache[(String)params.apiObject][methodAction]){
							params.action = cache[(String)params.apiObject]['defaultAction']
						}else{
							params.action = methods[request.method]

							// FORWARD FOR REST DEFAULTS WITH NO ACTION
							String[] tempUri = request.getRequestURI().split("/")
							if(tempUri[2].contains('dispatch') && "${params.controller}.dispatch" == tempUri[2] && !cache[params.apiObject]['domainPackage']){
								forward(controller:params.controller,action:params.action)
								traceService.endTrace('ProfilerInterceptor','before')
								return false
							}
						}
					}

					// SET PARAMS AND TEST ENDPOINT ACCESS (PER APIOBJECT)
					ApiDescriptor cachedEndpoint = cache[(String)params.apiObject][(String)params.action] as ApiDescriptor
					boolean result = handleApiRequest(cachedEndpoint, request, response, params)
					traceService.endTrace('ProfilerInterceptor','before')
					return result
				}
			}
			// no cache found
			traceService.endTrace('ProfilerInterceptor','before')
			return false

		}catch(Exception e){
			log.error("[ProfilerInterceptor :: preHandler] : Exception - full stack trace follows:", e)
			traceService.endTrace('ProfilerInterceptor','before')
			return false
		}
	}

	boolean after(){
		//println('##### PROFILE FILTER (AFTER)')
		traceService.startTrace('ProfilerInterceptor','after')
		try{
			LinkedHashMap newModel = [:]

			if(params.controller!='apidoc') {
				if (!model) {
					render(status: HttpServletResponse.SC_NOT_FOUND, text: 'No resource returned / domain is empty')
					traceService.endTrace('ProfilerInterceptor','after')
					return false
				} else {
					newModel = convertModel(model)
				}
			}else{
				newModel = model as LinkedHashMap
			}

			LinkedHashMap cache = session['cache'] as LinkedHashMap
			//LinkedHashMap cache = apiCacheService.getApiCache(params.controller.toString())
			ApiDescriptor cachedEndpoint = cache[params.apiObject][(String)params.action] as ApiDescriptor
			String content = handleApiResponse(cachedEndpoint['returns'] as LinkedHashMap,cachedEndpoint['roles'] as List,request,response,newModel,params) as LinkedHashMap

			if(content){
				traceService.endTrace('ProfilerInterceptor','after')
				LinkedHashMap traceContent = traceService.endAndReturnTrace('ProfilerInterceptor','after')

				String tcontent = traceContent as JSON
				render(text:tcontent, contentType:request.getContentType())
			}

			return false
		}catch(Exception e){
			log.error("[ProfilerInterceptor :: apitoolkit.after] : Exception - full stack trace follows:", e);
			traceService.endTrace('ProfilerInterceptor','after')
			return false
		}
	}

}
