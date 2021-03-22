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

import grails.util.Environment
import grails.util.Holders
import grails.core.GrailsApplication
import grails.plugin.springsecurity.SpringSecurityService
import grails.orm.HibernateCriteriaBuilder
import grails.plugins.mail.MailService

import java.nio.charset.StandardCharsets
import com.google.common.hash.Hashing

import javax.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.springframework.web.context.request.ServletRequestAttributes

// i18n
import org.springframework.context.MessageSource

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

	MessageSource messageSource

	private HttpServletRequest getRequest(){
		HttpServletRequest request = ((ServletRequestAttributes) RCH.currentRequestAttributes()).getRequest()
		return request
	}

    void check(String ip) {
		String userIpClass = grailsApplication.config.getProperty('grails.plugin.springsecurity.userLookup.userIpDomainClassName')

		String user = grailsApplication.config.getProperty('grails.plugin.springsecurity.userLookup.userDomainClassName')
		if (ip && ip!='unknown') {

			Long id = springSecurityService.principal.id
			def person = grailsApplication.getClassForName(user).get(id)
			def personIp = grailsApplication.getClassForName(userIpClass)

			// only do this for authorized users
			def temp = request.getHeader("Authorization")
			ArrayList auth = []
			if(temp) {
				auth = temp.split(" ")
				String hstr = "${auth[1]}/${ip}"
				String hashString = hashWithGuava(hstr)

				println("hash: "+hashString)

				int result = personIp.countByValidAndUser(true, person)
				println("count:"+result)
				if(result<=0){

					//create first entry
					def newPip = personIp.newInstance()
					newPip.ip=ip
					newPip.valid=true
					newPip.hash=hashString
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

					String msg1 = this.messageSource.getMessage("mail.ipConfirm.msg1", [ip] as Object[], 'Default Message', request.locale)
					String msg2 = this.messageSource.getMessage("mail.ipConfirm.msg2", [hashString] as Object[], 'Default Message', request.locale)
					String msg3 = this.messageSource.getMessage("mail.ipConfirm.msg3", [] as Object[], 'Default Message', request.locale)


					if (grails.util.Environment.current.name == "production") {
						mailService.sendMail {
							to "${person.email}"
							from "${grailsApplication.config.grails.mail.username}"
							subject "BeAPI Notification"
							html "${msg1}${msg2}${msg3}"
						}
					}else{
						//TODO : use greenmail
					}

					// we need to email user with a confirmation code
					// can also use a geoIp lookup to show user location of IP
					//}
				}


			}

		}
		// do nothing if no IP sent
	}

	void confirm(){}

	protected static String hashWithGuava(final String originalString) {
		//final String sha256hex = Hashing.sha256().hashString(originalString, StandardCharsets.UTF_8).toString()
		final String sha256hex = Hashing.murmur3_32().hashString(originalString, StandardCharsets.UTF_8).toString()
		return sha256hex
	}

}
