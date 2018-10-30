package net.nosegrind.apiframework


import grails.web.servlet.mvc.GrailsParameterMap
import javax.servlet.forward.*
import org.grails.groovy.grails.commons.*
import javax.servlet.http.HttpServletResponse
import grails.compiler.GrailsCompileStatic

/**
 *
 * This abstract provides basic communication logic for prehandling/posthandling and to simplify/standardize processing of
 * the request/response.
 *
 * @see ApiFrameworkInterceptor
 * @see BatchkInterceptor
 * @see ChainInterceptor
 *
 */
@GrailsCompileStatic
abstract class ApiCommLayer extends ApiCommProcess{

    /**
     * Given params, handles basic tests for the API request and returns boolean based upon result
     * @see ApiFrameworkInterceptor#before()
     * @param deprecated
     * @param method
     * @param mthd
     * @param response
     * @param params
     * @return
     */
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

    /**
     * Given params, handles basic tests for the BATCH request and returns boolean based upon result
     * @see BatchInterceptor#before()
     * @param deprecated
     * @param method
     * @param mthd
     * @param response
     * @param params
     * @return
     */
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

    /**
     * Given params, handles basic tests for the CHAIN request and returns boolean based upon result
     * @see ChainInterceptor#before()
     * @param deprecated
     * @param method
     * @param mthd
     * @param response
     * @param params
     * @return
     */
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

    /**
     * Givens params, parses API data to return a format resource ready to return to client
     * @see ApiFrameworkInterceptor#after()
     * @param requestDefinitions
     * @param roles
     * @param mthd
     * @param format
     * @param response
     * @param model
     * @param params
     * @return
     */
    def handleApiResponse(LinkedHashMap requestDefinitions, List roles, RequestMethod mthd, String format, HttpServletResponse response, LinkedHashMap model, GrailsParameterMap params){

        try{
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
            return content

        }catch(Exception e){
            throw new Exception("[ApiCommLayer : handleApiResponse] : Exception - full stack trace follows:",e)
        }
    }

    /**
     * Givens params, parses BATCH data to return a format resource ready to return to client
     * @see BatchInterceptor#after()
     * @param requestDefinitions
     * @param roles
     * @param mthd
     * @param format
     * @param response
     * @param model
     * @param params
     * @return
     */
    def handleBatchResponse(LinkedHashMap requestDefinitions, List roles, RequestMethod mthd, String format, HttpServletResponse response, LinkedHashMap model, GrailsParameterMap params){
        try{
            String authority = getUserRole() as String
            response.setHeader('Authorization', roles.join(', '))

            ArrayList<LinkedHashMap> temp = (requestDefinitions[authority.toString()])?requestDefinitions[authority.toString()] as ArrayList<LinkedHashMap>:requestDefinitions['permitAll'] as ArrayList<LinkedHashMap>
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

    /**
     * Givens params, parses CHAIN data to return a format resource ready to return to client
     * @see ChainInterceptor#after()
     * @param requestDefinitions
     * @param roles
     * @param mthd
     * @param format
     * @param response
     * @param model
     * @param params
     * @return
     */
    def handleChainResponse(LinkedHashMap requestDefinitions, List roles, RequestMethod mthd, String format, HttpServletResponse response, LinkedHashMap model, GrailsParameterMap params){
        try{
            String authority = getUserRole() as String
            response.setHeader('Authorization', roles.join(', '))

            ArrayList<LinkedHashMap> temp = (requestDefinitions[authority.toString()])?requestDefinitions[authority.toString()] as ArrayList<LinkedHashMap>:requestDefinitions['permitAll'] as ArrayList<LinkedHashMap>

            ArrayList responseList = (ArrayList)temp.collect(){ it.name }

            LinkedHashMap result = parseURIDefinitions(model,responseList)
            LinkedHashMap chain = params.apiChain as LinkedHashMap

            if (chain?.combine == 'true') {
                if (!params.apiCombine) {
                    params.apiCombine = [:]
                }
                String currentPath = params.controller.toString()+"/"+params.action.toString()
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
