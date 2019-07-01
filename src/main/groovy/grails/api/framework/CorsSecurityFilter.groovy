package grails.api.framework


import grails.util.Metadata

import javax.servlet.http.HttpSession
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.grails.plugin.cache.GrailsCacheManager


import org.springframework.web.filter.OncePerRequestFilter

//import net.nosegrind.apiframework.CorsService
import javax.servlet.FilterChain
import javax.servlet.ServletException

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import grails.util.Environment
import grails.util.Holders
import org.springframework.context.ApplicationContext
import grails.compiler.GrailsCompileStatic
import groovy.transform.CompileDynamic

/**
 * Filter for handling CORS(cross origin resource sharing) for request from frontend
 *
 * @author Owen Rubel
 */
@GrailsCompileStatic
class CorsSecurityFilter extends OncePerRequestFilter {

    //@Autowired
    //private ApplicationContext context

    String loginUri
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        // println("#### CorsSecurityFilter ####")

        //HttpServletRequest httpRequest = request as HttpServletRequest
        //HttpServletResponse httpResponse = response as HttpServletResponse


        if(!processPreflight(request, response) ) {
            chain.doFilter(request, response)
        }
    }

    boolean processPreflight(HttpServletRequest request, HttpServletResponse response) {

        boolean options = 'OPTIONS' == request.method.toUpperCase()

        request.getHeader('Access-Control-Request-Headers')
        List headers = request.getHeaderNames() as List

        /**
         * First get CORS Network grps, then test the domains listed in the users network group
         * against sent origin
         */
        this.loginUri = getLoginUrl()
        String actualUri = request.requestURI - request.contextPath
        String entryPoint = Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)
        String controller
        String action
        String version

        // TODO: need to also check for logoutUri
        switch(actualUri) {
            case ~/\/.{0}[a-z]${entryPoint}(-[0-9])*\/(.*)/:
                String[] params = actualUri.split('/')
                String[] temp = ((String)params[1]).split('-')
                version = (temp.size()>1) ? temp[1].toString() : ''
                controller = params[2]
                action = params[3]
                break
            case loginUri:
                String[] params = actualUri.split('/')
                controller = params[1]
                action = params[2]
                break;
            default:
                response.status = 401
                response.setHeader('ERROR', "Bad URI Access attempted at '${actualUri}'")
                response.writer.flush()
                return true
        }

        // get users network group and config NetworkGroups list and test if networkGrp sent exists
        List networkGroups = (List) Holders.grailsApplication.config.apitoolkit['networkGroups']
        String networkGroupType = getNetworkGrp(version, controller, action, request, response)
        if(!networkGroups.contains(networkGroupType)){
            response.status = 401
            response.setHeader('### beapi_api.yml CONFIG ERROR ###', "NETWORKGRP for IO State file :"+controller+" cannot be found. Please double check it against available NetworkGroups in the beapi_api.yml config file.")
            response.writer.flush()
            return true
        }else {

            // Now we get networkGrpList of allowedOrigins and compare against sent Origin header
            Map corsInterceptorConfig = (Map) Holders.grailsApplication.config.apitoolkit['corsInterceptor']

            String[] includeEnvironments = corsInterceptorConfig['includeEnvironments'] ?: null
            String[] excludeEnvironments = corsInterceptorConfig['excludeEnvironments'] ?: null
            LinkedHashMap allowedOrigins = corsInterceptorConfig['networkGroups'] ?: null
            String[] networkGroupList = allowedOrigins[networkGroupType]

            if (excludeEnvironments && excludeEnvironments.contains(Environment.current.name)) {
                // current env is excluded
                // skip
                return false
            } else if (includeEnvironments && !includeEnvironments.contains(Environment.current.name)) {
                // current env is not included
                // skip
                return false
            }



            String origin = request.getHeader('Origin')
            if (options) {
                response.addHeader('Allow', 'GET, HEAD, POST, PUT, DELETE, TRACE, PATCH, OPTIONS')
                if (origin != 'null') {
                    //response.setHeader("Access-Control-Allow-Headers", "Cache-Control,  Pragma, WWW-Authenticate, Origin, X-Requested-With, authorization, Content-Type,Access-Control-Request-Headers,Access-Control-Request-Method,Access-Control-Allow-Credentials")
                    response.addHeader('Access-Control-Allow-Headers', 'Accept, Accept-Charset, Accept-Datetime, Accept-Encoding, Accept-Ext, Accept-Features, Accept-Language, Accept-Params, Accept-Ranges, Access-Control-Allow-Headers, Access-Control-Allow-Methods, Access-Control-Allow-Origin, Access-Control-Expose-Headers, Access-Control-Max-Age, Access-Control-Request-Headers, Access-Control-Request-Method, Age, Allow, Alternates, Authentication-Info, Authorization, C-Ext, C-Man, C-Opt, C-PEP, C-PEP-Info, CONNECT, Cache-Control, Compliance, Connection, Content-Base, Content-Disposition, Content-Encoding, Content-ID, Content-Language, Content-Length, Content-Location, Content-MD5, Content-Range, Content-Script-Type, Content-Security-Policy, Content-Style-Type, Content-Transfer-Encoding, Content-Type, Content-Version, Cookie, Cost, DAV, DELETE, DNT, DPR, Date, Default-Style, Delta-Base, Depth, Derived-From, Destination, Differential-ID, Digest, ETag, Expect, Expires, Ext, From, GET, GetProfile, HEAD, HTTP-date, Host, IM, If, If-Match, If-Modified-Since, If-None-Match, If-Range, If-Unmodified-Since, Keep-Alive, Label, Last-Event-ID, Last-Modified, Link, Location, Lock-Token, MIME-Version, Man, Max-Forwards, Media-Range, Message-ID, Meter, Negotiate, Non-Compliance, OPTION, OPTIONS, OWS, Opt, Optional, Ordering-Type, Origin, Overwrite, P3P, PEP, PICS-Label, POST, PUT, Pep-Info, Permanent, Position, Pragma, ProfileObject, Protocol, Protocol-Query, Protocol-Request, Proxy-Authenticate, Proxy-Authentication-Info, Proxy-Authorization, Proxy-Features, Proxy-Instruction, Public, RWS, Range, Referer, Refresh, Resolution-Hint, Resolver-Location, Retry-After, Safe, Sec-Websocket-Extensions, Sec-Websocket-Key, Sec-Websocket-Origin, Sec-Websocket-Protocol, Sec-Websocket-Version, Security-Scheme, Server, Set-Cookie, Set-Cookie2, SetProfile, SoapAction, Status, Status-URI, Strict-Transport-Security, SubOK, Subst, Surrogate-Capability, Surrogate-Control, TCN, TE, TRACE, Timeout, Title, Trailer, Transfer-Encoding, UA-Color, UA-Media, UA-Pixels, UA-Resolution, UA-Windowpixels, URI, Upgrade, User-Agent, Variant-Vary, Vary, Version, Via, Viewport-Width, WWW-Authenticate, Want-Digest, Warning, Width, xsrf-token, X-Content-Duration, X-Content-Security-Policy, X-Content-Type-Options, X-CustomHeader, X-DNSPrefetch-Control, X-Forwarded-For, X-Forwarded-Port, X-Forwarded-Proto, X-Frame-Options, X-Modified, X-OTHER, X-PING, X-PINGOTHER, X-Powered-By, X-Requested-With')
                    response.addHeader('Access-Control-Allow-Methods', 'POST, PUT, DELETE, TRACE, PATCH, OPTIONS')
                    response.addHeader('Access-Control-Expose-Headers', 'xsrf-token, Location,X-Auth-Token')
                    response.addHeader('Access-Control-Max-Age', '3600')
                }
            }


            if (networkGroupList && networkGroupList.contains(origin)) { // request origin is on the white list
                // add CORS access control headers for the given origin
                response.setHeader('Access-Control-Allow-Origin', origin)
                response.addHeader('Access-Control-Allow-Credentials', 'true')
            } else if (!networkGroupList) { // no origin; white list
                // add CORS access control headers for all origins
                if (origin) {
                    response.setHeader('Access-Control-Allow-Origin', origin)
                    response.addHeader('Access-Control-Allow-Credentials', 'true')
                } else {
                    response.addHeader('Access-Control-Allow-Origin', '*')
                }
            }
        }
        response.status = HttpStatus.OK.value()
        return options
    }

    @CompileDynamic
    String getLoginUrl(){
        return Holders.grailsApplication.config.grails.plugin.springsecurity.rest.login.endpointUrl
    }

    @CompileDynamic
    String getNetworkGrp(String version, String controller, String action, HttpServletRequest request, HttpServletResponse response){
        // login URI is always public; this is also handled by 3rd party plugin
        //String loginUrl = getLoginUrl()
        if("/${controller}/${action}" == this.loginUri){
            return 'public'
        }

        ApplicationContext ctx = Holders.grailsApplication.mainContext
        if(ctx) {
            GrailsCacheManager grailsCacheManager = ctx.getBean('grailsCacheManager') as GrailsCacheManager
            LinkedHashMap cache = [:]
            def temp = grailsCacheManager?.getCache('ApiCache')
            List cacheNames = temp.getAllKeys() as List
            def tempCache
            for (it in cacheNames) {
                String cKey = it.simpleKey.toString()
                if (cKey == controller) {
                    tempCache = temp.get(it)
                    break
                }
            }

            def cache2
            if (tempCache) {
                cache2 = tempCache.get() as LinkedHashMap
                version = (version.isEmpty()) ? cache2['cacheversion'] : version

                if (!cache2?."${version}"?."${action}") {
                    response.status = 401
                    response.setHeader('ERROR', 'IO State Not properly Formatted for this URI. Please contact the Administrator.')
                    response.writer.flush()
                    return
                } else {
                    def session = RCH.currentRequestAttributes().getSession()
                    session['cache'] = cache2
                    return session['cache'][version][action]['networkGrp']
                }
            } else {
                //println("no cache found")
            }

        }else{
            //log.debug('no ctx found')
        }
    }
}
