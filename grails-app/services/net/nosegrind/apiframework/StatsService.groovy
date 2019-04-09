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
import grails.plugin.cache.GrailsConcurrentMapCache

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
			throw new Exception("[StatsService :: flushAllStatsCache] : Error :",e)
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
	List getStatsCache(){
		try{
			def stats = []

			def temp = grailsCacheManager?.getCache('StatsCache')

			List cacheNames=temp.getAllKeys() as List
			//def cache
			int i=0
			cacheNames.each(){
				def cache = temp.get(it)
				if(cache?.get()){
					stats[i] = cache.get() as List
					i++
				}
			}
			return stats
		}catch(Exception e){
			throw new Exception("[StatsService :: getStatsCache] : Exception - full stack trace follows:",e)
		}
	}


	void setStatsCache(int userId, int code, String uri){
		//println(System.currentTimeMillis()/((1000*60*60*24)+1))
		BigInteger currentTime = System.currentTimeMillis()
		try{
			String key = "k${currentTime}".toString()
			setStatCache(key, userId, uri, code, currentTime)
		}catch(Exception e){
			throw new Exception("[StatsService :: setstatsCache] : Exception - full stack trace follows:",e)
		}
	}


	@CachePut(value="StatsCache",key={key})
	private List setStatCache(String key, int userId, String uri, int code, BigInteger timestamp){
			List entry = [userId, code, uri, timestamp]
			return entry
	}


	// need this to be a range
	List getStats(Integer statsKey){
		try{
			def temp = grailsCacheManager?.getCache('StatsCache')

			List cacheNames=temp.getAllKeys() as List
			def cache = []
			cacheNames.each(){
				if(it.simpleKey==statsKey) {
					cache += temp.get(it)?.get() as List
				}
			}

			return cache

		}catch(Exception e){
			throw new Exception("[StatsService :: getStats] : Exception - full stack trace follows:",e)
		}
	}

	/*
	LinkedHashMap getStats(){
		try{
			def temp = grailsCacheManager?.getCache('StatsCache')

			List cacheNames=temp.getAllKeys() as List
			def cache
			cacheNames.each(){
				cache = temp.get(it.simpleKey)
			}

			if(cache?.get()){
				return cache.get() as LinkedHashMap
			}else{
				return [:]
			}

		}catch(Exception e){
			throw new Exception("[StatsService :: getStats] : Exception - full stack trace follows:",e)
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
*/
}
