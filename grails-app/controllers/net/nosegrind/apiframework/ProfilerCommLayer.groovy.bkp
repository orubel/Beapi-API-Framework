package net.nosegrind.apiframework

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import grails.web.servlet.mvc.GrailsParameterMap
import javax.servlet.forward.*
import org.grails.groovy.grails.commons.*
import javax.servlet.http.HttpServletResponse
import groovy.transform.CompileStatic

// extended by Intercepters
@CompileStatic
abstract class ProfilerCommLayer extends ProfilerCommProcess{


    @Resource
    TraceService traceService

    /***************************
     * REQUESTS
     ***************************/
    boolean handleApiRequest(ApiDescriptor cache, HttpServletRequest request, HttpServletResponse response, GrailsParameterMap params){
        traceService.startTrace('ProfilerCommLayer','handleApiRequest')
        try{

            // CHECK VERSION DEPRECATION DATE
            List deprecated = cache['deprecated'] as List

            if(deprecated?.get(0)){
                if(checkDeprecationDate(deprecated[0].toString())){
                    String depMsg = deprecated[1].toString()
                    response.status = 400
                    response.setHeader('ERROR',depMsg)
                    traceService.endTrace('ProfilerCommLayer','handleApiRequest')
                    return false
                }
            }

            def method = cache['method']?.toString().trim()

            // DOES api.methods.contains(request.method)
            if(!isRequestMatch(method,request.method.toString())){
                response.status = 400
                response.setHeader('ERROR',"Request method doesn't match expected method.")
                traceService.endTrace('ProfilerCommLayer','handleApiRequest')
                return false
            }
            traceService.endTrace('ProfilerCommLayer','handleApiRequest')
            return true
        }catch(Exception e){
            traceService.endTrace('ProfilerCommLayer','handleApiRequest')
            throw new Exception("[ProfileCommLayer : handleApiRequest] : Exception - full stack trace follows:",e)
        }
    }

    /***************************
    * RESPONSES
     ***************************/
    def handleApiResponse(LinkedHashMap requestDefinitions, List roles, HttpServletRequest request, HttpServletResponse response, LinkedHashMap model, GrailsParameterMap params){
        traceService.startTrace('ProfilerCommLayer','handleApiResponse')
        try{
            String authority = getUserRole() as String
            response.setHeader('Authorization', roles.join(', '))

            List<HashMap> temp = (requestDefinitions["${authority}"])?requestDefinitions["${authority}"] as List<HashMap>:requestDefinitions['permitAll'] as List<HashMap>
            List responseList = temp.collect(){ it.name }

            LinkedHashMap content = [:]
            if(params.controller!='apidoc') {
                LinkedHashMap result = parseURIDefinitions(model, responseList)

                // will parse empty map the same as map with content
                content = parseResponseMethod(request, params, result)
            }else{
                content = parseResponseMethod(request, params, model)
            }
            traceService.endTrace('ProfilerCommLayer','handleApiResponse')
            return content

        }catch(Exception e){
            traceService.endTrace('ProfilerCommLayer','handleApiResponse')
            throw new Exception("[ProfileCommLayer : handleApiResponse] : Exception - full stack trace follows:",e)
        }
    }


}
