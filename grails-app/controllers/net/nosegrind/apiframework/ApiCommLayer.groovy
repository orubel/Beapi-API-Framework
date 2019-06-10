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
 * @see ApiFrameworkInterceptor
 * @see BatchkInterceptor
 * @see ChainInterceptor
 *
 */

abstract class ApiCommLayer extends ApiCommProcess{

    /**
     * Given params, handles basic tests for the API request and returns boolean based upon result
     * @see ApiFrameworkInterceptor#before()
     * @param deprecated
     * @param method
     * @param mthd
     * @param response
     * @param params
     * @return boolean returns false if past deprecation date
     */
    boolean handleRequest(List deprecated){
        try{
            // CHECK VERSION DEPRECATION DATE
            if(deprecated?.get(0)){
                if(checkDeprecationDate(deprecated[0].toString())){
                    errorResponse([400,deprecated[1].toString()])
                    return false
                }
            }
            return true
        }catch(Exception e){
            throw new Exception("[ApiCommLayer : handleApiRequest] : Exception - full stack trace follows:",e)
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
    String handleApiResponse(LinkedHashMap requestDefinitions, List roles, RequestMethod mthd, String format, LinkedHashMap model, GrailsParameterMap params){

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
    def handleBatchResponse(List authority, LinkedHashMap requestDefinitions, List roles, RequestMethod mthd, String format, HttpServletResponse response, LinkedHashMap model){
        try{
            response.setHeader('Authorization', roles.join(', '))

            ArrayList<LinkedHashMap> temp = []
            authority.each { temp.addAll(requestDefinitions["${it}"] as ArrayList) }
            if (receives['permitAll'][0] != null) { temp.addAll(requestDefinitions['permitAll'] as ArrayList) }

            //ArrayList<LinkedHashMap> temp = (requestDefinitions[authority])?requestDefinitions[authority] as ArrayList<LinkedHashMap>:requestDefinitions['permitAll'] as ArrayList<LinkedHashMap>
            ArrayList responseList = (ArrayList)temp.collect(){ it.name }
            LinkedHashMap result = parseURIDefinitions(model,responseList)

            if(!result){
                response.status = 400
            }else{
                return parseBatchResponseMethod(mthd, format, result)
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
    def handleChainResponse(List authority,LinkedHashMap requestDefinitions, List roles, RequestMethod mthd, String format, HttpServletResponse response, LinkedHashMap model, GrailsParameterMap params){
        try{
            response.setHeader('Authorization', roles.join(', '))
            ArrayList<LinkedHashMap> temp = []
            authority.each { temp.addAll(requestDefinitions["${it}"] as ArrayList) }
            if (receives['permitAll'][0] != null) { temp.addAll(requestDefinitions['permitAll'] as ArrayList) }

            //ArrayList<LinkedHashMap> temp = (requestDefinitions[authority])?requestDefinitions[authority] as ArrayList<LinkedHashMap>:requestDefinitions['permitAll'] as ArrayList<LinkedHashMap>
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
