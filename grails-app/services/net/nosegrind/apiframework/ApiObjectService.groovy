package net.nosegrind.apiframework

import net.nosegrind.apiframework.ApiDescriptor
import net.nosegrind.apiframework.ApiParams
import net.nosegrind.apiframework.ParamsDescriptor
import org.grails.web.json.JSONObject
import grails.core.GrailsApplication

import grails.converters.JSON
import grails.util.Environment
//import net.nosegrind.apiframework.ApiDescriptor
import org.grails.groovy.grails.commons.*

// TODO: rename to IOSTATESERVICE
class ApiObjectService{

	GrailsApplication grailsApplication
	ApiCacheService apiCacheService = new ApiCacheService()

	static transactional = false
	
/*
	public initialize(){
		try {
			if(grailsApplication.config.apitoolkit.serverType=='master'){
				String ioPath
				if(grailsApplication.isWarDeployed()){
					ioPath = grailsApplication.mainContext.servletContext.getRealPath('/')
					if(Environment.current == Environment.DEVELOPMENT || Environment.current == Environment.TEST) {
						ioPath += 'WEB-INF/classes/iostate'
					}
				}else{
					ioPath = 'src/iostate'
				}
				parseFiles(ioPath)
			}

			String apiObjectSrc = grailsApplication.config.iostate.preloadDir
			parseFiles(apiObjectSrc.toString())

		} catch (Exception e) {
			throw new Exception("[ApiObjectService :: initialize] : Exception - full stack trace follows:",e)
		}
	}
	*/

	private Boolean parseFile(JSONObject json){
		String apiObjectName = json.NAME.toString()
		return parseJson(json.NAME.toString(),json)
	}
	
	private parseFiles(String path){
		new File(path).eachFile() { file ->
			def tmp = file.name.toString().split('\\.')

			if(tmp[1]=='json'){
				try{
					JSONObject json = JSON.parse(file.text)
					parseJson(json.NAME.toString(),json)
					//def cache = apiCacheService.getApiCache(apiName)
				}catch(Exception e){
					throw new Exception("[ApiObjectService :: initialize] : Unacceptable file '${file.name}' - full stack trace follows:",e)
				}
			}
		}
	}
	
	String getKeyType(String reference, String type){
		String keyType = (reference.toLowerCase()=='self')?((type.toLowerCase()=='long')?'PKEY':'INDEX'):((type.toLowerCase()=='long')?'FKEY':'INDEX')
		return keyType
	}

	private LinkedHashMap getIOSet(JSONObject io,LinkedHashMap apiObject,List valueKeys,String apiName){
		LinkedHashMap<String,ParamsDescriptor> ioSet = [:]

		io.each{ k, v ->
			// init
			if(!ioSet[k]){ ioSet[k] = [] }

			def roleVars=v.toList()
			roleVars.each{ val ->
				if(v.contains(val)){
					if(!ioSet[k].contains(apiObject[val])) {
						if (apiObject[val]){
							ioSet[k].add(apiObject[val])
						}else {
							throw new Exception("VALUE '"+val+"' is not a valid key for IO State [${apiName}]. Please check that this 'VALUE' exists")
						}
					}
				}
			}
		}

		def permitAll = ioSet['permitAll']
		ioSet.each(){ key, val ->
			if(key!='permitAll'){
				permitAll.each(){ it ->
					if(!ioSet[key].contains("-${it}")) {
						ioSet[key].add(it)
					}
				}
			}
		}

		//List ioKeys = []
		ioSet.each(){ k, v ->
			List ioKeys = v.collect(){ it -> it.name }
			if (!ioKeys.minus(valueKeys).isEmpty()) {
				throw new Exception("[Runtime :: getIOSet] : VALUES for IO State [" + apiName + "] do not match REQUEST/RESPONSE values for endpoints")
			}
		}

		return ioSet
	}

	public LinkedHashMap convertApiDescriptor(ApiDescriptor desc){
		LinkedHashMap newDesc = [
			'empty':false,
			'method':desc.method,
			'fkeys':desc.fkeys,
			'description':desc.description,
			'roles':desc.roles,
			'batchRoles':desc.batchRoles,
			'receives':desc.receives,
			'returns':desc.returns
		]
		return newDesc
	}

	private ApiDescriptor createApiDescriptor(String networkGrp, String apiname,String apiMethod, String apiDescription, List apiRoles, Set batchRoles, Set hookRoles, String uri, JSONObject values, JSONObject json){
		LinkedHashMap<String,ParamsDescriptor> apiObject = [:]
		ApiParams param = new ApiParams()

		Set fkeys = []
		Set pkeys= []
		List keys = []
		try {
			values.each { k, v ->
				keys.add(k)
				v.reference = (v.reference) ? v.reference : 'self'
				param.setParam(v.type, k)

				String hasKey = (v?.key) ? v.key : null

				if (hasKey != null) {
					param.setKey(hasKey)

					String hasReference = (v?.reference) ? v.reference : 'self'
					param.setReference(hasReference)

					if (['FOREIGN', 'INDEX', 'PRIMARY'].contains(v.key?.toUpperCase())) {
						switch (v.key) {
							case 'INDEX':
								if (v.reference != 'self') {
									LinkedHashMap fkey = ["${k}": "${v.reference}"]
									fkeys.add(fkey)
								}
								break;
							case 'FOREIGN':
								LinkedHashMap fkey = ["${k}": "${v.reference}"]
								fkeys.add(fkey)
								break;
							case 'PRIMARY':
								pkeys.add(k)
								break;
						}
					}
				}

				String hasDescription = (v?.description) ? v.description : ''
				param.setDescription(hasDescription)

				if (v.mockData!=null) {
					if(v.mockData.isEmpty()){
						param.setMockData('')
					}else {
						param.setMockData(v.mockData.toString())
					}
				} else {
					throw new Exception("[Runtime :: createApiDescriptor] : MockData Required for type '" + k + "' in IO State[" + apiname + "]")
				}

				// collect api vars into list to use in apiDescriptor

				apiObject[param.param.name] = param.toObject()
			}
		}catch(Exception e){
			throw new Exception("[Runtime :: createApiDescriptor] : Badly Formatted IO State :",e)
		}

		LinkedHashMap receives = getIOSet(json.URI[uri]?.REQUEST,apiObject,keys,apiname)
		LinkedHashMap returns = getIOSet(json.URI[uri]?.RESPONSE,apiObject,keys,apiname)

		ApiDescriptor service = new ApiDescriptor(
				'empty':false,
				'method':"$apiMethod",
				'networkGrp': "$networkGrp",
				'pkey':pkeys,
				'fkeys':fkeys,
				'description':"$apiDescription",
				'roles':apiRoles,
				'batchRoles':[],
				'hookRoles':[],
				'doc':[:],
				'receives':receives,
				'returns':returns,
				'cachedResult': [:]
		)

		// override networkRoles with 'DEFAULT' in IO State



		batchRoles.each{
			if(!apiRoles.contains(it)){
				throw new Exception("[Runtime :: createApiDescriptor] : BatchRoles in IO State[" + apiname + "] do not match default/networkRoles")
			}
		}
		service['batchRoles'] = batchRoles

		hookRoles.each{
			if(!apiRoles.contains(it)){
				throw new Exception("[Runtime :: createApiDescriptor] : HookRoles in IO State[" + apiname + "] do not match default/networkRoles")
			}
		}
		service['hookRoles'] = hookRoles

		return service
	}

	LinkedHashMap parseJson(String apiName,JSONObject json){
		//apiCacheService.flushAllApiCache()

		LinkedHashMap methods = [:]

		String networkGrp = json.NETWORKGRP
		String testUser = json.TESTUSER
		json.VERSION.each() { vers ->
			//def versKey = vers.key
			String defaultAction = (vers.value['DEFAULTACTION'])?vers.value.DEFAULTACTION:'index'

			//Set testOrder = (vers.value['TESTORDER'])?vers.value.TESTORDER:[]



			Set deprecated = (vers.value.DEPRECATED)?vers.value.DEPRECATED:[]
			String domainPackage = (vers.value.DOMAINPACKAGE!=null || vers.value.DOMAINPACKAGE?.size()>0)?vers.value.DOMAINPACKAGE:null

			String actionname
			vers.value.URI.each() { it ->

				//def cache = apiCacheService.getApiCache(apiName.toString())
				//def cache = (temp?.get(apiName))?temp?.get(apiName):[:]

				methods['cacheversion'] = 1

				JSONObject apiVersion = json.VERSION[vers.key]

				actionname = it.key

				ApiDescriptor apiDescriptor
				//Map apiParams

				String apiMethod = it.value.METHOD
				String apiDescription = it.value.DESCRIPTION

				List apiRoles = (it.value.ROLES.DEFAULT)?it.value.ROLES.DEFAULT as List:null
				List networkRoles = grails.util.Holders.grailsApplication.config.apitoolkit.networkRoles."${networkGrp}"
				if(apiRoles) {
					if(!(apiRoles-networkRoles.intersect(apiRoles).isEmpty())){
						throw new Exception("[Runtime :: parseJson] : ${it.key}.ROLES.DEFAULT does not match any networkRoles for ${apiName} NETWORKGRP :",e)
					}
				}else{
					apiRoles = networkRoles
				}


				Set batchRoles = it.value.ROLES.BATCH
				Set hookRoles = it.value.ROLES.HOOK

				String uri = it.key
				apiDescriptor = createApiDescriptor(networkGrp, apiName, apiMethod, apiDescription, apiRoles, batchRoles, hookRoles, uri, json.get('VALUES'), apiVersion)
				if(!methods[vers.key]){
					methods[vers.key] = [:]
				}

				if(!methods['values']){
					methods['values'] = [:]
					methods['values'] = json.get('VALUES')
				}

				if(!methods['currentStable']){
					methods['currentStable'] = [:]
					methods['currentStable']['value'] = json.CURRENTSTABLE
				}

				if(!methods[vers.key]['deprecated']){
					methods[vers.key]['deprecated'] = []
					methods[vers.key]['deprecated'] = deprecated
				}

				if(!methods[vers.key]['defaultAction']){
					methods[vers.key]['defaultAction'] = defaultAction
				}

				if(!methods[vers.key]['testOrder']){
					methods[vers.key]['testOrder'] = []
					//methods[vers.key]['testOrder'] = testOrder
				}

				if(!methods[vers.key]['testUser']){
					methods[vers.key]['testUser'] = testUser
				}

				methods[vers.key][actionname] = apiDescriptor

			}

			if(methods){
				def cache = apiCacheService.setApiCache(apiName,methods)

				cache[vers.key].each(){ key1,val1 ->

					if(!['deprecated','defaultAction','testOrder','testUser'].contains(key1)){
						apiCacheService.setApiCache(apiName,key1, val1, vers.key)
					}
				}

			}
/*
			cache[vers.key].each(){ key1,val1 ->
				if(!['deprecated','defaultAction','testOrder','testUser'].contains(key1)){
					apiCacheService.unsetApiCachedResult(apiName, vers.key, key1)
				}
			}
*/

		}
		return methods
	}

}
