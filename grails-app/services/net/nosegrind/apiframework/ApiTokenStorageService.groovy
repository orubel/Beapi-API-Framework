package net.nosegrind.apiframework

import grails.transaction.Transactional
import com.nimbusds.jose.JOSEException
import com.nimbusds.jwt.JWT
import grails.plugin.springsecurity.rest.JwtService
import grails.plugin.springsecurity.rest.token.storage.TokenNotFoundException
import grails.plugin.springsecurity.rest.token.storage.TokenStorageService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import grails.plugin.springsecurity.SpringSecurityUtils
import grails.core.GrailsApplication
import grails.util.Holders
import java.text.ParseException

import grails.plugin.springsecurity.rest.token.storage.TokenStorageService


class ApiTokenStorageService implements TokenStorageService {

    GrailsApplication grailsApplication


    public ApiTokenStorageService() {
        this.grailsApplication = Holders.grailsApplication
    }

    @Override
    UserDetails loadUserByToken(String tokenValue) throws TokenNotFoundException {
        log.debug "Finding token ${tokenValue} in GORM"
        def conf = SpringSecurityUtils.securityConfig
        String usernamePropertyName = conf.rest.token.storage.gorm.usernamePropertyName
        def existingToken = findExistingToken(tokenValue)

        if (existingToken) {
            def username = existingToken."${usernamePropertyName}"
            return userDetailsService.loadUserByUsername(username)
        }

        throw new TokenNotFoundException("Token ${tokenValue} not found")
    }

    // TODO: add 'App' to storeToken
    void storeToken(String tokenValue, UserDetails principal) {
        log.debug "Storing principal for token: ${tokenValue}"
        log.debug "Principal: ${principal}"

        def conf = SpringSecurityUtils.securityConfig
        String tokenClassName = conf.rest.token.storage.gorm.tokenDomainClassName
        String tokenValuePropertyName = conf.rest.token.storage.gorm.tokenValuePropertyName
        String usernamePropertyName = conf.rest.token.storage.gorm.usernamePropertyName
        def domainClass = grailsApplication.getClassForName(tokenClassName)

        //TODO check at startup, not here
        if (!domainClass) {
            throw new IllegalArgumentException("The specified token domain class '$tokenClassName' is not a domain class ")
        }

        domainClass.withTransaction { status ->
            def newTokenObject = domainClass.newInstance((tokenValuePropertyName): tokenValue, (usernamePropertyName): principal.username)
            newTokenObject.save()
        }
    }

    void removeToken(String tokenValue) throws TokenNotFoundException {
        log.debug "Removing token ${tokenValue} from GORM"
        def conf = SpringSecurityUtils.securityConfig
        String tokenClassName = conf.rest.token.storage.gorm.tokenDomainClassName
        def existingToken = findExistingToken(tokenValue)
        if (existingToken) {
            def domainClass = grailsApplication.getClassForName(tokenClassName)
            domainClass.withTransaction() {
                existingToken.delete(flush:true)
            }
        } else {
            throw new TokenNotFoundException("Token ${tokenValue} not found")
        }

    }

    private findExistingToken(String tokenValue) {
        log.debug "Searching in GORM for UserDetails of token ${tokenValue}"
        def conf = SpringSecurityUtils.securityConfig
        String tokenClassName = conf.rest.token.storage.gorm.tokenDomainClassName
        String tokenValuePropertyName = conf.rest.token.storage.gorm.tokenValuePropertyName
        def domainClass = grailsApplication.getClassForName(tokenClassName)

        //TODO check at startup, not here
        if (!dc) {
            throw new IllegalArgumentException("The specified token domain class '$tokenClassName' is not a domain class")
        }

        dc.withTransaction() { status ->
            return domainClass.findWhere((tokenValuePropertyName): tokenValue)
        }
    }

    public createToken(){

    }
}
