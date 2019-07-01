/*
 * Academic Free License ("AFL") v. 3.0
 * Copyright 2014-2017 Owen Rubel
 *
 * IO State (tm) Owen Rubel 2014
 * API Chaining (tm) Owen Rubel 2013
 *
 *   https://opensource.org/licenses/AFL-3.0
 */

package grails.api.framework;

import grails.plugin.springsecurity.rest.RestAuthenticationProvider
import grails.plugin.springsecurity.rest.authentication.RestAuthenticationEventPublisher
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.reader.TokenReader
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.web.filter.GenericFilterBean

import org.springframework.web.context.request.RequestContextHolder as RCH

import javax.annotation.Resource
import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.xml.ws.Service

import grails.util.Metadata

import org.springframework.web.context.support.WebApplicationContextUtils
import org.springframework.context.ApplicationContext

//import grails.plugin.cache.GrailsCacheManager
import org.grails.plugin.cache.GrailsCacheManager
import grails.util.Holders

import javax.servlet.http.HttpSession
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.grails.plugin.cache.GrailsCacheManager
import grails.compiler.GrailsCompileStatic

/**
 * Filter for validation of token send through the request.
 *
 * @author Owen Rubel
 */
@GrailsCompileStatic
class TokenCacheValidationFilter extends GenericFilterBean {

    String headerName

    RestAuthenticationProvider restAuthenticationProvider

    AuthenticationSuccessHandler authenticationSuccessHandler
    AuthenticationFailureHandler authenticationFailureHandler
    RestAuthenticationEventPublisher authenticationEventPublisher

    TokenReader tokenReader
    String validationEndpointUrl
    Boolean active

    Boolean enableAnonymousAccess
    GrailsCacheManager grailsCacheManager

    //@Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // println("#### TokenCacheValidationFilter ####")

        HttpServletRequest httpRequest = request as HttpServletRequest
        HttpServletResponse httpResponse = response as HttpServletResponse

        //String actualUri = httpRequest.requestURI - httpRequest.contextPath
        //String[] prms = actualUri.split('/')
        //def cont = prms[2]
        //def act = prms[3]


        AccessToken accessToken

        try {
            accessToken = tokenReader.findToken(httpRequest)
            if (accessToken) {

                //log.debug "Token found: ${accessToken.accessToken}"

                accessToken = restAuthenticationProvider.authenticate(accessToken) as AccessToken

                if (accessToken.authenticated) {
                    //log.debug "Token authenticated. Storing the authentication result in the security context"
                    //log.debug "Authentication result: ${accessToken}"
                    SecurityContextHolder.context.setAuthentication(accessToken)

                    //authenticationEventPublisher.publishAuthenticationSuccess(accessToken)

                    processFilterChain(httpRequest, httpResponse, chain, accessToken)
                }else{
                    //log.debug('not authenticated')
                    httpResponse.status = 401
                    httpResponse.setHeader('ERROR', 'Token Unauthenticated. Uauthorized Access.')
                    httpResponse.writer.flush()
                    //return
                }

            } else {
                //log.debug('token not found')
                httpResponse.status = 401
                httpResponse.setHeader('ERROR', 'Token not found. Unauthorized Access.')
                httpResponse.writer.flush()
                return
            }

        } catch (AuthenticationException ae) {
            // NOTE: This will happen if token not found in database
            //log.debug('Token not found in database.')
            println("Token not found in database: "+ae)
            httpResponse.status = 401
            httpResponse.setHeader('ERROR', 'Token not found in database. Authorization Attempt Failed')
            httpResponse.writer.flush()

            //authenticationEventPublisher.publishAuthenticationFailure(ae, accessToken)
            //authenticationFailureHandler.onAuthenticationFailure(request, response, ae)
        }

    }

    @CompileDynamic
    private void processFilterChain(HttpServletRequest request, HttpServletResponse response, FilterChain chain, AccessToken authenticationResult) {

        //HttpServletRequest httpRequest = request as HttpServletRequest
        //HttpServletResponse httpResponse = response as HttpServletResponse

        String actualUri = request.requestURI - request.contextPath

        if (!active) {
            log.debug('Token validation is disabled. Continuing the filter chain')
            return
        }

        if (authenticationResult?.accessToken) {
            if (actualUri == validationEndpointUrl) {
                //log.debug 'Validation endpoint called. Generating response.'
                authenticationSuccessHandler.onAuthenticationSuccess(request, response, authenticationResult)
            } else {
                // continue..
            }
        } else {
            //log.debug 'Request does not contain any token. Letting it continue through the filter chain'
        }

        chain.doFilter(request, response)
    }

}
