package net.nosegrind.apiframework

import grails.converters.JSON
//import grails.converters.XML
import org.grails.web.json.JSONObject
import grails.util.Metadata
import grails.plugin.cache.CacheEvict
import grails.plugin.cache.CachePut
import org.springframework.cache.annotation.*
import org.grails.plugin.cache.GrailsCacheManager
//import grails.plugin.cache.GrailsConcurrentMapCacheManager
import org.grails.groovy.grails.commons.*
import grails.core.GrailsApplication

import javax.annotation.Resource

import groovyx.gpars.*
import static groovyx.gpars.GParsPool.withPool

class ApiCacheService{

	static transactional = false

	GrailsApplication grailsApplication
	GrailsCacheManager grailsCacheManager

	/*
	 * Only flush on RESTART.
	 * DO NOT flush while LIVE!!!
	 * Need to lock this down to avoid process calling this.
	 */
	void flushAllApiCache(){
		try {
			grailsApplication?.controllerClasses?.each { controllerClass ->
				String controllername = controllerClass.logicalPropertyName
				if (controllername != 'aclClass') {
					flushApiCache(controllername)
				}
			}
		}catch(Exception e){
			throw new Exception("[ApiCacheService :: flushApiCache] : Error :",e)
		}
	}

	/*
	 * Only flush on RESTART.
	 * DO NOT flush while LIVE!!!
	 */
	//@org.springframework.cache.annotation.CacheEvict(value="ApiCache",key="#controllername")
	@CacheEvict(value="ApiCache",key={controllername})
	void flushApiCache(String controllername){}


	//@org.springframework.cache.annotation.CachePut(value="ApiCache",key="#controllername")
	@CachePut(value="ApiCache",key={controllername})
	LinkedHashMap setApiCache(String controllername,LinkedHashMap apidesc){
		return apidesc
	}

	//@org.springframework.cache.annotation.CachePut(value="ApiCache",key="#controllername")
	@CachePut(value="ApiCache",key={controllername})
	LinkedHashMap setApiCache(String controllername,String methodname, ApiDescriptor apidoc, String apiversion){
		try{
			def cache = getApiCache(controllername)
			if(!cache[apiversion]){
				cache[apiversion] = [:]
			}
			if(!cache[apiversion][methodname]){
				cache[apiversion][methodname] = [:]
			}

			cache[apiversion][methodname]['name'] = apidoc.name
			cache[apiversion][methodname]['description'] = apidoc.description
			cache[apiversion][methodname]['receives'] = apidoc.receives
			cache[apiversion][methodname]['returns'] = apidoc.returns
			cache[apiversion][methodname]['doc'] = generateApiDoc(controllername, methodname,apiversion)
			cache[apiversion][methodname]['doc']['hookRoles'] = cache[apiversion][methodname]['hookRoles']
			cache[apiversion][methodname]['doc']['batchRoles'] = cache[apiversion][methodname]['batchRoles']

			return cache
		}catch(Exception e){
			throw new Exception("[ApiCacheService :: setApiCache] : Exception - full stack trace follows:",e)
		}
	}

	//@org.springframework.cache.annotation.CachePut(value="ApiCache",key="#controllername")
	@CachePut(value="ApiCache",key={controllername})
	LinkedHashMap setApiCachedResult(String controllername, String apiversion, String methodname, String authority, String format, String content){
		try{
			JSONObject json = JSON.parse(content)
			LinkedHashMap cachedResult = [:]
			cachedResult[authority] = [:]
			cachedResult[authority][format] = json

			def cache = getApiCache(controllername)
			cache[apiversion][methodname]['cachedResult'] = cachedResult
			return cache
		}catch(Exception e){
			throw new Exception("[ApiCacheService :: setApiCache] : Exception - full stack trace follows:",e)
		}
	}
	
	LinkedHashMap generateApiDoc(String controllername, String actionname, String apiversion){
		try{
			LinkedHashMap doc = [:]
			def cache = getApiCache(controllername)

			String apiPrefix = "v${Metadata.current.getApplicationVersion()}"

			if(cache){
				String path = "/${apiPrefix}-${apiversion}/${controllername}/${actionname}"
				doc = ['path':path,'method':cache[apiversion][actionname]['method'],'description':cache[apiversion][actionname]['description']]
				if(cache[apiversion][actionname]['receives']){
					doc['receives'] = [:]
					for(receiveVal in cache[apiversion][actionname]['receives']){
						if(receiveVal?.key) {
							doc['receives']["$receiveVal.key"] = receiveVal.value
						}
					}
				}

				if(cache[apiversion][actionname]['pkey']) {
					doc['pkey'] = []
					cache[apiversion][actionname]['pkey'].each(){
							doc['pkey'].add(it)
					}
				}

				if(cache[apiversion][actionname]['fkeys']) {
					doc['fkeys'] = [:]
					for(fkeyVal in cache[apiversion][actionname]['fkeys']){
						if(fkeyVal?.key) {
							doc['fkeys']["$fkeyVal.key"] = fkeyVal.value as JSON
						}
					}
				}

				if(cache[apiversion][actionname]['returns']){
					doc['returns'] = [:]
					for(returnVal in cache[apiversion][actionname]['returns']){
						if(returnVal?.key) {
							doc['returns']["$returnVal.key"] = returnVal.value
						}
					}

					doc['inputjson'] = processJson(doc["receives"])
					doc['outputjson'] = processJson(doc["returns"])
				}
	
			}
			return doc
		}catch(Exception e){
			throw new Exception("[ApiCacheService :: generateApiDoc] : Exception - full stack trace follows:",e)
		}
	}
	
	LinkedHashMap getApiCache(String controllername){
		try{
			def temp = grailsCacheManager?.getCache('ApiCache')

			List cacheNames=temp.getAllKeys() as List
			def cache
			cacheNames.each(){
				if(it.simpleKey==controllername) {
					cache = temp.get(it)
				}
			}

			if(cache?.get()){
				return cache.get() as LinkedHashMap
			}else{ 
				return [:] 
			}

		}catch(Exception e){
			throw new Exception("[ApiCacheService :: getApiCache] : Exception - full stack trace follows:",e)
		}
	}
	
	List getCacheNames(){
		List cacheNames = []
		def temp = grailsCacheManager?.getCache('ApiCache')
		cacheNames = temp.getAllKeys() as List
		return cacheNames
	}

	/*
 * TODO: Need to compare multiple authorities
 */
	private String processJson(LinkedHashMap returns){
		try{
			LinkedHashMap json = [:]
			returns.each{ p ->
				p.value.each{ it ->
					if(it) {
						def paramDesc = it

						LinkedHashMap j = [:]
						if (paramDesc?.values) {
							j["$paramDesc.name"] = []
						} else {
							String dataName = (['PKEY', 'FKEY', 'INDEX'].contains(paramDesc?.paramType?.toString())) ? 'ID' : paramDesc.paramType
							j = (paramDesc?.mockData?.trim()) ? ["$paramDesc.name": "$paramDesc.mockData"] : ["$paramDesc.name": "$dataName"]
						}
						GParsPool.withPool(20,{
							j.eachParallel { key, val ->
								if (val instanceof List) {
									def child = [:]
									withPool(20) {
										val.eachParallel { it2 ->
											withPool(20) {
												it2.eachParallel { key2, val2 ->
													child[key2] = val2
												}
											}
										}
									}
									json[key] = child
								} else {
									json[key] = val
								}
							}
						})
					}
				}
			}

			String jsonReturn
			if(json){
				jsonReturn = json as JSON
			}
			return jsonReturn
		}catch(Exception e){
			throw new Exception("[ApiCacheService :: processJson] : Exception - full stack trace follows:",e)
		}
	}
}
