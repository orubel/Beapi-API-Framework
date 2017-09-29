package net.nosegrind.apiframework


import javax.servlet.http.HttpServletRequest
import grails.web.servlet.mvc.GrailsParameterMap
import javax.servlet.forward.*
import org.grails.groovy.grails.commons.*
import javax.servlet.http.HttpServletResponse
import groovy.transform.CompileStatic
import net.nosegrind.apiframework.RequestMethod
import grails.compiler.GrailsCompileStatic

// extended by Intercepters
@GrailsCompileStatic
abstract class ApiCommLayer extends ApiCommProcess{

    /***************************
     * REQUESTS
     ***************************/
    boolean handleApiRequest(List deprecated, String method, RequestMethod mthd, HttpServletResponse response, GrailsParameterMap params){
        try{
            // CHECK VERSION DEPRECATION DATE
            if(deprecated?.get(0)){
                if(checkDeprecationDate(deprecated[0].toString())){
                    String depMsg = deprecated[1].toString()
                    response.status = 400
                    response.setHeader('ERROR',depMsg)
                    return false
                }
            }

            // DOES api.methods.contains(request.method)
            if(!isRequestMatch(method,mthd)){
                response.status = 400
                response.setHeader('ERROR',"Request method doesn't match expected method.")
                return false
            }
            return true
        }catch(Exception e){
            throw new Exception("[ApiCommLayer : handleApiRequest] : Exception - full stack trace follows:",e)
        }
    }

    boolean handleBatchRequest(List deprecated, String method, RequestMethod mthd, HttpServletResponse response, GrailsParameterMap params){
        int status = 400
        try{

            // CHECK VERSION DEPRECATION DATE
            if(deprecated?.get(0)){
                if(checkDeprecationDate(deprecated[0].toString())){
                    String depMsg = deprecated[1].toString()
                    response.status = status
                    response.setHeader('ERROR',depMsg)
                    return false
                }
            }

            // DOES api.methods.contains(request.method)
            if(!isRequestMatch(method,mthd)){
                response.status = status
                response.setHeader('ERROR',"Request method doesn't match expected method.")
                return false
            }
            return true
        }catch(Exception e){
            throw new Exception("[ApiCommLayer : handleBatchRequest] : Exception - full stack trace follows:",e)
        }
    }


    boolean handleChainRequest(List deprecated, String method, RequestMethod mthd,HttpServletResponse response, GrailsParameterMap params){
        try{
            // CHECK VERSION DEPRECATION DATE
            if(deprecated?.get(0)){
                if(checkDeprecationDate(deprecated[0].toString())){
                    String depMsg = deprecated[1].toString()
                    response.status = 400
                    response.setHeader('ERROR',depMsg)
                    return false
                }
            }

            // DOES api.methods.contains(request.method)
            if(!isRequestMatch(method,mthd)){
                response.status = 400
                response.setHeader('ERROR',"Request method doesn't match expected method.")
                return false
            }

            return true
        }catch(Exception e){
            throw new Exception("[ApiCommLayer : handleBatchRequest] : Exception - full stack trace follows:",e)
        }
    }

    /***************************
    * RESPONSES
     ***************************/
    def handleApiResponse(LinkedHashMap requestDefinitions, List roles, RequestMethod mthd, String format, HttpServletResponse response, LinkedHashMap model, GrailsParameterMap params){

        try{
            String authority = getUserRole() as String
            response.setHeader('Authorization', roles.join(', '))
            ArrayList responseList = []
            ArrayList<LinkedHashMap> temp = new ArrayList()
            if(requestDefinitions["${authority}".toString()]) {
                ArrayList<LinkedHashMap> temp1 = requestDefinitions["${authority}".toString()] as ArrayList<LinkedHashMap>
                temp.addAll(temp1)
            }else{
                ArrayList<LinkedHashMap> temp2 = requestDefinitions['permitAll'] as ArrayList<LinkedHashMap>
                temp.addAll(temp2)
            }


            responseList = (ArrayList)temp?.collect(){ if(it!=null){it.name} }

            String content
            if(params.controller!='apidoc') {
                LinkedHashMap result = parseURIDefinitions(model, responseList)
                // will parse empty map the same as map with content
                content = parseResponseMethod(mthd, format, params, result)

            }else{
                content = parseResponseMethod(mthd, format, params, model)
            }
            return content

        }catch(Exception e){
            throw new Exception("[ApiCommLayer : handleApiResponse] : Exception - full stack trace follows:",e)
        }
    }

    def handleBatchResponse(LinkedHashMap requestDefinitions, List roles, RequestMethod mthd, String format, HttpServletResponse response, LinkedHashMap model, GrailsParameterMap params){
        try{
            String authority = getUserRole() as String
            response.setHeader('Authorization', roles.join(', '))

            ArrayList<LinkedHashMap> temp = (requestDefinitions["${authority}"])?requestDefinitions["${authority}"] as ArrayList<LinkedHashMap>:requestDefinitions['permitAll'] as ArrayList<LinkedHashMap>
            ArrayList responseList = (ArrayList)temp.collect(){ it.name }

            LinkedHashMap result = parseURIDefinitions(model,responseList)

            // TODO : add combine functionality for batching
            //if(params?.apiBatch.combine=='true'){
            //	params.apiCombine["${params.uri}"] = result
            //}

            if(!result){
                response.status = 400
            }else{
                //LinkedHashMap content = parseResponseMethod(request, params, result)
                return parseResponseMethod(mthd, format, params, result)
            }
        }catch(Exception e){
            throw new Exception("[ApiCommLayer : handleBatchResponse] : Exception - full stack trace follows:",e)
        }
    }


    def handleChainResponse(LinkedHashMap requestDefinitions, List roles, RequestMethod mthd, String format, HttpServletResponse response, LinkedHashMap model, GrailsParameterMap params){
        try{
            String authority = getUserRole() as String
            response.setHeader('Authorization', roles.join(', '))

            ArrayList<LinkedHashMap> temp = (requestDefinitions["${authority}"])?requestDefinitions["${authority}"] as ArrayList<LinkedHashMap>:requestDefinitions['permitAll'] as ArrayList<LinkedHashMap>

            ArrayList responseList = (ArrayList)temp.collect(){ it.name }

            LinkedHashMap result = parseURIDefinitions(model,responseList)
            LinkedHashMap chain = params.apiChain as LinkedHashMap

            if (chain?.combine == 'true') {
                if (!params.apiCombine) {
                    params.apiCombine = [:]
                }
                String currentPath = "${params.controller}/${params.action}"
                params.apiCombine[currentPath] = result
            }

            if(!result){
                response.status = 400
            }else{
                //LinkedHashMap content = parseResponseMethod(request, params, result)
                return parseResponseMethod(mthd, format, params, result)
            }

        }catch(Exception e){
            throw new Exception("[ApiResponseService :: handleApiResponse] : Exception - full stack trace follows:",e)
        }
    }


}
