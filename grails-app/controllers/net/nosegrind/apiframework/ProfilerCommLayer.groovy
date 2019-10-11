package net.nosegrind.apiframework

import javax.annotation.Resource
import javax.servlet.http.HttpServletRequest
import grails.web.servlet.mvc.GrailsParameterMap
import javax.servlet.forward.*
import org.grails.groovy.grails.commons.*
import javax.servlet.http.HttpServletResponse

/**
 *
 * This abstract provides basic communication logic for prehandling/posthandling and to simplify/standardize processing of
 * the request/response.
 * @author Owen Rubel
 *
 * @see ProfilerInterceptor
 *
 */

abstract class ProfilerCommLayer extends ProfilerCommProcess{


    @Resource
    TraceService traceService


    /**
     * Given params, handles basic tests for the API request and returns boolean based upon result
     * @see ProfilerInterceptor#before()
     * @param deprecated
     * @param method
     * @param mthd
     * @param response
     * @param params
     * @return boolean returns false if past deprecation date
     */
    boolean handleRequest(List deprecated){
        try{
            traceService.startTrace('ProfilerCommLayer','handleRequest')

            // CHECK VERSION DEPRECATION DATE
            if(deprecated?.get(0)){
                if(checkDeprecationDate(deprecated[0].toString())){
                    traceService.endTrace('ProfilerCommLayer','handleRequest')
                    errorResponse([400,deprecated[1].toString()])
                    return false
                }
            }
            traceService.endTrace('ProfilerCommLayer','handleRequest')
            return true
        }catch(Exception e){
            throw new Exception("[ProfilerCommLayer : handleRequest] : Exception - full stack trace follows:",e)
        }
    }

    /**
     * Givens params, parses API data to return a format resource ready to return to client
     * @see ProfilerInterceptor#after()
     * @param requestDefinitions
     * @param roles
     * @param mthd
     * @param format
     * @param response
     * @param model
     * @param params
     * @return
     */
    String handleApiResponse(LinkedHashMap requestDefinitions, List roles, RequestMethod mthd, String format, LinkedHashMap model, GrailsParameterMap params){
        try{
            traceService.startTrace('ProfilerCommLayer','handleApiResponse')

            String content
            if(params.controller=='apidoc') {
                content = parseResponseMethod(mthd, format, params, model)
            }else{
                String authority = getUserRole() as String
                response.setHeader('Authorization', roles.join(', '))
                ArrayList responseList = []
                ArrayList<LinkedHashMap> temp = new ArrayList()
                if(requestDefinitions[authority.toString()]) {
                    ArrayList<LinkedHashMap> temp1 = requestDefinitions[authority.toString()] as ArrayList<LinkedHashMap>
                    temp.addAll(temp1)
                }else{
                    ArrayList<LinkedHashMap> temp2 = requestDefinitions['permitAll'] as ArrayList<LinkedHashMap>
                    temp.addAll(temp2)
                }

                responseList = (ArrayList)temp?.collect(){ if(it!=null){it.name} }

                LinkedHashMap result = parseURIDefinitions(model, responseList)
                // will parse empty map the same as map with content

                content = parseResponseMethod(mthd, format, params, result)
            }
            traceService.endTrace('ProfilerCommLayer','handleApiResponse')
            return content

        }catch(Exception e){
            throw new Exception("[ProfilerCommLayer : handleApiResponse] : Exception - full stack trace follows:",e)
        }
    }

}
