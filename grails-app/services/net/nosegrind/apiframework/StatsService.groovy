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
import grails.util.Holders

import javax.annotation.Resource

import groovyx.gpars.*
import static groovyx.gpars.GParsPool.withPool

/**
 * A class for caching processed api calls and returning them
 * @author Owen Rubel
 */
class StatsService{

	GrailsApplication grailsApplication
	GrailsCacheManager grailsCacheManager

	/**
	 * Constructor
	 */
	public StatsService() {
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
	void flushAllStatsCache(){
		try {
			def temp = grailsCacheManager?.getCache('StatsCache')

			List cacheNames=temp.getAllKeys() as List
			cacheNames.each(){
					flushStatsCache(it.simpleKey)
			}
		}catch(Exception e){
			throw new Exception("[ApiCacheService :: flushApiCache] : Error :",e)
		}
	}


	/**
	 * Private method to flush the cache
	 * @see #flushAllApiCache
	 * @param controllername
	 */
	@CacheEvict(value="StatsCache",key={statsKey})
	private void flushStatsCache(String statsKey){}

	/**
	 *
	 * @return
	 */
	LinkedHashMap getStatsCache(){
		try{
			def stats = [:]

			def temp = grailsCacheManager?.getCache('StatsCache')

			List cacheNames=temp.getAllKeys() as List
			//def cache
			cacheNames.each(){
				def cache = temp.get(it)
				if(cache?.get()){
					stats[it.simpleKey] = cache.get() as LinkedHashMap
					return stats
				}else{
					return [:]
				}
			}
		}catch(Exception e){
			throw new Exception("[StatsService :: getStatsCache] : Exception - full stack trace follows:",e)
		}
	}

	/**
	 *
	 * @param statsKey
	 * @return
	 */
	LinkedHashMap getStatsCache(String statsKey){
		try{
			def temp = grailsCacheManager?.getCache('StatsCache')

			List cacheNames=temp.getAllKeys() as List
			def cache
			cacheNames.each(){
				if(it.simpleKey==statsKey) {
					cache = temp.get(it)
				}
			}

			if(cache?.get()){
				return cache.get() as LinkedHashMap
			}else{
				return [:]
			}

		}catch(Exception e){
			throw new Exception("[StatsService :: getStatsCache] : Exception - full stack trace follows:",e)
		}
	}


	void setStatsCache(int userId, int code, String uri){
		Integer day = (Integer) System.currentTimeMillis()/((1000*60*60*24)+1)
		try{
			setStatCache(day, userId, uri, code)
		}catch(Exception e){
			throw new Exception("[ApiCacheService :: setstatsCache] : Exception - full stack trace follows:",e)
		}
	}


	@CachePut(value="StatsCache",key={statsKey})
	private List setStatCache(int statsKey, int userId, String uri, int code){
		try{
			def cache = getStats(statsKey)
			List entry = [userId, code, uri, System.currentTimeMillis()]
			cache.add(entry)
			return cache
		}catch(Exception e){
			throw new Exception("[ApiCacheService :: setStatCache] : Exception - full stack trace follows:",e)
		}
	}


	List getStats(Integer statsKey){
		try{
			def temp = grailsCacheManager?.getCache('StatsCache')

			List cacheNames=temp.getAllKeys() as List
			def cache
			cacheNames.each(){
				if(it.simpleKey==statsKey) {
					cache = temp.get(it)
				}
			}

			if(cache?.get()){
				//println("has cache")
				return cache.get() as List
			}else{
				//println("no cache")
				return []
			}

		}catch(Exception e){
			throw new Exception("[ApiCacheService :: getStats] : Exception - full stack trace follows:",e)
		}
	}

	LinkedHashMap getStatsByWeek(Integer statsKey){
		int inc = 0
		LinkedHashMap stats = [:]
		while(inc!=7){
			stats[statsKey] = getStats(statsKey)
			inc++
			statsKey = statsKey-1
		}
		return stats
	}

	List getStatsByMonth(Integer statsKey){
		int inc = 0
		List stats = []
		while(inc!=4){
			stats.add(getStatsByWeek(statsKey))
			inc++
		}
		return stats
	}

	List getStatsByYear(Integer statsKey){
		int inc = 0
		List stats = []
		while(inc!=12){
			stats.add(getStatsByMonth(statsKey))
			inc++
		}
		return stats
	}

}
