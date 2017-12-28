package grails.api.framework

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.filter.GenericFilterBean
import org.springframework.web.filter.OncePerRequestFilter

//import net.nosegrind.apiframework.CorsService
import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse

import javax.servlet.http.HttpServletRequest
//import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import grails.util.Environment
import grails.util.Holders

import com.google.common.io.CharStreams


class CorsSecurityFilter extends OncePerRequestFilter {

    //@Autowired
    //CorsService crsService



    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        //println("#### CorsSecurityFilter ####")
        HttpServletRequest httpRequest = request as HttpServletRequest
        HttpServletResponse httpResponse = response as HttpServletResponse

        if( !processPreflight(httpRequest, httpResponse) ) {
            chain.doFilter(request, response)
        }
    }

    boolean processPreflight(HttpServletRequest request, HttpServletResponse response) {

        Map corsInterceptorConfig = (Map) Holders.grailsApplication.config.corsInterceptor

        String[] includeEnvironments = corsInterceptorConfig['includeEnvironments']?: null
        String[] excludeEnvironments = corsInterceptorConfig['excludeEnvironments']?: null
        String[] allowedOrigins = corsInterceptorConfig['allowedOrigins']?: null

        if( excludeEnvironments && excludeEnvironments.contains(Environment.current.name) )  { // current env is excluded
            // skip
            return false
        } else if( includeEnvironments && !includeEnvironments.contains(Environment.current.name) )  {  // current env is not included
            // skip
            return false
        }

        String origin = request.getHeader("Origin")
        boolean options = ("OPTIONS" == request.method)

        if (options) {
            response.setHeader("Allow", "GET, HEAD, POST, PUT, DELETE, TRACE, PATCH, OPTIONS")
            if (origin != null) {
                //response.setHeader("Access-Control-Allow-Headers", "Cache-Control, Pragma, WWW-Authenticate, Origin, authorization, Content-Type, Access-Control-Request-Headers")
                //response.setHeader("Access-Control-Allow-Headers", "Cache-Control, Pragma, WWW-Authenticate, Origin, authorization, Content-Type,Access-Control-Request-Headers,Access-Control-Request-Method")
                //response.setHeader("Access-Control-Allow-Methods", "GET, HEAD, POST, PUT, DELETE, TRACE, PATCH, OPTIONS")
                //response.setHeader("Access-Control-Max-Age", "3600")

                //request.getHeader("Access-Control-Request-Headers")
            }
            //response.status = HttpStatus.OK.value()
        }

        if(allowedOrigins && allowedOrigins.contains(origin)) { // request origin is on the white list
            // add CORS access control headers for the given origin
            response.setHeader("Access-Control-Allow-Origin", origin)
            response.setHeader("Access-Control-Allow-Credentials", "true")
            response.status = HttpStatus.OK.value()
            return false
        } else if( !allowedOrigins ) { // no origin; white list
            // add CORS access control headers for all origins
            response.setHeader("Access-Control-Allow-Origin", origin ?: "*")
            response.setHeader("Access-Control-Allow-Credentials", "true")
            response.status = HttpStatus.OK.value()
            return false
        }

        return options
    }
}
