/*
 * Academic Free License ("AFL") v. 3.0
 * Copyright 2014-2017 Owen Rubel
 *
 * IO State (tm) Owen Rubel 2014
 * API Chaining (tm) Owen Rubel 2013
 *
 *   https://opensource.org/licenses/AFL-3.0
 */

package grails.api.framework

import grails.compiler.GrailsCompileStatic
import grails.converters.JSON
import grails.converters.XML
import grails.core.GrailsApplication
import grails.plugin.cache.GrailsConcurrentMapCache
import grails.plugin.cache.GrailsValueWrapper
import grails.plugin.springsecurity.rest.RestAuthenticationProvider
import grails.plugin.springsecurity.rest.authentication.RestAuthenticationEventPublisher
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.reader.TokenReader
import grails.util.Environment
import grails.util.Holders
import grails.util.Metadata
import grails.web.servlet.mvc.GrailsHttpSession
import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j
import org.grails.plugin.cache.GrailsCacheManager
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.springframework.web.filter.GenericFilterBean
import org.springframework.web.multipart.MultipartFile

import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.Part

//import org.springframework.cache.Cache

/**
 * Filter for validation of token send through the request.
 *
 * @author Owen Rubel
 */
@Slf4j
@GrailsCompileStatic
class ContentTypeMarshallerFilter extends GenericFilterBean {

    String headerName
    String loginUri
    boolean altUri = false

    private static final String CONTENT_DISPOSITION = "content-disposition";
    private static final String FILENAME_KEY = "filename=";

    Boolean active

    Boolean enableAnonymousAccess


    GrailsApplication grailsApplication = Holders.grailsApplication

    //@Override
    void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // println("#### ContentTypeMarshallerFilter ####")

        HttpServletRequest httpRequest = request as HttpServletRequest
        HttpServletResponse httpResponse = response as HttpServletResponse

        if(!processPreflight(httpRequest, httpResponse) ) {
            processFilterChain(httpRequest, httpResponse, chain)
        }

    }

    boolean processPreflight(HttpServletRequest request, HttpServletResponse response) {

        boolean options = 'OPTIONS' == request.method.toUpperCase()

        // TESTING BLOCK: IGNORE
        //request.getHeader('Access-Control-Request-Headers')
        //List headers = request.getHeaderNames() as List

        /**
         * First get CORS Network grps, then test the domains listed in the users network group
         * against sent origin
         */
        this.loginUri = Holders.grailsApplication.config.getProperty('grails.plugin.springsecurity.rest.login.endpointUrl')
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
                this.altUri = true
                String[] params = actualUri.split('/')
                controller = params[1]
                action = params[2]
                break;
            case ~/\/provider\/auth\/\w+/:
                this.altUri = true
                String[] params = actualUri.split('/')
                controller = params[1]
                action = params[2]
                break
            default:
                if(actualUri!='/error') {
                    response.setContentType("application/json")
                    response.setStatus(401)
                    response.getWriter().write("ContentTypeMarshallerFilter: Bad URI Access attempted at '${actualUri}'")
                    response.writer.flush()
                    return true
                }
        }

        // get users network group and config NetworkGroups list and test if networkGrp sent exists
        List<String> networkGroups = Holders.grailsApplication.config.apitoolkit['networkGroups'] as List<String>

        String networkGroupType = getNetworkGrp(version, controller, action, request, response)

        if(!networkGroups.contains(networkGroupType)){
            response.setContentType("application/json")
            response.setStatus(401)
            response.getWriter().write("NETWORKGRP for IO State file :"+controller+" cannot be found. Please double check it against available NetworkGroups in the beapi_api.yml config file.")
            response.writer.flush()
            return true
        }else {

            // Now we get networkGrpList of allowedOrigins and compare against sent Origin header
            Map corsInterceptorConfig = (Map) Holders.grailsApplication.config.apitoolkit['corsInterceptor']

            String[] includeEnvironments = corsInterceptorConfig['includeEnvironments'] ?: null
            String[] excludeEnvironments = corsInterceptorConfig['excludeEnvironments'] ?: null
            LinkedHashMap allowedOrigins = corsInterceptorConfig['networkGroups'] ?: null
            String[] networkGroupList = allowedOrigins[networkGroupType]

            //def excludeEnvRule = excludeEnvironments.contains(Environment.current.name)
            //def includeEnvRule = includeEnvironments.contains(Environment.current.name)
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

            def networkGrpListRule = networkGroupList.contains(origin)
            if (networkGroupList && networkGrpListRule) { // request origin is on the white list
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

    boolean doesContentTypeMatch(HttpServletRequest request){
        String format = (request?.format)?request.format.toUpperCase():'JSON'
        String contentType = request.getContentType()

        try{
            switch(contentType) {
                case 'text/xml':
                case 'application/xml':
                    return 'XML' == format
                    break
                case 'multipart/form-data':
                    Collection parts = request.getParts();
                    def multipartParameterNames = new LinkedHashSet<String>(parts.size());
                    MultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<String, MultipartFile>(parts.size());
                    for (Part part : parts) {
                        String filename = extractFilename(part.getHeader(CONTENT_DISPOSITION));
                        File uploadedFile = new File('/temp', filename);

                        String out = ""
                        InputStream input = part.getInputStream();
                        StringBuilder stringBuilder = new StringBuilder();
                        String line = null;
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input))
                        while ((line = bufferedReader.readLine()) != null) {
                            stringBuilder.append(line);
                        }
                        out = stringBuilder.toString()
                        if (JSON.parse(out)) {
                            format = 'JSON'
                            return 'JSON' == format
                        }

                        if (XML.parse(out)) {
                            format = 'XML'
                            return 'XML' == format
                        }

                    }
                    break
                case 'text/json':
                case 'application/json':
                    return 'JSON' == format
                    break
                default:
                    if (contentType) {
                        String temp = contentType.split(';')[0]
                        switch(temp) {
                            case 'text/xml':
                            case 'application/xml':
                                return 'XML' == format
                                break
                            case 'multipart/form-data':
                                Collection parts = request.getParts();
                                def multipartParameterNames = new LinkedHashSet<String>(parts.size());
                                MultiValueMap<String, MultipartFile> files = new LinkedMultiValueMap<String, MultipartFile>(parts.size());
                                for (Part part : parts) {
                                    String filename = extractFilename(part.getHeader(CONTENT_DISPOSITION));
                                    File uploadedFile = new File('/temp', filename);

                                    String out = ""
                                    InputStream input = part.getInputStream();
                                    StringBuilder stringBuilder = new StringBuilder();
                                    String line = null;
                                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(input))
                                    while ((line = bufferedReader.readLine()) != null) {
                                        stringBuilder.append(line);
                                    }
                                    out = stringBuilder.toString()

                                    if (JSON.parse(out)) {
                                        format = 'JSON'
                                        return 'JSON' == format
                                    }

                                    if (XML.parse(out)) {
                                        format = 'XML'
                                        return 'XML' == format
                                    }

                                }
                                break
                            case 'text/json':
                            case 'application/json':
                                return 'JSON' == format
                        }
                    }
                    return false
                    break
            }
            return false
        }catch(Exception e){
            throw new Exception("[ContentTypeMarshallerFilter :: getContentType] : Exception - full stack trace follows:",e)
        }
    }

    private String extractFilename(String contentDisposition) {
        if (contentDisposition == null) {
            return null;
        }
        // TODO: can only handle the typical case at the moment
        int startIndex = contentDisposition.indexOf(FILENAME_KEY);
        if (startIndex == -1) {
            return null;
        }

        String filename = contentDisposition.substring(startIndex + FILENAME_KEY.length());
        if (filename.startsWith("\"")) {
            int endIndex = filename.indexOf("\"", 1);
            if (endIndex != -1) {
                return filename.substring(1, endIndex);
            }
        }
        else {
            int endIndex = filename.indexOf(";");
            if (endIndex != -1) {
                return filename.substring(0, endIndex);
            }
        }
        return filename;
    }

    @CompileDynamic
    String getNetworkGrp(String version, String controller, String action, HttpServletRequest request, HttpServletResponse response){
        // login URI is always public; this is also handled by 3rd party plugin
        if(this.altUri){
            return 'public'
        }

        ApplicationContext ctx = Holders.grailsApplication.mainContext
        if(ctx) {
            GrailsCacheManager grailsCacheManager = ctx.getBean('grailsCacheManager') as GrailsCacheManager
            LinkedHashMap cache = [:]
            GrailsConcurrentMapCache temp = grailsCacheManager?.getCache('ApiCache')

            List cacheNames = temp.getAllKeys() as List
            GrailsValueWrapper tempCache
            for (it in cacheNames) {
                String cKey = it.simpleKey.toString()
                if (cKey == controller) {
                    tempCache = temp.get(it)
                    break
                }
            }

            LinkedHashMap cache2
            if (tempCache) {

                cache2 = tempCache.get() as LinkedHashMap
                version = (version.isEmpty()) ? cache2['cacheversion'] : 1

                if (!cache2?."${version}"?."${action}") {
                    //ApiDescriptor apidesc = cache2[version][action]
                    response.setContentType("application/json")
                    response.setStatus(401)
                    response.getWriter().write("IO State Not properly Formatted for this URI. Please contact the Administrator.")
                    //response.writer.flush()
                    return
                } else {
                    GrailsHttpSession session = RCH.currentRequestAttributes().getSession()
                    session['cache'] = cache2
                    return cache2[version][action]['networkGrp']
                }
            } else {
                //println("no cache found")
            }

        }else{
            //log.debug('no ctx found')
        }
    }

    @CompileDynamic
    Boolean isMultipartform(String contentType){
        try{
            switch(contentType) {
                case 'multipart/form-data':
                    return true
                    break
                default:
                    String temp = contentType.split(';')[0]
                    switch(temp) {
                        case 'multipart/form-data':
                            return true
                            break
                        default:
                            return false
                    }
            }
        }catch(Exception e){
            throw new Exception("[ContentTypeMarshallerFilter :: isMultipartform] : Exception - full stack trace follows:",e)
        }
    }

    @CompileDynamic
    private void processFilterChain(HttpServletRequest request, HttpServletResponse response, FilterChain chain) {
        String actualUri = request.requestURI - request.contextPath
        String contentType = request.getContentType()

        String format = (request?.format)?request.format.toUpperCase():'JSON'
        HashSet formats = new HashSet()

        formats.add('XML')
        formats.add('JSON')

        if(!doesContentTypeMatch(request)){
            response.setContentType("application/json")
            response.setStatus(401)
            response.getWriter().write('ContentType does not match Requested Format')
            response.writer.flush()
            return
        }

        try {
            // Init params
            def formatsRule = formats.contains(format)
            if (formatsRule) {
                LinkedHashMap dataParams = [:]
                switch (format) {
                    case 'XML':
                        if(isMultipartform(contentType)){
                            // do nothing; placeholder
                        }else {
                            dataParams = request.XML as LinkedHashMap
                            request.setAttribute('XML', dataParams)
                        }
                        break
                    case 'JSON':
                    default:
                        if(isMultipartform(contentType)){
                            // do nothing; placeholder
                        }else {
                            dataParams = request.JSON as LinkedHashMap
                            request.setAttribute('JSON', dataParams)
                        }
                        break
                }
            }

        } catch (Exception e) {
            response.setContentType("application/json")
            response.setStatus(401)
            response.getWriter().write('Badly formatted data')
            response.writer.flush()
            return
        }

        chain.doFilter(request, response)
    }

}
