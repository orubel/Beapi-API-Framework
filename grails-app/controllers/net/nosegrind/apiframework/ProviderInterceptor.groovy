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
import grails.core.GrailsApplication
import grails.plugin.springsecurity.SpringSecurityService

import grails.util.Metadata
import grails.converters.JSON
import grails.converters.XML
import org.grails.web.json.JSONObject

import grails.util.Holders

import javax.servlet.http.HttpServletResponse
import groovy.transform.CompileStatic
import net.nosegrind.apiframework.HookService
import net.nosegrind.apiframework.StatsService
import javax.servlet.http.HttpSession


/**
 *
 * HandlerInterceptor for Basic API Calls. Parses XML/JSON, handles authentication, rate limiting, caching, and statistics reporting
 * @author Owen Rubel
 *
 * @see ApiCommLayer
 * @see BatchInterceptor
 * @see ChainInterceptor
 *
 */
@CompileStatic
class ProviderInterceptor{

	int order = HIGHEST_PRECEDENCE + 990

	@Resource
	GrailsApplication grailsApplication

	String format
	List formats = ['XML', 'JSON']
	String mthdKey
	RequestMethod mthd


	LinkedHashMap stat = [:]
	String contentType

	String controller
	String action



	List roles
	List authority


	/**
	 * Constructor for ProviderInterceptor.
	 * @return
	 */
	ProviderInterceptor(){
		match(uri:"/provider/auth/**")
	}

	/**
	 * PreHandler for the HandlerInterceptor
	 * @return
	 */
	boolean before(){
		println("##### PROVIDERINTERCEPTOR (BEFORE) - ${params.controller}/${params.action}")

		// TESTING: SHOW ALL FILTERS IN CHAIN
		//def filterChain = grailsApplication.mainContext.getBean('springSecurityFilterChain')
		//println("FILTERCHAIN : "+filterChain)

		format = (request?.format)?request.format.toUpperCase():'JSON'
		mthdKey = request.method.toUpperCase()
		mthd = (RequestMethod) RequestMethod[mthdKey]
		contentType = request.getContentType()

		// TODO: Check if user in USER roles and if this request puts user over 'rateLimit'



		// Init params
		if (formats.contains(format)) {
			LinkedHashMap attribs = [:]
			switch (format) {
				case 'XML':
					attribs = request.getAttribute('XML') as LinkedHashMap
					break
				case 'JSON':
				default:
					println("### JSON ###")
					println(request.getAttribute('JSON'))
					attribs = request.getAttribute('JSON') as LinkedHashMap
					break
			}

			if(attribs) {
				attribs.each() { k, v ->
					params.put(k, v)
				}
			}
			println("### ATTRIBS:" + attribs)
		}


		controller = params?.controller
		action = params.action

		return true
	}

	/**
	 * PostHandler
	 * @return
	 */
	boolean after() {
		println("##### PROVIDERINTERCEPTOR (AFTER) - ${params.controller}/${params.action}")

		return true
	}




}
