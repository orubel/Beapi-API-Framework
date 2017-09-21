package net.nosegrind.apiframework

import grails.core.GrailsApplication

import org.grails.groovy.grails.commons.*
import javax.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.springframework.web.context.request.ServletRequestAttributes

import org.springframework.web.context.request.ServletRequestAttributes

import javax.servlet.http.HttpServletRequest

class TraceService {

	GrailsApplication grailsApplication
	TraceCacheService traceCacheService

	static transactional = false


	private HttpServletRequest getRequest(){
		HttpServletRequest request = ((ServletRequestAttributes) RCH.currentRequestAttributes()).getRequest()
		return request
	}


	public void startTrace(String className, String methodName){
		Long mStart = System.currentTimeMillis()
		String uri = getRequest().forwardURI
		LinkedHashMap cache = traceCacheService.getTraceCache(uri)
		String loc = "${className}/${methodName}".toString()

		if(!cache['calls'] || cache['calls']==0){
			cache['calls'] = [:]
		}

		Long order = (cache['calls'])?cache['calls'].size()+1:1
		cache['calls']["${order}"] = [:]
		cache['calls']["${order}"][loc] = [:]
		cache['calls']["${order}"][loc]['start'] = mStart
		cache['calls']["${order}"][loc]['stop'] = 0
		traceCacheService.setTraceMethod(uri, cache)
	}

	public LinkedHashMap endTrace(String className, String methodName) {
		String uri = getRequest().forwardURI
		LinkedHashMap cache = traceCacheService.getTraceCache(uri)
		String loc = "${className}/${methodName}".toString()
		Long order = cache['calls'].size()

		// if uri!= loc, walk backwards through cache and find
		// 'loc' that matches and has empty 'stop' value
		if (cache.calls?."${order}"?."${loc}" == null) {
			for(int i = order; i > 0; i--){
				if (cache.calls?."${i}"?."${loc}" != null) {
					cache['calls']["${i}"]["${loc}"]['stop'] = System.currentTimeMillis()
					break;
				}
			}
		}else {
			cache['calls']["${order}"][loc]['stop'] = System.currentTimeMillis()
		}

		return traceCacheService.setTraceMethod(uri, cache)
	}

	public LinkedHashMap endAndReturnTrace(String className, String methodName){
		String uri = getRequest().forwardURI
		LinkedHashMap returnCache = endTrace(className, methodName)
		LinkedHashMap newTrace = processTrace(returnCache)
		traceCacheService.flushCache(uri)
		return newTrace
	}

	private LinkedHashMap processTrace(LinkedHashMap cache){
		LinkedHashMap newTrace = [:]
		newTrace['elapsedTime'] = 0
		cache['calls'].sort{ a, b -> b.key <=> a.key }
		cache['calls'].each() { it ->
			Long startTime = 0
			Long stopTime = 0
			it.value.each() { it2 ->
					String loc = it2.key

					if(startTime==0){ startTime=it2.value['start'] }
					stopTime = it2.value['stop']

					newTrace[it.key] = ['time': getElapsedTime(it2.value['start'], it2.value['stop']), 'loc': loc]
					if(!['ProfilerInterceptor/before','ProfilerInterceptor/after'].contains(loc)) {
						newTrace['elapsedTime'] += newTrace[it.key]['time']
					}
			}

			//newTrace['elapsedTime'] = getElapsedTime(startTime,stopTime)
		}
		return newTrace
	}

	private Long getElapsedTime(Long startTime, Long stopTime){
		Long elapsedTime = stopTime - startTime
		if(elapsedTime>=0) {
			return elapsedTime
		}else{
			throw new Exception("[TraceService :: getElapsedTime] : Exception - stopTime is less that startTime")
		}
	}
}
