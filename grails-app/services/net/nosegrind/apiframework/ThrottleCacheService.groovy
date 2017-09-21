package net.nosegrind.apiframework

import grails.converters.JSON
//import grails.converters.XML
import grails.plugin.cache.CachePut
//import grails.plugin.cache.GrailsCacheManager
import org.grails.plugin.cache.GrailsCacheManager
import org.grails.groovy.grails.commons.*
import grails.core.GrailsApplication
import grails.plugin.springsecurity.SpringSecurityService


class ThrottleCacheService{

	static transactional = false
	
	GrailsApplication grailsApplication
	GrailsCacheManager grailsCacheManager
	SpringSecurityService springSecurityService

	// called through generateJSON()

	// TODO : change from userid to token/appId
	@org.springframework.cache.annotation.CachePut(value="Throttle",key="#userId")
	LinkedHashMap setThrottleCache(String userId, LinkedHashMap cache){
		try{
			return cache
		}catch(Exception e){
			throw new Exception("[ThrottleCacheService :: setThrottleCache] : Exception - full stack trace follows:",e)
		}
	}

	// TODO : change from userid to token/appId
	@org.springframework.cache.annotation.CachePut(value="Throttle",key="#userId")
	LinkedHashMap incrementThrottleCache(String userId){
		try{
			def cache = getLimitCache(userId)
			cache['rateLimitCurrent']++
			return cache
		}catch(Exception e){
			throw new Exception("[ThrottleCacheService :: incrementThrottleCache] : Exception - full stack trace follows:",e)
		}
	}

	@org.springframework.cache.annotation.CachePut(value="Throttle",key="#userId")
	LinkedHashMap lockLimitCache(String uri){
		def cache = getLimitCache(userId)
		cache['locked']=true
		return cache
	}

	// TODO : change from userid to token/appId
	@org.springframework.cache.annotation.CachePut(value="Throttle",key="#userId")
	LinkedHashMap checkLimitCache(String userId,String role){
		// check role against config role limit
	}

	LinkedHashMap getThrottleCache(String userId){
		try{
			def temp = grailsCacheManager?.getCache('Throttle')
			def cache = temp?.get(userId)
			if(cache?.get()){
				LinkedHashMap lcache = cache.get() as LinkedHashMap
				return lcache
			}else{ 
				return [:] 
			}

		}catch(Exception e){
			throw new Exception("[ThrottleCacheService :: getThrottleCache] : Exception - full stack trace follows:",e)
		}
	}
	
	List getCacheNames(){
		List cacheNames = []
		cacheNames = grailsCacheManager?.getCache('Throttle')?.getAllKeys() as List
		return cacheNames
	}
}
