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
import grails.orm.HibernateCriteriaBuilder
import grails.plugins.mail.MailService
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

	MailService mailService

    void check(String ip) {
		String userIpClass = grailsApplication.config.getProperty('grails.plugin.springsecurity.userLookup.userIpDomainClassName')

		String user = grailsApplication.config.getProperty('grails.plugin.springsecurity.userLookup.userDomainClassName')
		if (ip && ip!='unknown') {

			Long id = springSecurityService.principal.id
			def person = grailsApplication.getClassForName(user).get(id)
			def personIp = grailsApplication.getClassForName(userIpClass)


			int result = personIp.countByValidAndUser(true, person)
			println("count:"+result)
			if(result<=0){
				//create first entry
				def newPip = personIp.newInstance()
				newPip.ip=ip
				newPip.valid=true
				newPip.user=person

				if (!newPip.save(flush: true)) {
					newPip.errors.allErrors.each {
						println("err:"+it)
					}
				}else {
					// test that it was accurately inserted
					//ArrayList results2 = results.findIp("from PersonIp where valid=true and user.id=?", [id])
				}
			}else{
				//def results = personIp.findAllIp("from PersonIp where valid=true and ip=? and user.id=?", [ip,id])

				def criteria = personIp.createCriteria()
				def results = criteria.listDistinct() {
					projections {
						groupProperty("ip")
					}
					eq("valid", true)
					and {
						eq("user.id", id)
					}
				}
				println(results)


				//if(!results){

					mailService.sendMail {
						to "${person.email}"
						from "${grailsApplication.config.grails.mail.username}"
						subject "BeAPI Notification"
						text 'this is some text'
					}

					// we need to email user with a confirmation code
					// can also use a geoIp lookup to show user location of IP
				//}


			}

		}
		// do nothing if no IP sent
	}

	void confirm(){}

}
