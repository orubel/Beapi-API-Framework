package net.nosegrind.apiframework


import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import org.springframework.web.context.request.ServletRequestAttributes
import javax.servlet.http.HttpSession

import org.springframework.security.core.context.SecurityContextHolder as SCH
import java.text.SimpleDateFormat

import net.nosegrind.apiframework.RequestMethod
//import groovyx.gpars.*
import static groovyx.gpars.GParsPool.withPool

import grails.converters.JSON
import grails.converters.XML
import grails.web.servlet.mvc.GrailsParameterMap

import java.util.LinkedList

import javax.servlet.forward.*
import org.grails.groovy.grails.commons.*
import grails.core.GrailsApplication
import grails.util.Holders
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.grails.core.artefact.DomainClassArtefactHandler

import org.springframework.beans.factory.annotation.Autowired

// import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler


import net.nosegrind.apiframework.ApiCacheService
import net.nosegrind.apiframework.ThrottleCacheService
//import grails.plugin.cache.GrailsCacheManager
import org.grails.plugin.cache.GrailsCacheManager

// extended by ApiCommLayer

abstract class ApiCommProcess{

    @Resource
    GrailsApplication grailsApplication

    @Autowired
    GrailsCacheManager grailsCacheManager

    @Autowired
    ThrottleCacheService throttleCacheService

    @Autowired
    ApiCacheService apiCacheService
    List formats = ['text/json','application/json','text/xml','application/xml']
    List optionalParams = ['method','format','contentType','encoding','action','controller','v','apiCombine', 'apiObject','entryPoint','uri']

    boolean batchEnabled = Holders.grailsApplication.config.apitoolkit.batching.enabled
    boolean chainEnabled = Holders.grailsApplication.config.apitoolkit.chaining.enabled

    // set params for this 'loop'; these will NOT forward
    void setBatchParams(GrailsParameterMap params){
        if (batchEnabled) {
            def batchVars = request.getAttribute(request.format.toUpperCase())
            if(!request.getAttribute('batchLength')){ request.setAttribute('batchLength',batchVars['batch'].size()) }
            batchVars['batch'][request.getAttribute('batchInc').toInteger()].each() { k,v ->
                params."${k}" = v
            }
        }
    }

    void setChainParams(GrailsParameterMap params){
        if (chainEnabled) {
            if(!params.apiChain){ params.apiChain = [:] }
            def chainVars = request.JSON
            if(!request.getAttribute('chainLength')){ request.setAttribute('chainLength',chainVars['chain'].size()) }
            chainVars['chain'].each() { k,v ->
                params.apiChain[k] = v
            }
        }
    }

    String getUserRole() {
        String authority = 'permitAll'
        if (springSecurityService.loggedIn){
            authority = springSecurityService.principal.authorities*.authority[0]
        }
        return authority
    }

    String getUserId() {
        if (springSecurityService.loggedIn){
            return springSecurityService.principal.id
        }
        return null
    }

    boolean checkAuth(HttpServletRequest request, List roles){
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
            return hasAuth
        }catch(Exception e) {
            throw new Exception("[ApiCommProcess :: checkAuth] : Exception - full stack trace follows:",e)
        }
    }

    boolean checkDeprecationDate(String deprecationDate){
        try{
            def ddate = new SimpleDateFormat("MM/dd/yyyy").parse(deprecationDate)
            def deprecated = new Date(ddate.time)
            def today = new Date()
            if(deprecated < today ) {
                return true
            }
            return false
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: checkDeprecationDate] : Exception - full stack trace follows:",e)
        }
    }

    boolean checkRequestMethod(RequestMethod mthd,String method, boolean restAlt){
        if(!restAlt) {
            return (mthd.getKey() == method) ? true : false
        }
        return true
    }

    // TODO: put in OPTIONAL toggle in application.yml to allow for this check
    boolean checkURIDefinitions(GrailsParameterMap params,LinkedHashMap requestDefinitions){
        ArrayList reservedNames = ['batchLength','batchInc','chainInc','apiChain','_','max','offset']
        try{
            String authority = getUserRole() as String
            ArrayList temp = []
            if(requestDefinitions["${authority}"]){
                temp = requestDefinitions["${authority}"] as ArrayList
            }else if(requestDefinitions['permitAll'][0]!=null){
                temp = requestDefinitions['permitAll'] as ArrayList
            }

            ArrayList requestList = (temp!=null)?temp.collect(){ it.name }:[]

            LinkedHashMap methodParams = getMethodParams(params)
            ArrayList paramsList = methodParams.keySet() as ArrayList

            // remove reservedNames from List
            reservedNames.each(){ paramsList.remove(it) }

            if (paramsList.size() == requestList.intersect(paramsList).size()) {
                return true
            }

            return false
        }catch(Exception e) {
           throw new Exception("[ApiCommProcess :: checkURIDefinitions] : Exception - full stack trace follows:",e)
        }
        return false
    }

    String parseResponseMethod(RequestMethod mthd, String format, GrailsParameterMap params, LinkedHashMap result){
        String content
        switch(mthd.getKey()) {
            case 'PURGE':
                // cleans cache; disabled for now
                break;
            case 'TRACE':
                break;
            case 'HEAD':
                break;
            case 'OPTIONS':
                String doc = getApiDoc(params)
                content = doc
                break;
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
                break;
        }

        return content
    }

    String parseRequestMethod(RequestMethod mthd, GrailsParameterMap params){
        String content
        switch(mthd.getKey()) {
            case 'PURGE':
                // cleans cache; disabled for now
                break;
            case 'TRACE':
                // placeholder
                break;
            case 'HEAD':
                // placeholder
                break;
            case 'OPTIONS':
                content = getApiDoc(params)
                break;
        }

        return content
    }

    LinkedHashMap parseURIDefinitions(LinkedHashMap model,ArrayList responseList){
        if(model[0].getClass().getName()=='java.util.LinkedHashMap') {
            model.each() { key, val ->
                model[key] = parseURIDefinitions(val, responseList)
            }
            return model
        }else{
            try {
                String msg = 'Error. Invalid variables being returned. Please see your administrator'

                //List paramsList
                //Integer msize = model.size()
                //List paramsList = (model.size()==0)?[:]:model.keySet() as List
                ArrayList paramsList = (model.size()==0)?[:]:model.keySet() as ArrayList
                paramsList?.removeAll(optionalParams)
                if (!responseList.containsAll(paramsList)) {
                    paramsList.removeAll(responseList)
                    paramsList.each() { it2 ->
                        model.remove("${it2}".toString())
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
                throw new Exception("[ApiCommProcess :: parseURIDefinitions] : Exception - full stack trace follows:", e)
            }
        }
    }


    // used in ApiCommLayer
    boolean isRequestMatch(String protocol,RequestMethod mthd){
        if(RequestMethod.isRestAlt(mthd.getKey())){
            return true
        }else{
            if(protocol == mthd.getKey()){
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

    // used locally
    LinkedHashMap getMethodParams(GrailsParameterMap params){
        try{
            LinkedHashMap paramsRequest = [:]
            paramsRequest = params.findAll { it2 -> !optionalParams.contains(it2.key) }
            return paramsRequest
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: getMethodParams] : Exception - full stack trace follows:",e)
        }
        return [:]
    }

    // used locally
    Boolean hasRoles(ArrayList set) {
        if(springSecurityService.principal.authorities*.authority.any { set.contains(it) }){
            return true
        }
        return false
    }

    LinkedHashMap getApiCache(String controllername){
        try{
            def temp = grailsCacheManager?.getCache('ApiCache')

            def cache = temp?.get(controllername)
            if(cache?.get()){
                return cache.get() as LinkedHashMap
            }else{
                return [:]
            }
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: getApiCache] : Exception - full stack trace follows:",e)
        }
    }

    String getApiDoc(GrailsParameterMap params){
        // TODO: Need to compare multiple authorities
        // TODO: check for ['doc'][role] in cache; if none, continue

        LinkedHashMap newDoc = [:]
        List paramDescProps = ['paramType','idReferences','name','description']
        try{
            def controller = grailsApplication.getArtefactByLogicalPropertyName('Controller', params.controller)
            if(controller){
                def cache = (params.controller)?getApiCache(params.controller):null
                //LinkedHashMap cache = session['cache'] as LinkedHashMap
                if(cache){
                    if(cache[params.apiObject][params.action]){

                        def doc = cache[params.apiObject][params.action].doc
                        def path = doc?.path
                        def method = doc?.method
                        def description = doc?.description


                        //def authority = springSecurityService.principal.authorities*.authority[0]
                        newDoc[params.action] = ['path':path,'method':method,'description':description]
                        if(doc.receives){
                            newDoc[params.action].receives = []

                            doc.receives.each{ it ->
                                if(hasRoles([it.key]) || it.key=='permitAll'){
                                    it.value.each(){ it2 ->
                                        LinkedHashMap values = [:]
                                        it2.each(){ it3 ->
                                            if(paramDescProps.contains(it3.key)){
                                                values[it3.key] = it3.value
                                            }
                                        }
                                        if(values) {
                                            newDoc[params.action].receives.add(values)
                                        }
                                    }

                                }
                            }
                        }

                        if(doc.returns){
                            newDoc[params.action].returns = []
                            List jsonReturns = []
                            doc.returns.each(){ v ->
                                if(hasRoles([v.key]) || v.key=='permitAll'){
                                    jsonReturns.add(["${v.key}":v.value])
                                    v.value.each(){ v2 ->
                                        LinkedHashMap values3 = [:]
                                        v2.each(){ v3 ->
                                            if(paramDescProps.contains(v3.key)){
                                                values3[v3.key] = v3.value
                                            }
                                        }
                                        if(values3) {
                                            newDoc[params.action].returns.add(values3)
                                        }
                                    }
                                    //newDoc[params.action].returns[v.key] = v.value
                                }
                            }

                            //newDoc[params.action].json = processJson(newDoc[params.action].returns)

                            newDoc[params.action].json = processJson(jsonReturns[0] as LinkedHashMap)
                        }

                        if(doc.errorcodes){
                            doc.errorcodes.each{ it ->
                                newDoc[params.action].errorcodes.add(it)
                            }
                        }

                        // store ['doc'][role] in cache

                        return newDoc as JSON
                    }
                }
            }
            return [:]
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: getApiDoc] : Exception - full stack trace follows:",e)
        }
    }

    // Used by getApiDoc
    private String processJson(LinkedHashMap returns){
        // TODO: Need to compare multiple authorities
        try{
            LinkedHashMap json = [:]
            returns.each{ p ->
                p.value.each{ it ->
                    if(it) {
                        ParamsDescriptor paramDesc = it

                        LinkedHashMap j = [:]
                        if (paramDesc?.values) {
                            j["$paramDesc.name"] = []
                        } else {
                            String dataName = (['PKEY', 'FKEY', 'INDEX'].contains(paramDesc?.paramType?.toString())) ? 'ID' : paramDesc.paramType
                            j = (paramDesc?.mockData?.trim()) ? ["$paramDesc.name": "$paramDesc.mockData"] : ["$paramDesc.name": "$dataName"]
                        }
                        withPool(20) { pool ->
                            j.eachParallel { key, val ->
                                if (val instanceof List) {
                                    def child = [:]
                                    withExistingPool(pool, {
                                        val.eachParallel { it2 ->
                                            withExistingPool(pool, {
                                                it2.eachParallel { key2, val2 ->
                                                    child[key2] = val2
                                                }
                                            })
                                        }
                                    })
                                    json[key] = child
                                } else {
                                    json[key] = val
                                }
                            }
                        }
                    }
                }
            }

            String jsonReturn
            if(json){
                jsonReturn = json as JSON
            }
            return jsonReturn
        }catch(Exception e){
            throw new Exception("[ApiCommProcess :: processJson] : Exception - full stack trace follows:",e)
        }
    }

    // interceptor::after (response)
    LinkedHashMap convertModel(Map map){
        //try{
            LinkedHashMap newMap = [:]
            String k = map.entrySet().toList().first().key

            if(map && (!map?.response && !map?.metaClass && !map?.params)){
                if (DomainClassArtefactHandler?.isDomainClass(map[k].getClass())) {
                    newMap = formatDomainObject(map[k])
                    return newMap
                } else if(['class java.util.LinkedList', 'class java.util.ArrayList'].contains(map[k].getClass().toString())) {
                    newMap = formatList(map[k])
                    return newMap
                } else if(['class java.util.Map', 'class java.util.LinkedHashMap'].contains(map[k].getClass().toString())) {
                    newMap = formatMap(map[k])
                    return newMap
                }
            }
            return newMap
        //}catch(Exception e){
        //    throw new Exception("[ApiCommProcess :: convertModel] : Exception - full stack trace follows:",e)
        //}
    }

    // used by convertModel > interceptor::after (response)
    LinkedHashMap formatDomainObject(Object data){
        try{
            LinkedHashMap newMap = [:]

            newMap.put('id',data?.id)
            newMap.put('version',data?.version)

            //DefaultGrailsDomainClass d = new DefaultGrailsDomainClass(data.class)

            def d = grailsApplication?.getArtefact(DomainClassArtefactHandler.TYPE, data.class.getName())

            d.persistentProperties.each() { it ->
                if (it?.name) {
                    if (DomainClassArtefactHandler.isDomainClass(data[it.name].getClass())) {
                        newMap["${it.name}Id"] = data[it.name].id
                    } else {
                        newMap[it.name] = data[it.name]
                    }
                }
            }
            return newMap
        }catch(Exception e){
           throw new Exception("[ApiCommProcess :: formatDomainObject] : Exception - full stack trace follows:",e)
        }
    }

    // used by convertModel > interceptor::after (response)
    LinkedHashMap formatMap(LinkedHashMap map){
        LinkedHashMap newMap = [:]
        map.each(){ key,val ->
            if(val){
                if (java.lang.Class.isInstance(val.class)) {
                    newMap[key] = ((val in java.util.ArrayList || val in java.util.List) || val in java.util.Map)?val:val.toString()
                }else if(DomainClassArtefactHandler?.isDomainClass(val.getClass()) || DomainClassArtefactHandler?.isArtefactClass(val.class)){
                    newMap[key]=formatDomainObject(val)
                }else{
                    newMap[key] = ((val in java.util.ArrayList || val in java.util.List) || val in java.util.Map)?val:val.toString()
                }
            }
        }
        return newMap
    }

    // used by convertModel > interceptor::after (response)
    LinkedHashMap formatList(List list){
        LinkedHashMap newMap = [:]
        list.eachWithIndex(){ val, key ->
            if(val){
                if(val[0]) {
                    if (java.lang.Class.isInstance(val[0].class)) {
                        newMap[key] = ((val[0] in java.util.ArrayList || val[0] in java.util.List) || val[0] in java.util.Map)?val[0]:val[0].toString()
                    }else if(DomainClassArtefactHandler?.isDomainClass(val[0].getClass()) || DomainClassArtefactHandler?.isArtefactClass(val[0].getClass())){
                        newMap[key]=formatDomainObject(val[0])
                    }else{
                        newMap[key] = ((val[0] in java.util.ArrayList || val[0] in java.util.List) || val[0] in java.util.Map)?val[0]:val[0].toString()
                    }
                }else {
                    if (java.lang.Class.isInstance(val.class)) {
                        newMap[key] = ((val in java.util.ArrayList || val in java.util.List) || val in java.util.Map) ? list[key] : val.toString()
                    }else if (DomainClassArtefactHandler?.isDomainClass(val.getClass()) || DomainClassArtefactHandler?.isArtefactClass(val.class)) {
                        newMap[key] = formatDomainObject(val)
                    }else{
                        newMap[key] = ((val in java.util.ArrayList || val in java.util.List) || val in java.util.Map) ? list[key] : val.toString()
                    }
                }
            }
        }
        return newMap
    }

    // interceptor::after (response)
    boolean isCachedResult(Integer version, String className){
        Class clazz = grailsApplication.domainClasses.find { it.clazz.simpleName == className }.clazz

        def c = clazz.createCriteria()
        def currentVersion = c.get {
            projections {
                property('version')
            }
            maxResults(1)
            order("version", "desc")
        }

        return (currentVersion > version)?false:true
    }

    // interceptor::after (response)
    boolean isChain(HttpServletRequest request){
        String contentType = request.getContentType()
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
    String getThrottleExpiration(){
        return Holders.grailsApplication.config.apitoolkit.throttle.expires as String
    }

    // interceptor::before
    boolean checkLimit(int contentLength){
        LinkedHashMap throttle = Holders.grailsApplication.config.apitoolkit.throttle as LinkedHashMap
        LinkedHashMap rateLimit = throttle.rateLimit as LinkedHashMap
        LinkedHashMap dataLimit = throttle.dataLimit as LinkedHashMap
        ArrayList roles = rateLimit.keySet() as ArrayList
        String auth = getUserRole()

        if(roles.contains(auth)){
            String userId = getUserId()
            def lcache = throttleCacheService.getThrottleCache(userId)

            if(lcache['timestamp']==null) {
                Integer currentTime= System.currentTimeMillis() / 1000
                Integer expires = currentTime+((Integer)Holders.grailsApplication.config.apitoolkit.throttle.expires)
                LinkedHashMap cache = ['timestamp': currentTime, 'currentRate': 1, 'currentData':contentLength,'locked': false, 'expires': expires]
                response.setHeader("Content-Length", "${contentLength}")
                throttleCacheService.setThrottleCache(userId, cache)
                return true
            }else{
                if(lcache['locked']==false) {

                    Integer userLimit = rateLimit["${auth}"] as Integer
                    Integer userDataLimit = dataLimit["${auth}"] as Integer
                    if(lcache['currentRate']>=userLimit || lcache['currentData']>=userDataLimit){
                        // TODO : check locked (and lock if not locked) and expires
                        Integer now = System.currentTimeMillis() / 1000
                        if(lcache['expires']<=now){
                            currentTime= System.currentTimeMillis() / 1000
                            expires = currentTime+((Integer)Holders.grailsApplication.config.apitoolkit.throttle.expires)
                            cache = ['timestamp': currentTime, 'currentRate': 1, 'currentData':contentLength,'locked': false, 'expires': expires]
                            response.setHeader("Content-Length", "${contentLength}")
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
                        response.setHeader("Content-Length", "${contentLength}")
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

}
