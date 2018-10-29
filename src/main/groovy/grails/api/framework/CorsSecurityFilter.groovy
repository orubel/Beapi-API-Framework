package grails.api.framework


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

class CorsSecurityFilter extends OncePerRequestFilter {

    //@Autowired
    //private ApplicationContext context

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        println("#### CorsSecurityFilter ####")
        HttpServletRequest httpRequest = request as HttpServletRequest
        HttpServletResponse httpResponse = response as HttpServletResponse

        //def filterChain = context.getBean('springSecurityFilterChain')
        //println("FILTERCHAIN : "+filterChain)

        if( !processPreflight(httpRequest, httpResponse) ) {
            println('chaining...')
            chain.doFilter(request, response)
        }
    }

    boolean processPreflight(HttpServletRequest request, HttpServletResponse response) {

        Map corsInterceptorConfig = (Map) Holders.grailsApplication.config.apitoolkit.corsInterceptor

        String[] includeEnvironments = corsInterceptorConfig['includeEnvironments']?: null
        String[] excludeEnvironments = corsInterceptorConfig['excludeEnvironments']?: null
        String[] allowedOrigins = corsInterceptorConfig['allowedOrigins']?: null

        if( excludeEnvironments && excludeEnvironments.contains(Environment.current.name) )  { // current env is excluded
            // skip
            println('skip1')
            return false
        } else if( includeEnvironments && !includeEnvironments.contains(Environment.current.name) )  {  // current env is not included
            // skip
            println('skip2')
            return false
        }



        String origin = request.getHeader("Origin")
        boolean options = "OPTIONS"==request.method.toUpperCase()
println(request.method.toUpperCase()+"/"+origin)

        //request.getHeader("Access-Control-Request-Headers")

        if (options) {
            println('options')
            response.setHeader("Allow", "GET, HEAD, POST, PUT, DELETE, TRACE, PATCH, OPTIONS")
            if (origin != 'null') {
                println('origin not null')
                response.setHeader("Access-Control-Allow-Headers","Cache-Control, Pragma, WWW-Authenticate, Origin, authorization, Content-Type, Access-Control-Request-Headers")
                response.setHeader("Access-Control-Allow-Headers", "Cache-Control,  Pragma, WWW-Authenticate, Origin, X-Requested-With, authorization, Content-Type,Access-Control-Request-Headers,Access-Control-Request-Method")
                response.setHeader("Access-Control-Allow-Headers", "Accept, Accept-CH, Accept-Charset, Accept-Datetime, Accept-Encoding, Accept-Ext, Accept-Features, Accept-Language, Accept-Params, Accept-Ranges, Access-Control-Allow-Credentials, Access-Control-Allow-Headers, Access-Control-Allow-Methods, Access-Control-Allow-Origin, Access-Control-Expose-Headers, Access-Control-Max-Age, Access-Control-Request-Headers, Access-Control-Request-Method, Age, Allow, Alternates, Authentication-Info, Authorization, C-Ext, C-Man, C-Opt, C-PEP, C-PEP-Info, CONNECT, Cache-Control, Compliance, Connection, Content-Base, Content-Disposition, Content-Encoding, Content-ID, Content-Language, Content-Length, Content-Location, Content-MD5, Content-Range, Content-Script-Type, Content-Security-Policy, Content-Style-Type, Content-Transfer-Encoding, Content-Type, Content-Version, Cookie, Cost, DAV, DELETE, DNT, DPR, Date, Default-Style, Delta-Base, Depth, Derived-From, Destination, Differential-ID, Digest, ETag, Expect, Expires, Ext, From, GET, GetProfile, HEAD, HTTP-date, Host, IM, If, If-Match, If-Modified-Since, If-None-Match, If-Range, If-Unmodified-Since, Keep-Alive, Label, Last-Event-ID, Last-Modified, Link, Location, Lock-Token, MIME-Version, Man, Max-Forwards, Media-Range, Message-ID, Meter, Negotiate, Non-Compliance, OPTION, OPTIONS, OWS, Opt, Optional, Ordering-Type, Origin, Overwrite, P3P, PEP, PICS-Label, POST, PUT, Pep-Info, Permanent, Position, Pragma, ProfileObject, Protocol, Protocol-Query, Protocol-Request, Proxy-Authenticate, Proxy-Authentication-Info, Proxy-Authorization, Proxy-Features, Proxy-Instruction, Public, RWS, Range, Referer, Refresh, Resolution-Hint, Resolver-Location, Retry-After, Safe, Sec-Websocket-Extensions, Sec-Websocket-Key, Sec-Websocket-Origin, Sec-Websocket-Protocol, Sec-Websocket-Version, Security-Scheme, Server, Set-Cookie, Set-Cookie2, SetProfile, SoapAction, Status, Status-URI, Strict-Transport-Security, SubOK, Subst, Surrogate-Capability, Surrogate-Control, TCN, TE, TRACE, Timeout, Title, Trailer, Transfer-Encoding, UA-Color, UA-Media, UA-Pixels, UA-Resolution, UA-Windowpixels, URI, Upgrade, User-Agent, Variant-Vary, Vary, Version, Via, Viewport-Width, WWW-Authenticate, Want-Digest, Warning, Width, xsrf-token, X-Content-Duration, X-Content-Security-Policy, X-Content-Type-Options, X-CustomHeader, X-DNSPrefetch-Control, X-Forwarded-For, X-Forwarded-Port, X-Forwarded-Proto, X-Frame-Options, X-Modified, X-OTHER, X-PING, X-PINGOTHER, X-Powered-By, X-Requested-With");
                response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, TRACE, PATCH, OPTIONS")
                response.setHeader("Access-Control-Allow-Credentials", "true")
                response.addHeader("Access-Control-Expose-Headers", "xsrf-token, Location")
                response.setHeader("Access-Control-Max-Age", "3600")
            }
        }



        if(allowedOrigins && allowedOrigins.contains(origin)) { // request origin is on the white list
            println("allowed origin:"+origin)
            // add CORS access control headers for the given origin
            response.setHeader("Access-Control-Allow-Origin", origin)
            response.setHeader("Access-Control-Allow-Credentials", "true")
            response.status = HttpStatus.OK.value()
            //return false
        } else if( !allowedOrigins ) { // no origin; white list
            // add CORS access control headers for all origins
            response.setHeader("Access-Control-Allow-Origin", origin ?: "*")
            response.setHeader("Access-Control-Allow-Credentials", "true")
            response.status = HttpStatus.OK.value()
            //return false
        }


println(options)
        return options
    }
}
