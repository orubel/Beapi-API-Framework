package net.nosegrind.apiframework

import javax.annotation.Resource

import java.text.SimpleDateFormat
import grails.converters.JSON
import grails.converters.XML
import grails.web.servlet.mvc.GrailsParameterMap
import javax.servlet.forward.*
import org.grails.groovy.grails.commons.*
import grails.core.GrailsApplication
import grails.util.Holders
import org.grails.core.artefact.DomainClassArtefactHandler

import org.springframework.beans.factory.annotation.Autowired
import org.grails.plugin.cache.GrailsCacheManager

import com.google.common.hash.Hashing
import java.nio.charset.StandardCharsets

import org.grails.core.DefaultGrailsDomainClass
import grails.orm.HibernateCriteriaBuilder
import org.grails.web.util.WebUtils
import javax.servlet.http.HttpServletResponse

import groovyx.gpars.*
import static groovyx.gpars.GParsPool.withPool

import javax.servlet.http.HttpServletResponse


// extended by ProfilerCommLayer
abstract class ProfilerCommProcess {

    @Resource
    GrailsApplication grailsApplication

    @Autowired
    GrailsCacheManager grailsCacheManager

    @Autowired
    ThrottleCacheService throttleCacheService

	@Resource
	TraceService traceService

    List formats = ['text/json','application/json','text/xml','application/xml']
    List optionalParams = ['method','format','contentType','encoding','action','controller','v','apiCombine', 'apiObject','entryPoint','uri','apiObjectVersion']

    // Used for parallelization
    int cores = Holders.grailsApplication.config.apitoolkit['procCores'] as Integer

	String docUrl = Holders.grailsApplication.config.apitoolkit.documentationUrl

    boolean batchEnabled = Holders.grailsApplication.config.apitoolkit.batching.enabled
    boolean chainEnabled = Holders.grailsApplication.config.apitoolkit.chaining.enabled


    /**
     * Given a deprecationDate, checks the deprecation date against todays date; returns boolean
     * Used mainly to check whether API Version is deprecated
     * @see ApiCommLayer#handleApiRequest(List, String, RequestMethod, HttpServletResponse, GrailsParameterMap)
     * @see ApiCommLayer#handleBatchRequest(List, String, RequestMethod, HttpServletResponse, GrailsParameterMap)
     * @see ApiCommLayer#handleChainRequest(List, String, RequestMethod, HttpServletResponse, GrailsParameterMap)
     * @param String deprecationDate
     * @return boolean returns false if deprecation date is equal or later than today
     */
    boolean checkDeprecationDate(String deprecationDate){
        try{
            traceService.startTrace('ProfilerCommProcess','checkDeprecationDate')
            Date ddate = new SimpleDateFormat("MM/dd/yyyy").parse(deprecationDate)
            Date deprecated = new Date(ddate.time)
            Date today = new Date()
            if(deprecated < today ) {
                traceService.endTrace('ProfilerCommProcess','checkDeprecationDate')
                return true
            }
            traceService.endTrace('ProfilerCommProcess','checkDeprecationDate')
            return false
        }catch(Exception e){
			String msg = this.messageSource.getMessage("error.profilerCommProcess.checkDeprecationDate", [docUrl,e] as Object[], 'Default Message', request.locale)
			throw new Exception(msg)
            //throw new Exception("[ApiCommProcess :: checkDeprecationDate] : Exception - full stack trace follows:",e)
        }
    }

    /**
     * Given the RequestMethod Object for the request, the endpoint request method and a boolean declaration of whether it is a restAlt(ernative),
     * test To check whether RequestMethod value matches expected request method for endpoint; returns boolean
     * @param RequestMethod request method for httprequest
     * @param String method associated with endpoint
     * @param boolean a boolean value determining if endpoint is 'restAlt' (OPTIONS,TRACE,etc)
     * @return Boolean returns true if not endpoint method matches request method
     */
    boolean checkRequestMethod(RequestMethod mthd, String method, boolean restAlt){
        traceService.startTrace('ProfilerCommProcess','checkRequestMethod')
        if(!restAlt) {
            if(mthd.getKey() == method){
                traceService.endTrace('ProfilerCommProcess','checkRequestMethod')
                return true
            }else{
                traceService.endTrace('ProfilerCommProcess','checkRequestMethod')
				String msg = this.messageSource.getMessage("error.profilerCommProcess.checkRequestMethod", [docUrl] as Object[], 'Default Message', request.locale)
                errorResponse([400,msg])
                return false
            }
        }
        traceService.endTrace('ProfilerCommProcess','checkRequestMethod')
        return true
    }


    // TODO: put in OPTIONAL toggle in application.yml to allow for this check
    /**
     * Given the request params and endpoint request definitions, test to check whether the request params match the expected endpoint params; returns boolean
     * @see ApiFrameworkInterceptor#before()
     * @see BatchInterceptor#before()
     * @see ChainInterceptor#before()
     * @param GrailsParameterMap Map of params created from the request data
     * @param LinkedHashMap map of variables defining endpoint request variables
     * @return Boolean returns false if request variable keys do not match expected endpoint keys
     */
    boolean checkURIDefinitions(GrailsParameterMap params,ArrayList requestList, List authority){
        traceService.startTrace('ProfilerCommProcess','checkURIDefinitions')

        ArrayList reservedNames = ['batchLength','batchInc','chainInc','apiChain','apiResult','combine','_','batch','max','offset','apiObjectVersion']
        ArrayList paramsList

        try {

            if (requestList.contains('*')) {
                traceService.endTrace('ProfilerCommProcess','checkURIDefinitions')
                return true
            } else {
                LinkedHashMap methodParams = getMethodParams(params)
                paramsList = methodParams.keySet() as ArrayList
                // remove reservedNames from List

                reservedNames.each() { paramsList.remove(it) }

                //println("RL (expected):"+requestList)
                //println("PL (sent):"+paramsList)

                if (paramsList.size() == requestList.intersect(paramsList).size()) {
                    traceService.endTrace('ProfilerCommProcess','checkURIDefinitions')
                    return true
                }
            }

			String msg = this.messageSource.getMessage("error.profilerCommProcess.checkURIDefinitions", [requestList,paramsList,docUrl] as Object[], 'Default Message', request.locale)
            errorResponse([400,msg])

			traceService.endTrace('ProfilerCommProcess','checkURIDefinitions')

            return false
        }catch(Exception e) {
            throw new Exception("[ApiCommProcess :: checkURIDefinitions] : Exception - full stack trace follows:",e)
        }
        return false
    }


    /**
     * Given a request method, format, params and response result, Formats response; returns a parsed string based on format
     * @see ApiFrameworkInterceptor#after()
     * @see BatchInterceptor#after()
     * @see ChainInterceptor#after()
     * @param RequestMethod mthd
     * @param String format
     * @param GrailsParameterMap Map of params created from the request data
     * @param HashMap result
     * @return
     */
    String parseResponseMethod(RequestMethod mthd, String format, GrailsParameterMap params, LinkedHashMap result){
        traceService.startTrace('ProfilerCommProcess','parseResponseMethod')
        String content
        switch(mthd.getKey()) {
            case 'PURGE':
                // cleans cache; disabled for now
                break
            case 'TRACE':
                break
            case 'HEAD':
                break
            case 'OPTIONS':
                String doc = getApiDoc(params)
                content = doc
                break
            case 'GET':
            case 'PUT':
            case 'POST':
            case 'DELETE':
                switch(format){
                    case 'XML':
                        content = result as XML
                        break
                    case 'JSON':
                    default:
                        content = result as JSON
                }
                break
        }
        traceService.endTrace('ProfilerCommProcess','parseResponseMethod')
        return content
    }


	/**
	 * Given the request method and request params, format response for preHandler based upon request method
	 * @see ApiFrameworkInterceptor#before()
	 * @see BatchInterceptor#before()
	 * @see ChainInterceptor#before()
	 * @param RequestMethod mthd
	 * @param GrailsParameterMap Map of params created from the request data
	 * @deprecated
	 * @return
	 */
	String parseRequestMethod(RequestMethod mthd, GrailsParameterMap params){
		traceService.startTrace('ProfilerCommProcess','parseRequestMethod')
		String content
		switch(mthd.getKey()) {
			case 'PURGE':
				// cleans cache; disabled for now
				break
			case 'TRACE':
				// placeholder
				break
			case 'HEAD':
				// placeholder
				break
			case 'OPTIONS':
				content = getApiDoc(params)
				break
		}
		traceService.endTrace('ProfilerCommProcess','parseRequestMethod')
		return content
	}

	/**
	 * Given the returning resource and a list of response variables, creates and returns a HashMap from request params sent that match endpoint params
	 * @see ApiCommLayer#handleApiResponse(LinkedHashMap, List, RequestMethod, String, HttpServletResponse, HashMap, GrailsParameterMap)
	 * @see ApiCommLayer#handleBatchResponse(LinkedHashMap, List, RequestMethod, String, HttpServletResponse, HashMap, GrailsParameterMap)
	 * @see ApiCommLayer#handleChainResponse(LinkedHashMap, List, RequestMethod, String, HttpServletResponse, HashMap, GrailsParameterMap)
	 * @param HashMap model
	 * @param ArrayList responseList
	 * @return LinkedHashMap parses all data for expected response params for users ROLE
	 */
	LinkedHashMap parseURIDefinitions(LinkedHashMap model,ArrayList responseList){
		traceService.startTrace('ProfilerCommProcess','parseURIDefinitions')
		if (model["0"].getClass().getName() == 'java.util.LinkedHashMap') {
			model.each() { key, val ->
				model[key] = parseURIDefinitions(val, responseList)
			}
			traceService.endTrace('ProfilerCommProcess','parseURIDefinitions')
			return model
		} else {
			try {
				ArrayList paramsList = (model.size() == 0) ? [:] : model.keySet() as ArrayList
				paramsList?.removeAll(optionalParams)
				if (!responseList.containsAll(paramsList)) {
					paramsList.removeAll(responseList)
					paramsList.each() { it2 ->
						model.remove(it2.toString())
					}

					if (!paramsList) {
						traceService.endTrace('ProfilerCommProcess','parseURIDefinitions')
						return [:]
					} else {
						traceService.endTrace('ProfilerCommProcess','parseURIDefinitions')
						return model
					}
				} else {
					traceService.endTrace('ProfilerCommProcess','parseURIDefinitions')
					return model
				}

			} catch (Exception e) {
				traceService.endTrace('ProfilerCommProcess','parseURIDefinitions')
				throw new Exception('[ApiCommProcess :: parseURIDefinitions] : Exception - full stack trace follows:', e)
			}
		}
	}


	/**
	 * Given request method and endpoint method/protocol,tests for endpoint method matching request method. Will also return true if
	 * request method is Rest Alternative; returns boolean
	 * @see ApiCommLayer#handleApiRequest(List, String, RequestMethod, HttpServletResponse, GrailsParameterMap)
	 * @see ApiCommLayer#handleBatchRequest(List, String, RequestMethod, HttpServletResponse, GrailsParameterMap)
	 * @see ApiCommLayer#handleChainRequest(List, String, RequestMethod, HttpServletResponse, GrailsParameterMap)
	 * @param String method
	 * @param RequestMethod mthd
	 * @return Boolean returns false if SENT request method and EXPECTED request method do not match
	 */
	boolean isRequestMatch(String method,RequestMethod mthd){
		traceService.startTrace('ProfilerCommProcess','isRequestMatch')
		if(RequestMethod.isRestAlt(mthd.getKey())){
			traceService.endTrace('ProfilerCommProcess','isRequestMatch')
			return true
		}else{
			if(method == mthd.getKey()){
				traceService.endTrace('ProfilerCommProcess','isRequestMatch')
				return true
			}else{
				traceService.endTrace('ProfilerCommProcess','isRequestMatch')
				return false
			}
		}
		traceService.endTrace('ProfilerCommProcess','isRequestMatch')
		return false
	}


	/*
	* TODO : USED FOR TEST
	List getRedirectParams(GrailsParameterMap params){
		def uri = grailsApplication.mainContext.servletContext.getControllerActionUri(request)
		return uri[1..(uri.size()-1)].split('/')
	}
	*/


	/**
	 * Given the request params, returns a parsed HashMap of all request params NOT found in optionalParams List
	 * @see checkURIDefinitions(GrailsParameterMap,HashMap)
	 * @param GrailsParameterMap Map of params created from the request data
	 * @return LinkedHashMap map of all params associated with request for use with api call
	 */
	LinkedHashMap getMethodParams(GrailsParameterMap params){
		traceService.startTrace('ProfilerCommProcess','getMethodParams')
		try{
			LinkedHashMap paramsRequest = [:]
			paramsRequest = params.findAll { it2 -> !optionalParams.contains(it2.key) }
			traceService.endTrace('ProfilerCommProcess','getMethodParams')
			return paramsRequest
		}catch(Exception e){
			traceService.endTrace('ProfilerCommProcess','getMethodParams')
			String msg = this.messageSource.getMessage("error.profilerCommProcess.getMethodParams", [docUrl,e] as Object[], 'Default Message', request.locale)
			throw new Exception(msg)
		}
		traceService.endTrace('ProfilerCommProcess','getMethodParams')
		return [:]
	}


    /**
     * Returns authorities associated with loggedIn user; creates default authority which will be checked
     * against endpoint 'roles' if no 'loggedIn' user is found
     * @see #checkURIDefinitions(GrailsParameterMap,LinkedHashMap)
     * @see #checkLimit(int)
     * @see ApiCommLayer#handleApiResponse(LinkedHashMap, List, RequestMethod, String, HttpServletResponse, HashMap, GrailsParameterMap)
     * @return String Role of current principal (logged in user)
     */
    List getUserRole(List roles) {
        traceService.startTrace('ProfilerCommProcess','getUserRole')
        List authority = ['permitAll']
        springSecurityService.principal.authorities*.authority.each{
            if(roles.contains(it)){
                authority.add(it)
            }
        }
        traceService.endTrace('ProfilerCommProcess','getUserRole')
        return authority
    }


	/**
	 * Given a Map, will process cased on type of object and return a HashMap;
	 * Called by the PostHandler
	 * @see ApiFrameworkInterceptor#after()
	 * @see BatchInterceptor#after()
	 * @see ChainInterceptor#after()
	 * @param Map map
	 * @return LinkedHashMap commonly formatted linkedhashmap
	 */
	LinkedHashMap convertModel(Map map){
		try{
			traceService.startTrace('ProfilerCommProcess','convertModel')
			LinkedHashMap newMap = [:]
			String k = map.entrySet().toList().first().key

			if(map && (!map?.response && !map?.metaClass && !map?.params)){
				if (DomainClassArtefactHandler?.isDomainClass(map[k].getClass()) && map[k]!=null) {
					newMap = formatDomainObject(map[k])
					traceService.endTrace('ProfilerCommProcess','convertModel')
					return newMap
				} else if(['class java.util.LinkedList', 'class java.util.ArrayList'].contains(map[k].getClass().toString())) {
					newMap = formatList(map[k])
					traceService.endTrace('ProfilerCommProcess','convertModel')
					return newMap
				} else if(['class java.util.Map', 'class java.util.LinkedHashMap','class java.util.HashMap'].contains(map[k].getClass().toString())) {
					newMap = formatMap(map[k])
					traceService.endTrace('ProfilerCommProcess','convertModel')
					return newMap
				}
			}
			traceService.endTrace('ProfilerCommProcess','convertModel')
			return newMap
		}catch(Exception e){
			throw new Exception("[ApiCommProcess :: convertModel] : Exception - full stack trace follows:",e)
		}
	}


	/**
	 * Given an Object detected as a DomainObject, processes in a standardized format and returns a HashMap;
	 * Used by convertModel and called by the PostHandler
	 * @see #convertModel(Map)
	 * @param Object data
	 * @return LinkedHashMap commonly formatted linkedhashmap
	 */
	LinkedHashMap formatDomainObject(Object data){
		try {
			traceService.startTrace('ProfilerCommProcess','formatDomainObject')

			LinkedHashMap newMap = [:]
			newMap.put('id', data?.id)
			newMap.put('version', data?.version)

			//DefaultGrailsDomainClass d = new DefaultGrailsDomainClass(data.class)

			DefaultGrailsDomainClass d = grailsApplication?.getArtefact(DomainClassArtefactHandler.TYPE, data.class.getName())

			if (d!=null) {
				// println("PP:"+d.persistentProperties)

				d?.persistentProperties?.each() { it2 ->
					if (it2?.name) {
						if(DomainClassArtefactHandler.isDomainClass(data[it2.name].getClass())) {
							newMap["${it2.name}Id"] = data[it2.name].id
						} else {
							newMap[it2.name] = data[it2.name]
						}
					}
				}

			}
			traceService.endTrace('ProfilerCommProcess','formatDomainObject')
			return newMap
		}catch(Exception e){
			throw new Exception("[ApiCommProcess :: formatDomainObject] : Exception - full stack trace follows:",e)
		}
	}

	/**
	 * Given a LinkedHashMap detected as a Map, processes in a standardized format and returns a LinkedHashMap;
	 * Used by convertModel and called by the PostHandler
	 * @see #convertModel(Map)
	 * @param LinkedHashMap map
	 * @return LinkedHashMap commonly formatted linkedhashmap
	 */
	LinkedHashMap formatMap(HashMap map){
		traceService.startTrace('ProfilerCommProcess','formatMap')
		LinkedHashMap newMap = [:]
		if(map) {
			GParsPool.withPool(this.cores, {
				map.eachParallel() { key, val ->
					if (val) {
						if (java.lang.Class.isInstance(val.class)) {
							newMap[key] = ((val in java.util.ArrayList || val in java.util.List) || val in java.util.Map) ? val : val.toString()
						} else if (DomainClassArtefactHandler?.isDomainClass(val.getClass())) {
							newMap[key] = formatDomainObject(val)
						} else {
							newMap[key] = ((val in java.util.ArrayList || val in java.util.List) || (val in java.util.Map || val in java.util.Map || val in java.util.LinkedHashMap)) ? val : val.toString()
						}
					}
				}
			})
		}
		traceService.endTrace('ProfilerCommProcess','formatMap')
		return newMap
	}

	/**
	 * Given a List detected as a List, processes in a standardized format and returns a HashMap;
	 * Used by convertModel and called by the PostHandler
	 * @see #convertModel(Map)
	 * @param ArrayList list
	 * @return commonly formatted linkedhashmap
	 */
	LinkedHashMap formatList(List list){
		traceService.startTrace('ProfilerCommProcess','formatList')
		LinkedHashMap newMap = [:]
		if(list) {
			list.eachWithIndex() { val, key ->
				if (val) {
					if (val instanceof java.util.ArrayList || val instanceof java.util.List) {
						newMap[key] = ((val in java.util.ArrayList || val in java.util.List) || val in java.util.Map) ? val : val.toString()
					} else {
						if (DomainClassArtefactHandler?.isDomainClass(val.getClass())) {
							newMap[key] = formatDomainObject(val)
						} else {
							newMap[key] = ((val in java.util.ArrayList || val in java.util.List) || val in java.util.Map) ? list[key] : val.toString()
						}
					}
				}
			}
		}
		traceService.endTrace('ProfilerCommProcess','formatList')
		return newMap
	}

	/**
	 * Given api version and a controllerName/className, tests whether cache exists; returns boolean
	 * @see ApiFrameworkInterceptor#before()
	 * @see BatchInterceptor#before()
	 * @param Integer version
	 * @param String className
	 * @return
	 */
	boolean isCachedResult(Integer version, String className){
		traceService.startTrace('ProfilerCommProcess','isCachedResult')
		Class clazz = grailsApplication.domainClasses.find { it.clazz.simpleName == className }.clazz
		HibernateCriteriaBuilder c = clazz.createCriteria()
		long currentVersion = c.get {
			projections {
				property('version')
			}
			maxResults(1)
			order('version', 'desc')
		}
		traceService.endTrace('ProfilerCommProcess','isCachedResult')
		return (currentVersion > version)?false:true
	}



	/**
	 * Given the contentLength of the response, tests to see if rateLimit or dataLimit have been reached or supassed; returns boolean
	 * @see ApiFrameworkInterceptor#before()
	 * @see ApiFrameworkInterceptor#after()
	 * @param int contentLength
	 * @return
	 */
	boolean checkLimit(int contentLength,List auth){
		traceService.startTrace('ProfilerCommProcess','checkLimit')

		HashMap throttle = Holders.grailsApplication.config.apitoolkit.throttle as HashMap
		HashMap rateLimit = throttle.rateLimit as HashMap
		HashMap dataLimit = throttle.dataLimit as HashMap
		Integer expire = throttle.expires as Integer
		ArrayList keys = rateLimit.keySet() as ArrayList

		if(keys.contains(rateLimitKey)){
			String userId = springSecurityService.loggedIn?springSecurityService.principal.id : null
			def lcache = throttleCacheService.getThrottleCache(userId)

			if(lcache['timestamp']==null) {
				int currentTime= System.currentTimeMillis() / 1000
				int expires = currentTime+expire
				LinkedHashMap cache = ['timestamp': currentTime, 'currentRate': 1, 'currentData':contentLength,'locked': false, 'expires': expires]
				response.setHeader("Content-Length", "${contentLength}")
				throttleCacheService.setThrottleCache(userId, cache)
				traceService.endTrace('ProfilerCommProcess','checkLimit')
				return true
			}else{
				if(lcache['locked']==false) {
					int userLimit = 0
					int userDataLimit = 0
					auth.each(){
						userLimit=((rateLimit[it] as Integer)>userLimit)?rateLimit[it] as Integer:userLimit
						userDataLimit = ((dataLimit[it] as Integer)>userDataLimit)?dataLimit[it] as Integer:userDataLimit
					}

					if(lcache['currentRate']>=userLimit || lcache['currentData']>=userDataLimit){
						// TODO : check locked (and lock if not locked) and expires
						int now = System.currentTimeMillis() / 1000
						if(lcache['expires']<=now){
							currentTime= System.currentTimeMillis() / 1000
							expires = currentTime+expires
							cache = ['timestamp': currentTime, 'currentRate': 1, 'currentData':contentLength,'locked': false, 'expires': expires]
							response.setHeader('Content-Length', "${contentLength}")
							throttleCacheService.setThrottleCache(userId, cache)
							traceService.endTrace('ProfilerCommProcess','checkLimit')
							return true
						}else{
							lcache['locked'] = true
							throttleCacheService.setThrottleCache(userId, lcache)
						}

						traceService.endTrace('ProfilerCommProcess','checkLimit')
						errorResponse([404, 'Rate Limit exceeded. Please wait'])
						return false
					}else{
						lcache['currentRate']++
						lcache['currentData']+=contentLength
						response.setHeader('Content-Length', "${contentLength}")
						throttleCacheService.setThrottleCache(userId, lcache)
						traceService.endTrace('ProfilerCommProcess','checkLimit')
						return true
					}

					traceService.endTrace('ProfilerCommProcess','checkLimit')
					errorResponse([404, 'Rate Limit exceeded. Please wait'])
					return false
				}else{
					errorResponse([404, 'Rate Limit exceeded. Please wait'])
					return false
				}
			}
		}

		return true
	}


	/**
	 * Given request and List of roles(authorities), tests whether user is logged in and user authorities match authorities sent; returns boolean
	 * Used mainly to check endpoint authorities against user authorities
	 * @param request
	 * @param roles
	 * @return
	 */
	boolean checkAuth(List roles){
		try {
			traceService.startTrace('ProfilerCommProcess','checkAuth')
			boolean hasAuth = false
			if (springSecurityService.loggedIn) {
				def principal = springSecurityService.principal
				ArrayList userRoles = principal.authorities*.authority as ArrayList
				roles.each {
					if (userRoles.contains(it) || it=='permitAll') {
						hasAuth = true
					}
				}
			}else{
				//println("NOT LOGGED IN!!!")
			}
			if(hasAuth==false){
				traceService.endTrace('ProfilerCommProcess','checkAuth')
				errorResponse([400, 'Unauthorized Access attempted'])
				return false
			}else{
				traceService.endTrace('ProfilerCommProcess','checkAuth')
				return hasAuth
			}
		}catch(Exception e) {
			String msg = this.messageSource.getMessage("error.profilerCommProcess.checkAuth", [docUrl,e] as Object[], 'Default Message', request.locale)
			throw new Exception(msg)
		}
	}


	/**
	 * Returns concatenated IDS as a HASH used as ID for the API cache
	 * @see ApiFrameworkInterceptor#before()
	 * @see BatchInterceptor#before()
	 * @see ChainInterceptor#before()
	 * @param GrailsParameterMap Map of params created from the request data
	 * @param LinkedHashMap List of ids required when making request to endpoint
	 * @return a hash from all id's needed when making request to endpoint
	 */
	String createCacheHash(GrailsParameterMap params, ArrayList receivesList, List authority){
		traceService.startTrace('ProfilerCommProcess','createCacheHash')

		StringBuilder hashString = new StringBuilder('')

		receivesList.each(){ it ->
			hashString.append(params[it])
			hashString.append("/")
		}
		String hash = hashWithGuava(hashString.toString())
		traceService.endTrace('ProfilerCommProcess','createCacheHash')
		return hash
	}

	protected static String hashWithGuava(final String originalString) {
		final String sha256hex = Hashing.sha256().hashString(originalString, StandardCharsets.UTF_8).toString()
		return sha256hex
	}

	protected String getContent(Object result, String contentType){
		traceService.startTrace('ProfilerCommProcess','getContent')
		String content
		if(result=='{}') {
			return null
		}
		switch(contentType){
			case 'text/xml':
			case 'application/xml':
				content = result as XML
				break
			case 'text/json':
			case 'application/json':
			default:
				content = result as JSON
				break
		}
		traceService.endTrace('ProfilerCommProcess','getContent')
		return content
	}

	/**
	 * Allows loading of response data and sets stat data at same time.
	 * This then is 'flushed' in main classes; saves on having to call service in every method
	 * @param List error ; error code, error message
	 */
	protected void errorResponse(List error){
		traceService.startTrace('ProfilerCommProcess','errorResponse')
		Integer status = error[0]
		String msg = error[1]

		//statsService.setStatsCache(springSecurityService.principal['id'], response.status, request.requestURI)

		//response.status = status
		//response.setHeader('ERROR', msg)
		//response.writer.flush()


		response.setContentType("application/json")
		response.setStatus(status)
		response.getWriter().write(msg)
		response.writer.flush()

		traceService.endTrace('ProfilerCommProcess','errorResponse')
	}
}
