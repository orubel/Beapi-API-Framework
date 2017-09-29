package net.nosegrind.apiframework.comm

import grails.converters.JSON
import grails.converters.XML
import grails.util.Holders
import org.grails.validation.routines.UrlValidator
import org.grails.web.util.GrailsApplicationAttributes

import javax.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.springframework.web.context.request.ServletRequestAttributes
import javax.servlet.forward.*
import java.text.SimpleDateFormat
import org.grails.groovy.grails.commons.*
import grails.web.servlet.mvc.GrailsParameterMap

import net.nosegrind.apiframework.*


abstract class ApiLayer{

	static transactional = false

	def springSecurityService
	ApiCacheService apiCacheService

	List optionalParams = ['action','controller','v','apiCombine', 'apiObject']


	private HttpServletRequest getRequest(){
		HttpServletRequest request = ((ServletRequestAttributes) RCH.currentRequestAttributes()).getRequest()
		return request
	}


	// BOTH
	List getApiParams(LinkedHashMap definitions){
		try{
			List apiList = []
			def principal = springSecurityService.principal
			List roleNames = principal.authorities*.authority
			definitions.each() { it2 ->
				if (roleNames.contains(it2.key) || it2.key == 'permitAll') {
					it2.value.each() { it4 ->
						apiList.add(it4.name)
					}
				}
			}
			return apiList
		}catch(Exception e){
			throw new Exception("[ApiLayer :: getApiParams] : Exception - full stack trace follows:",e)
		}
	}


	// BOTH
	LinkedHashMap parseURIDefinitions(LinkedHashMap model,List responseList){
		if(model[0].getClass()=='java.util.LinkedHashMap'){
			println("##### recursive check #####")
			model.each(){ key,val ->
				model[key] = this.parseUriDefinitions(val,responseList)
			}
			return model
		}else {
			try {
				String msg = 'Error. Invalid variables being returned. Please see your administrator'

				List paramsList
				Integer msize = model.size()
				switch (msize) {
					case 0:
						return [:]
						break;
					case 1:
						paramsList = (model.keySet() != ['id']) ? model.entrySet().iterator().next() as List : model.keySet() as List
						break;
					default:
						paramsList = model.keySet() as List
						break;
				}

				paramsList?.removeAll(optionalParams)

				if (!responseList.containsAll(paramsList)) {

					paramsList.removeAll(responseList)
					paramsList.each() { it2 ->
						model.remove("${it2}".toString())
					}

					if (!paramsList) {
						return [:]
					} else {
						return model
					}
				} else {
					return model
				}

			} catch (Exception e) {
				throw new Exception("[ApiLayer :: parseURIDefinitions] : Exception - full stack trace follows:", e)
			}
		}
	}

	LinkedHashMap parseResponseMethod(HttpServletRequest request, GrailsParameterMap params, LinkedHashMap result){
		LinkedHashMap data = [:]
		String defaultEncoding = Holders.grailsApplication.config.apitoolkit.encoding
		String encoding = request.getHeader('accept-encoding')?request.getHeader('accept-encoding'):defaultEncoding
		switch(request.method) {
			case 'PURGE':
				// cleans cache; disabled for now
				break;
			case 'TRACE':
				break;
			case 'HEAD':
				break;
			case 'OPTIONS':
				String doc = getApiDoc(params)
				data = ['content':doc,'contentType':request.getAttribute('contentType'),'encoding':encoding]
				break;
			case 'GET':
			case 'PUT':
			case 'POST':
			case 'DELETE':
					String content
					switch(request.format.toUpperCase()){
						case 'XML':
							content = result as XML
							break
						case 'JSON':
						default:
							content = result as JSON
					data = ['content':content,'contentType':request.getAttribute('contentType'),'encoding':encoding]
				}
				break;
		}

		return ['apiToolkitContent':data.content,'apiToolkitType':request.getAttribute('contentType'),'apiToolkitEncoding':encoding]
	}

	/*
 * TODO: Need to compare multiple authorities
 */
	String getApiDoc(GrailsParameterMap params){
		// check for ['doc'][role] in cache
		// if none, continue

		LinkedHashMap newDoc = [:]
		List paramDescProps = ['paramType','idReferences','name','description']
		try{
			def controller = grailsApplication.getArtefactByLogicalPropertyName('Controller', params.controller)
			if(controller){
				def cache = (params.controller)?apiCacheService.getApiCache(params.controller):null
				if(cache){
					if(cache[params.apiObject][params.action]){

						def doc = cache[params.apiObject][params.action].doc
						def path = doc?.path
						def method = doc?.method
						def description = doc?.description


						//def authority = springSecurityService.principal.authorities*.authority[0]
						newDoc[params.action] = ['path':path,'method':method,'description':description]
						if(doc.receives){
							newDoc[params.action].receives = []

							doc.receives.each{ it ->
								if(apiRoles([it.key]) || it.key=='permitAll'){
									it.value.each(){ it2 ->
										LinkedHashMap values = [:]
										it2.each(){ it3 ->
											if(paramDescProps.contains(it3.key)){
												values[it3.key] = it3.value
											}
										}
										if(values) {
											newDoc[params.action].receives.add(values)
										}
									}

								}
							}
						}

						if(doc.returns){
							newDoc[params.action].returns = []
							List jsonReturns = []
							doc.returns.each(){ v ->
								if(apiRoles([v.key]) || v.key=='permitAll'){
									jsonReturns.add(["${v.key}":v.value])
									v.value.each(){ v2 ->
										LinkedHashMap values3 = [:]
										v2.each(){ v3 ->
											if(paramDescProps.contains(v3.key)){
												values3[v3.key] = v3.value
											}
										}
										if(values3) {
											newDoc[params.action].returns.add(values3)
										}
									}
									//newDoc[params.action].returns[v.key] = v.value
								}
							}

							//newDoc[params.action].json = processJson(newDoc[params.action].returns)

							newDoc[params.action].json = processJson(jsonReturns[0] as LinkedHashMap)
						}

						if(doc.errorcodes){
							doc.errorcodes.each{ it ->
								newDoc[params.action].errorcodes.add(it)
							}
						}

						// store ['doc'][role] in cache

						return newDoc as JSON
					}
				}
			}
			return [:]
		}catch(Exception e){
			throw new Exception("[ApiLayer :: getApiDoc] : Exception - full stack trace follows:",e)
		}
	}



	// set version,controller,action / controller,action
	List parseUri(String uri, String entrypoint){
		if(uri[0]=='/'){ uri=uri[1..-1] }
		List uriVars = uri.split('/')
		if(uriVars.size()==3){
			List temp2 = entrypoint.split('-')
			if(temp2.size()>1){
				// version
				uriVars[0] = temp2[1]
				return uriVars
			}else{
				uriVars.drop(1)
				return uriVars
			}
		}else{
			return uriVars
		}
	}




	// api call now needs to detect request method and see if it matches anno request method
	boolean isApiCall(){
		try{
			HttpServletRequest request = getRequest()
			GrailsParameterMap params = RCH.currentRequestAttributes().params
			String uri = request.forwardURI.split('/')[1]
			String api
			if(params.apiObject){
				api = (apiName)?"v${params.apiVersion}-${params.apiObject}" as String:"v${params.apiVersion}-${params.apiObject}" as String
			}else{
				api = (apiName)?"v${params.apiVersion}" as String:"v${params.apiVersion}" as String
			}
			return uri==api
		}catch(Exception e){
			throw new Exception("[ApiLayerService :: isApiCall] : Exception - full stack trace follows:",e)
		}
	}

	protected void setParams(HttpServletRequest request,GrailsParameterMap params){
		try{
			String format = request.format.toUpperCase()
			request."$format"?.each() { key,value ->
				params.put(key,value)
			}
		}catch(Exception e){
			throw new Exception("[ApiLayerService :: setParams] : Exception - full stack trace follows:",e)
		}
	}

	/*
	 * Returns chainType
	 * 0 = blankchain
	 * 1 = prechain
	 * 2 = postchain
	 * 3 = illegal combination
	 */
	protected int checkChainedMethodPosition(LinkedHashMap cache,HttpServletRequest request, GrailsParameterMap params, List uri, Map path){
		try{
			boolean preMatch = false
			boolean postMatch = false
			boolean pathMatch = false

			List keys = path.keySet() as List
			int pathSize = keys.size()

			String controller = uri[0]
			String action = uri[1]
			long id = uri[2].toLong()

			// prematch check
			String currentMethod = Method["${request.method.toString()}"].toString()
			//println("currentMethod:"+currentMethod)
			String methods = cache[params.apiObject][action]['method'].trim()
			//println("methods:"+methods)

			if(currentMethod!=methods && methods=='GET'){
				if(['prechain','postchain'].contains(params?.apiChain?.type)){
					preMatch = true
				}
			}else{
				if(methods == currentMethod && params?.apiChain?.type=='blankchain'){
					preMatch = true
				}
			}


			// postmatch check
			if(pathSize>=1){
				//println("pathSize>=1")
				String last=path[keys[pathSize-1]]
				if(last && (last!='return' || last!='null')){
					List last2 = keys[pathSize-1].split('/')

					//println(last2)
					//println(last2[0])
					cache = apiCacheService.getApiCache(last2[0])
					//println(cache)
					methods = cache[params.apiObject][last2[1]]['method'].trim()
					//println("methods2:"+methods)
					if(methods=='GET'){
						if(methods != currentMethod && params?.apiChain?.type=='postchain'){
							postMatch = true
						}
					}else{
						if(methods == currentMethod){
							postMatch = true
						}
					}
				}else{
					postMatch = true
				}
			}

			// path check
			int start = 1
			int end = pathSize-2
			//println("${start} > ${end}")
			if(start<end){
				//println("${start} > ${end}")
				keys[0..(pathSize-1)].each{ val ->
					if(val){
						//println("val : "+val)
						List temp2 = val.split('/')
						//println(temp2)
						//println(temp2[0])
						cache = apiCacheService.getApiCache(temp2[0])
						methods = cache[params.apiObject][temp2[1]]['method'].trim()

						if(methods=='GET'){
							if(methods == currentMethod && params?.apiChain?.type=='blankchain'){
								pathMatch = true
							}
						}else{
							if(methods == currentMethod){
								pathMatch = true
							}
						}
					}
				}
			}

			//log.info("${pathMatch} / ${preMatch} / ${postMatch}")
			if(pathMatch || (preMatch && postMatch)){
				if(params?.apiChain?.type=='blankchain'){
					return 0
				}else{
					return 3
				}
			}else{
				if(preMatch){
					setParams(request,params)
					return 1
				}else if(postMatch){
					setParams(request,params)
					return 2
				}
			}

			if(params?.apiChain?.type=='blankchain'){
				return 0
			}else{
				return 3
			}

		}catch(Exception e){
			throw new Exception("[ApiLayerService :: checkChainedMethodPosition] : Exception - full stack trace follows:",e)
		}
	}



	boolean validateUrl(String url){
		String[] schemes = ["http","https"]
		UrlValidator urlValidator = new UrlValidator(schemes)
		return urlValidator.isValid(url)
	}

	boolean isRequestRedirected(){
		return (request.getAttribute(GrailsApplicationAttributes.REDIRECT_ISSUED) != null)? true : false
	}

	private ArrayList processDocErrorCodes(HashSet error){
		List errors = error as List
		ArrayList err = []
		errors.each{ v ->
			def code = ['code':v.code,'description':v.description]
			err.add(code)
		}
		return err
	}


	def apiRoles(List list) {
		if(springSecurityService.principal.authorities*.authority.any { list.contains(it) }){
			return true
		}
		return ['validation.customRuntimeMessage', 'ApiCommandObject does not validate. Check that your data validates or that requesting user has access to api method and all fields in api command object.']
	}



	// PostProcessService
	LinkedHashMap formatMap(Map map) {
		LinkedHashMap newMap = [:]

		map.each(){ key, val ->
			if(grailsApplication.isDomainClass(val.getClass())){
				newMap[key]=formatDomainObject(val)
			}else{
				newMap[key] = ((val in java.util.ArrayList || val in java.util.List) || val in java.util.Map)?val:val.toString()
			}
		}

		return newMap
	}

}
