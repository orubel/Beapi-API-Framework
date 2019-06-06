/*
 * Copyright 2013-2019 Beapi.io
 * API Chaining(R) 2019 USPTO
 *
 * Licensed under the MPL-2.0 License;
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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


/**
 *
 * This abstract provides Common API Methods used by APICommLayer and those that extend it
 * This is simply organizational in keeping repetitively called methods from communication processes
 * for readability
 * @author Owen Rubel
 *
 * @see ApiCommLayer
 *
 */

abstract class ApiCommProcess{

    @Resource
    GrailsApplication grailsApplication

    @Autowired
    GrailsCacheManager grailsCacheManager

    @Autowired
    ThrottleCacheService throttleCacheService

    List formats = ['text/json','application/json','text/xml','application/xml']
    List optionalParams = ['method','format','contentType','encoding','action','controller','v','apiCombine', 'apiObject','entryPoint','uri','apiObjectVersion']

    // Used for parallelization
    int cores = Holders.grailsApplication.config.apitoolkit['procCores'] as Integer

    boolean batchEnabled = Holders.grailsApplication.config.apitoolkit.batching.enabled
    boolean chainEnabled = Holders.grailsApplication.config.apitoolkit.chaining.enabled


    /**
     * Given the request params, resets parameters for a batch based upon each iteration
     * @see BatchInterceptor#before()
     * @param GrailsParameterMap Map of params created from the request data
     */
    void setBatchParams(GrailsParameterMap params){
        try{
            if (batchEnabled) {
                Object batchVars = request.getAttribute(request.format.toUpperCase())
                if(!request.getAttribute('batchLength') && request.JSON?.batch){
                    request.setAttribute('batchLength',request.JSON?.batch.size())
                }
                if(batchVars['batch']) {
                    batchVars['batch'][request.getAttribute('batchInc').toInteger()].each() { k, v ->
                        params."${k}" = v
                    }
                }
            }
        }catch(Exception e) {
            throw new Exception("[ApiCommProcess :: setBatchParams] : Exception - full stack trace follows:",e)
        }
    }

    String getModelResponseFormat(){}

    /**
     * Given the request params, resets parameters for an api chain based upon for each iteration
     * @see ChainInterceptor#before()
     * @param GrailsParameterMap Map of params created from the request data
     */
    void setChainParams(GrailsParameterMap params){
        if (chainEnabled) {
            if(!params.apiChain){ params.apiChain = [:] }
            LinkedHashMap chainVars = request.JSON
            if(!request.getAttribute('chainLength')){
                request.setAttribute('chainLength',chainVars['chain'].size())
            }
            if(chainVars['chain']) {
                chainVars['chain'].each() { k, v ->
                    params.apiChain[k] = v
                }
            }
        }
    }

    /**
     * Returns authorities associated with loggedIn user; creates default authority which will be checked
     * against endpoint 'roles' if no 'loggedIn' user is found
     * @see #checkURIDefinitions(GrailsParameterMap,LinkedHashMap)
     * @see #checkLimit(int)
     * @see ApiCommLayer#handleApiResponse(LinkedHashMap, List, RequestMethod, String, HttpServletResponse, HashMap, GrailsParameterMap)
     * @return String Role of current principal (logged in user)
     */
    String getUserRole(List roles) {
        String authority = 'permitAll'
        springSecurityService.principal.authorities*.authority.each{
            if(roles.contains(it)){
                authority = it
            }
        }
        return authority
    }


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
            Date ddate = new SimpleDateFormat("MM/dd/yyyy").parse(deprecationDate)
            Date deprecated = new Date(ddate.time)
            Date today = new Date()
            if(deprecated < today ) {
                return true
            }
            return false
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: checkDeprecationDate] : Exception - full stack trace follows:",e)
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
    boolean checkRequestMethod(RequestMethod mthd,String method, boolean restAlt){
        if(!restAlt) {
            if(mthd.getKey() == method){
                return true
            }else{
                errorResponse([400,'Expected request method for endpoint does not match sent method'])
                return false
            }
        }
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
    boolean checkURIDefinitions(GrailsParameterMap params,LinkedHashMap requestDefinitions, String authority){
        ArrayList reservedNames = ['batchLength','batchInc','chainInc','apiChain','apiResult','combine','_','batch','max','offset','apiObjectVersion']
        ArrayList paramsList
        ArrayList requestList
        try {
            ArrayList temp = []
            if (requestDefinitions["${authority}"]) {
                temp = requestDefinitions["${authority}"] as ArrayList
            } else if (requestDefinitions['permitAll'][0] != null) {
                temp = requestDefinitions['permitAll'] as ArrayList
            }

            requestList = (temp != null) ? temp.collect() { it.name } : []

            if (requestList.contains('*')) {
                return true
            } else {
                LinkedHashMap methodParams = getMethodParams(params)
                paramsList = methodParams.keySet() as ArrayList
                // remove reservedNames from List

                reservedNames.each() { paramsList.remove(it) }

                if (paramsList.size() == requestList.intersect(paramsList).size()) {
                    return true
                }
            }

            errorResponse([400,"Expected request variables for endpoint [${requestList}] do not match sent variables [${paramsList}]"])
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

        return content
    }

    LinkedHashMap parseBatchResponseMethod(RequestMethod mthd, String format, LinkedHashMap result){
        LinkedHashMap content
        switch(mthd.getKey()) {
            case 'GET':
                // placeholder
            case 'PUT':
                // placeholder
            case 'POST':
                // placeholder
            case 'DELETE':
                content = result
                break
        }

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
        if(model[0].getClass().getName()=='java.util.LinkedHashMap') {
            try {
                model.each() { key, val ->
                    model[key] = parseURIDefinitions(val, responseList)
                }
                return model
            }catch(Exception e){
                throw new Exception('[ApiCommProcess :: parseURIDefinitions] : Exception - full stack trace follows:', e)
            }
        }else{
            try {
                ArrayList paramsList = (model.size()==0)?[:]:model.keySet() as ArrayList
                paramsList?.removeAll(optionalParams)
                if (!responseList.containsAll(paramsList)) {
                    paramsList.removeAll(responseList)
                    paramsList.each() { it2 ->
                        model.remove(it2.toString())
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
                throw new Exception('[ApiCommProcess :: parseURIDefinitions] : Exception - full stack trace follows:', e)
            }
        }
    }


    // used in ApiCommLayer
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
        if(RequestMethod.isRestAlt(mthd.getKey())){
            return true
        }else{
            if(method == mthd.getKey()){
                return true
            }else{
                return false
            }
        }
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
        try{
            LinkedHashMap paramsRequest = [:]
            paramsRequest = params.findAll { it2 -> !optionalParams.contains(it2.key) }
            return paramsRequest
        }catch(Exception e){
            throw new Exception('[ApiCommProcess :: getMethodParams] : Exception - full stack trace follows:',e)
        }
        return [:]
    }

    /**
     * Given an ArrayList of authorities associated with endpoint, determines if User authorities match; returns boolean
     * @see #getApiDoc(GrailsParameterMap)
     * @param ArrayList list
     * @return Boolean returns false if users roles are not contained in list of provided roles
     */
    Boolean hasRoles(ArrayList list) {
        if(springSecurityService.principal.authorities*.authority.any { list.contains(it) }){
            return true
        }
        return false
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
            LinkedHashMap newMap = [:]
            String k = map.entrySet().toList().first().key

            if(map && (!map?.response && !map?.metaClass && !map?.params)){
                if (DomainClassArtefactHandler?.isDomainClass(map[k].getClass()) && map[k]!=null) {
                    newMap = formatDomainObject(map[k])
                    return newMap
                } else if(['class java.util.LinkedList', 'class java.util.ArrayList'].contains(map[k].getClass().toString())) {
                    newMap = formatList(map[k])
                    return newMap
                } else if(['class java.util.Map', 'class java.util.LinkedHashMap','class java.util.HashMap'].contains(map[k].getClass().toString())) {
                    newMap = formatMap(map[k])
                    return newMap
                }
            }
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
            LinkedHashMap newMap = [:]

            newMap.put('id', data?.id)
            newMap.put('version', data?.version)

            //DefaultGrailsDomainClass d = new DefaultGrailsDomainClass(data.class)

            DefaultGrailsDomainClass d = grailsApplication?.getArtefact(DomainClassArtefactHandler.TYPE, data.class.getName())

            if (d!=null) {
                // println("PP:"+d.persistentProperties)
                //GParsPool.withPool(this.cores, {
                    d?.persistentProperties?.each() { it2 ->
                        if (it2?.name) {
                            if (DomainClassArtefactHandler.isDomainClass(data[it2.name].getClass())) {
                                newMap["${it2.name}Id"] = data[it2.name].id
                            } else {
                                newMap[it2.name] = data[it2.name]
                            }
                        }
                    }
                //})
            }
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
        LinkedHashMap newMap = [:]
        if(list) {
            GParsPool.withPool(this.cores, {
                list.eachWithIndexParallel() { val, key ->
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
            })
        }
        return newMap
    }

    // TODO : add to ChainInterceptor
    /**
     * Given api version and a controllerName/className, tests whether cache exists; returns boolean
     * @see ApiFrameworkInterceptor#before()
     * @see BatchInterceptor#before()
     * @param Integer version
     * @param String className
     * @return
     */
    boolean isCachedResult(Integer version, String className){
        Class clazz = grailsApplication.domainClasses.find { it.clazz.simpleName == className }.clazz
        HibernateCriteriaBuilder c = clazz.createCriteria()
        long currentVersion = c.get {
            projections {
                property('version')
            }
            maxResults(1)
            order('version', 'desc')
        }
        return (currentVersion > version)?false:true
    }

    // interceptor::after (response)
    /**
     * Given request, test whether current request sent is an api chain; returns boolean
     * @see ChainInterceptor#before()
     * @param String contentType
     * @return
     */
    boolean isChain(String contentType){
        try{
            switch(contentType){
                case 'text/xml':
                case 'application/xml':
                    if(request.XML?.chain){
                        return true
                    }
                    break
                case 'text/json':
                case 'application/json':
                default:
                    if(request.JSON?.chain){
                        return true
                    }
                    break
            }
            return false
        }catch(Exception e){
            throw new Exception("[ApiResponseService :: isChain] : Exception - full stack trace follows:",e)
        }
    }

    // interceptor::before
    /**
     * Returns config variable representing number of seconds for throttle
     * @see ApiFrameworkInterceptor#before()
     * @see BatchInterceptor#before()
     * @see ChainInterceptor#before()
     * @return
     */
    String getThrottleExpiration(){
        return Holders.grailsApplication.config.apitoolkit.throttle.expires as String
    }

    // interceptor::before
    /**
     * Given the contentLength of the response, tests to see if rateLimit or dataLimit have been reached or supassed; returns boolean
     * @see ApiFrameworkInterceptor#before()
     * @see ApiFrameworkInterceptor#after()
     * @param int contentLength
     * @return
     */
    boolean checkLimit(int contentLength,String auth){
        HashMap throttle = Holders.grailsApplication.config.apitoolkit.throttle as HashMap
        HashMap rateLimit = throttle.rateLimit as HashMap
        HashMap dataLimit = throttle.dataLimit as HashMap
        Integer expire = throttle.expires as Integer
        ArrayList roles = rateLimit.keySet() as ArrayList

        if(roles.contains(auth)){
            String userId = springSecurityService.loggedIn?springSecurityService.principal.id : null
            def lcache = throttleCacheService.getThrottleCache(userId)

            if(lcache['timestamp']==null) {
                int currentTime= System.currentTimeMillis() / 1000
                int expires = currentTime+expire
                LinkedHashMap cache = ['timestamp': currentTime, 'currentRate': 1, 'currentData':contentLength,'locked': false, 'expires': expires]
                response.setHeader("Content-Length", "${contentLength}")
                throttleCacheService.setThrottleCache(userId, cache)
                return true
            }else{
                if(lcache['locked']==false) {

                    int userLimit = rateLimit["${auth}"] as Integer
                    int userDataLimit = dataLimit["${auth}"] as Integer
                    if(lcache['currentRate']>=userLimit || lcache['currentData']>=userDataLimit){
                        // TODO : check locked (and lock if not locked) and expires
                        int now = System.currentTimeMillis() / 1000
                        if(lcache['expires']<=now){
                            currentTime= System.currentTimeMillis() / 1000
                            expires = currentTime+expires
                            cache = ['timestamp': currentTime, 'currentRate': 1, 'currentData':contentLength,'locked': false, 'expires': expires]
                            response.setHeader('Content-Length', "${contentLength}")
                            throttleCacheService.setThrottleCache(userId, cache)
                            return true
                        }else{
                            lcache['locked'] = true
                            throttleCacheService.setThrottleCache(userId, lcache)

                            return false
                        }
                        return false
                    }else{
                        lcache['currentRate']++
                        lcache['currentData']+=contentLength
                        response.setHeader('Content-Length', "${contentLength}")
                        throttleCacheService.setThrottleCache(userId, lcache)
                        return true
                    }

                    return false
                }else{
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
                errorResponse([400, 'Unauthorized Access attempted'])
                return false
            }else{
                return hasAuth
            }
        }catch(Exception e) {
            throw new Exception("[ApiCommProcess :: checkAuth] : Exception - full stack trace follows:",e)
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
    String createCacheHash(GrailsParameterMap params, LinkedHashMap receives, String authority){
        //boolean roles = Holders.grailsApplication.config.apitoolkit.networkRoles."${networkGroup}"
        StringBuilder hashString = new StringBuilder('')
        ArrayList temp = []
        if (receives["${authority}"]) {
            temp = receives["${authority}"] as ArrayList
        } else if (receives['permitAll'][0] != null) {
            temp = receives['permitAll'] as ArrayList
        }

        ArrayList receivesList = (temp != null)?temp.collect(){ it.name }:[]

        receivesList.each(){ it ->
            hashString.append(params[it])
            hashString.append("/")
        }
        return hashWithGuava(hashString.toString())
    }

    protected static String hashWithGuava(final String originalString) {
        final String sha256hex = Hashing.sha256().hashString(originalString, StandardCharsets.UTF_8).toString()
        return sha256hex
    }

    protected String getContent(Object result, String contentType){
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
        return content
    }

    /**
     * Allows loading of response data and sets stat data at same time.
     * This then is 'flushed' in main classes; saves on having to call service in every method
     * @param List error ; error code, error message
     */
    protected void errorResponse(List error){
        Integer status = error[0]
        String msg = error[1]

        //statsService.setStatsCache(springSecurityService.principal['id'], response.status, request.requestURI)

        response.status = status
        response.setHeader('ERROR', msg)
        //response.writer.flush()
    }


}
