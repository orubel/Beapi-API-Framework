/* Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.nosegrind.apiframework


import org.apache.commons.lang3.RandomStringUtils

import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.TokenGenerator
import grails.plugin.springsecurity.rest.token.storage.TokenStorageService
import org.springframework.security.core.context.SecurityContextHolder
import grails.plugin.springsecurity.rest.token.rendering.AccessTokenJsonRenderer
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.authority.SimpleGrantedAuthority


class ProviderController{

	TokenGenerator tokenGenerator
	TokenStorageService tokenStorageService
	AccessTokenJsonRenderer accessTokenJsonRenderer

	LinkedHashMap auth() {
		switch (params.id) {
			case 'google':
				def user = checkUser(params.email)
				if(user) {
					Set<SimpleGrantedAuthority> auth = new HashSet<>();
					user.authorities.each() { it ->
						SimpleGrantedAuthority temp = new SimpleGrantedAuthority(it.authority)
						auth.add(temp)
					}
					UserDetails uDeets = new User(user.username, user.password, user.enabled, user.accountExpired, user.passwordExpired, user.accountLocked, auth)
					AccessToken accessToken = tokenGenerator.generateAccessToken(uDeets)
					tokenStorageService.storeToken(accessToken.accessToken, uDeets)
					SecurityContextHolder.context.setAuthentication(accessToken)

					response.addHeader 'Cache-Control', 'no-store'
					response.addHeader 'Pragma', 'no-cache'
					render contentType: 'application/json', encoding: 'UTF-8', text: accessTokenJsonRenderer.generateJson(accessToken)
				}else{

				}
				break
			case 'twitter':
			default:
				break
		}
	}

	private def checkUser(String email){
		def Person = grailsApplication.getDomainClass(grailsApplication.config.grails.plugin.springsecurity.userLookup.userDomainClassName).clazz
		def user = Person.findByEmail(email)

		if(user){
			// check if if acct is locked or disabled
			if(user.accountLocked || !user.enabled){
				// TODO: send email to user with instructions on how to unlock
				render( status: 403, text: "Account is locked/disabled. Please check your email for a notification.")
			}else{
				return user
			}
		}else {
			// else if no user, create user
			user = createUser()
			return user
		}
	}

	private def createUser(){
		def Person = grailsApplication.getDomainClass(grailsApplication.config.grails.plugin.springsecurity.userLookup.userDomainClassName).clazz
		def PersonRole = grailsApplication.getDomainClass(grailsApplication.config.grails.plugin.springsecurity.userLookup.authorityJoinClassName).clazz
		def Role = grailsApplication.getDomainClass(grailsApplication.config.grails.plugin.springsecurity.authority.className).clazz

		// create username
		Integer inc = 1
		String username = createUsername(inc)
		def user = Person.findByUsername(username)

		while(user){
			inc++
			username = createUsername(inc)
			user = Person.findByUsername(username)
		}

		PersonRole.withTransaction { status ->
			def userRole = Role.findByAuthority("ROLE_USER")
			def person = grailsApplication.getDomainClass(grailsApplication.config.grails.plugin.springsecurity.userLookup.userDomainClassName).newInstance()
			def pRole = grailsApplication.getDomainClass(grailsApplication.config.grails.plugin.springsecurity.userLookup.authorityJoinClassName).newInstance()

			if (!user?.id) {
				String password = generatePassword()
				//Map person = [username: username, password: password, email: params.email]
				person.properties = [username: username, password: password, email: params.email]
				person.save(flush: true)
			}

			if (!user?.authorities?.contains(userRole)) {
				user = Person.findByUsername(username)

				// pRole.properties = [person_id: person.id, role_id: userRole.id]
				pRole.person = person
				pRole.role =  userRole
				pRole.save(flush: true)
			}

			status.isCompleted()
		}

		return user
	}

	private String generatePassword(){
		String charset = (('A'..'Z') + ('0'..'9')).join()
		Integer length = 9
		String password = RandomStringUtils.random(length, charset.toCharArray())
		return password
	}

	String createUsername(Integer inc){
		if(params.fname.size()>inc && params.lname.size()>inc) {
			return (params.fname[0..(inc - 1)]+params.lname[0..-inc]+inc)
		}
	}
}

