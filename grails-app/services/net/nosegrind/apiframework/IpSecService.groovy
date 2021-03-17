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


import grails.util.Holders
import grails.core.GrailsApplication
import grails.plugin.springsecurity.SpringSecurityService

/**
 *
 * @author Owen Rubel
 */
class IpSecService {

	/**
	 * Application Class
	 */
	GrailsApplication grailsApplication

	SpringSecurityService springSecurityService


    void check(String ip) {
		String userClass = Holders.grailsApplication.config.getProperty('grails.plugin.springsecurity.userLookup.userIpDomainClassName')
		if (ip && ip!='unknown') {



			// TODO : may not need springSecurityService? Can we just pass this var???
			Long id = springSecurityService.principal.id
			ArrayList results = grailsApplication.getClassForName(userClass).findAll("from PersonIp where valid=true and ip=? and user.id=?", [ip, id])



			println(results.size())
		}



        // check to see if already exists
		// do we need to email user??
		// getClientIp(HttpServletRequest request)
	}

	void confirm(){}

}
