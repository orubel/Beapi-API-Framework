package net.nosegrind.apiframework

import grails.converters.JSON
import grails.converters.XML
import org.grails.web.json.JSONObject
import grails.util.Metadata
import grails.plugin.cache.CacheEvict
import grails.plugin.cache.CachePut
import org.springframework.cache.annotation.*
import org.grails.plugin.cache.GrailsCacheManager
import org.grails.groovy.grails.commons.*
import grails.core.GrailsApplication
import grails.util.Holders
import grails.plugin.cache.GrailsConcurrentMapCache
import grails.plugin.cache.GrailsValueWrapper

import groovyx.gpars.*
import static groovyx.gpars.GParsPool.withPool

/**
 * A class for caching processed api calls and returning them
 * @author Owen Rubel
 */


class ApiCacheService{

	/**
	 * Application Class
	 */
	GrailsApplication grailsApplication
	/**
	 * Cache Manager Class
	 */
	GrailsCacheManager grailsCacheManager

	int cores = Holders.grailsApplication.config.apitoolkit['procCores'] as int

	/**
	 * Constructor
	 */
	public ApiCacheService() {
		this.grailsApplication = Holders.grailsApplication
	}

	/*
	 * Only flush on RESTART.
	 * DO NOT flush while LIVE!!!
	 * Need to lock this down to avoid process calling this.
	 */

	/**
	 * Flushes all data from the API Cache; generally only called on startup to create a 'clean' cache
	 * @see BeapiApiFrameworkGrailsPlugin
	 * @return
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


	@CacheEvict(value='ApiCache',key={controllername})
	private void flushApiCache(String controllername){}


	//@org.springframework.cache.annotation.CachePut(value="ApiCache",key="#controllername")
	/**
	 * Method to set the apicache associated with the controller name
	 * @param String controllername for designated endpoint
	 * @param LinkedHashMap a map of all apidoc information for all roles which can be easily traversed
	 * @return A LinkedHashMap of Cached data associated with controllername
	 */
	@CachePut(value='ApiCache',key={controllername})
	LinkedHashMap setApiCache(String controllername,LinkedHashMap apidesc){
		return apidesc
	}

	/**
	 * Method to set the apicache associated with the controller name using pregenerated apidoc
	 * @param String controllername for designated endpoint
	 * @param String methodname for designated endpoint
	 * @param ApiDescriptor apidocs for current application
	 * @param String apiversion of current application
	 * @return A LinkedHashMap of Cached data associated with controllername
	 */
	@CachePut(value='ApiCache',key={controllername})
	LinkedHashMap setApiCache(String controllername,String methodname, ApiDescriptor apidoc, String apiversion){
		try{
			LinkedHashMap cache = getApiCache(controllername)
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
			cache[apiversion][methodname]['stats'] = [] // [[code:200,cnt:56,time:123456789]]
			cache[apiversion][methodname]['doc'] = generateApiDoc(controllername, methodname,apiversion)
			cache[apiversion][methodname]['doc']['hookRoles'] = cache[apiversion][methodname]['hookRoles']
			cache[apiversion][methodname]['doc']['batchRoles'] = cache[apiversion][methodname]['batchRoles']

			return cache
		}catch(Exception e){
			throw new Exception("[ApiCacheService :: setApiCache] : Exception - full stack trace follows:",e)
		}
	}

	/**
	 * Method to set the cached result associated with endpoint; only works with GET method
	 * and uses HASH of all id's as ID for the cache itself. Also checks authority and format
	 * @param String hash of all ids sent for given endpoint
	 * @param String controllername for designated endpoint
	 * @param String apiversion of current application
	 * @param String methodname for designated endpoint
	 * @param String authority of user making current request for cache which we are storing
	 * @param String format of cache being stored (ie xml/json)
	 * @param String content of 'response' to be added to endpoint cache
	 * @return A LinkedHashMap of Cached data associated with controllername
	 */
	// TODO: parse for XML as well
	@CachePut(value='ApiCache',key={controllername})
	LinkedHashMap setApiCachedResult(String cacheHash, String controllername, String apiversion, String methodname, String authority, String format, String content){
		try {
			LinkedHashMap cachedResult = [:]
			switch(format){
				case 'XML':
					Object xml = XML.parse(content)
					cachedResult[authority] = [:]
					cachedResult[authority][format] = xml
					break
				case 'JSON':
				default:
					JSONObject json = JSON.parse(content) as JSONObject
					cachedResult[authority] = [:]
					cachedResult[authority][format] = json
					break
			}

			LinkedHashMap cache = getApiCache(controllername)
			if (cache[apiversion]) {
				cache[apiversion][methodname]['cachedResult'] = [:]
				cache[apiversion][methodname]['cachedResult'][cacheHash] = cachedResult
			}
			return cache
		}catch(Exception e){
			throw new Exception("[ApiCacheService :: setApiCache] : Exception - full stack trace follows:",e)
		}
	}

	/**
	 * Method to autogenerate the apidoc data set from loaded IO state files
	 * @param String controllername for designated endpoint
	 * @param String actionname for designated endpoint
	 * @param String apiversion of current application
	 * @return A LinkedHashMap of all apidoc information for all roles which can be easily traversed
	 */
	LinkedHashMap generateApiDoc(String controllername, String actionname, String apiversion){
		try{
			LinkedHashMap doc = [:]
			LinkedHashMap cache = getApiCache(controllername)

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

					doc['inputjson'] = processJson(doc['receives'])
					doc['outputjson'] = processJson(doc['returns'])
				}
	
			}
			return doc
		}catch(Exception e){
			throw new Exception("[ApiCacheService :: generateApiDoc] : Exception - full stack trace follows:",e)
		}
	}

	/**
	 * Method to load the 'ApiCache' cache object
	 * @param String controller name for designated endpoint
	 * @return A LinkedHashMap of Cached data associated with controllername
	 */
	LinkedHashMap getApiCache(String controllername){
		try{
			GrailsConcurrentMapCache temp = grailsCacheManager?.getCache('ApiCache')
			List cacheNames=temp.getAllKeys() as List
			GrailsValueWrapper cache
			cacheNames.each() { it2 ->
				if (it2.simpleKey == controllername) {
						cache = temp.get(it2)
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

	/**
	 * Method to load the list of all object contained in the 'ApiCache' cache
	 * @return A List of keys of all object names contained with the 'ApiCache'
	 */
	List getCacheNames(){
		List cacheNames = []
		GrailsConcurrentMapCache temp = grailsCacheManager?.getCache('ApiCache')
		cacheNames = temp.getAllKeys() as List
		return cacheNames
	}

	/*
 * TODO: Need to compare multiple authorities
 */
	private String processJson(LinkedHashMap returns){
		// int cores = grailsApplication.config.apitoolkit.procCores as int
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
						GParsPool.withPool(this.cores,{
							j.eachParallel { key, val ->
								if (val instanceof List) {
									def child = [:]
									withPool(this.cores) {
										val.eachParallel { it2 ->
											withPool(this.cores) {
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
