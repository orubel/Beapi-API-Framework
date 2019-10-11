package net.nosegrind.apiframework

import grails.converters.JSON
//import grails.converters.XML
import grails.plugin.cache.*
import org.grails.plugin.cache.*
import org.grails.plugin.cache.GrailsCacheManager
import org.grails.groovy.grails.commons.*
import grails.core.GrailsApplication

/*
* Want to be able to :
*  - cache each 'class/method' and associated start/end times and order  in which they are called
*  - prior to API response return, generateJSON() and return JSON as response
 */

class TraceCacheService{

	static transactional = false
	
	GrailsApplication grailsApplication
	GrailsCacheManager grailsCacheManager
	
	// called through generateJSON()

	public void flushCache(String uri){
		try{
			grailsCacheManager?.getCache('Trace').clear()
		}catch(Exception e){
			throw new Exception("[TraceCacheService :: getTraceCache] : Exception - full stack trace follows:",e)
		}
	}

	@CachePut(value="Trace",key={uri})
	LinkedHashMap putTraceCache(String uri, LinkedHashMap cache){
		try{
			return cache
		}catch(Exception e){
			throw new Exception("[TraceCacheService :: putTraceCache] : Exception - full stack trace follows:",e)
		}
	}


	// issue here
	@CachePut(value="Trace",key={uri})
	LinkedHashMap setTraceMethod(String uri,LinkedHashMap cache){
		try{
			return cache
		}catch(Exception e){
			throw new Exception("[TraceCacheService :: setTraceCache] : Exception - full stack trace follows:",e)
		}
	}

	LinkedHashMap getTraceCache(String uri){
		println("### getTraceCache")
		try{
			GrailsConcurrentMapCache temp = grailsCacheManager?.getCache('Trace')
			def temp2 = temp?.getAllKeys() as List
			temp2.each() { it2 ->
				println("key:"+it2.simpleKey)
				def cache = temp?.get(it2.simpleKey)
				println("${uri} / ${cache}")
			}
			def cache = temp?.get(uri)
			if(cache?.get()){
				println(cache.get())
				return cache.get() as LinkedHashMap
			}else{
				return [:] 
			}
		}catch(Exception e){
			throw new Exception("[TraceCacheService :: getTraceCache] : Exception - full stack trace follows:",e)
		}
	}
	
	List getCacheNames(){
		List cacheNames = []
		cacheNames = grailsCacheManager?.getCache('Trace')?.getAllKeys() as List
		return cacheNames
	}
}
