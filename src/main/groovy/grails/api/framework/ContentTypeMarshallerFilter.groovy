package grails.api.framework

import grails.util.Metadata
import groovy.transform.CompileDynamic
import groovy.util.logging.Slf4j

import org.springframework.web.filter.GenericFilterBean
import org.springframework.web.filter.OncePerRequestFilter

import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

// import groovy.json.JsonSlurper
import groovy.json.JsonSlurperClassic
import grails.compiler.GrailsCompileStatic
import groovy.transform.CompileStatic
import java.util.stream.Collectors

/**
 * Used to check proper format was sent for endpoint and to format
 * params using common naming
 * @author Owen Rubel
 */
//@Slf4j
@GrailsCompileStatic
class ContentTypeMarshallerFilter extends OncePerRequestFilter {

    String headerName


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        //println("#### ContentTypeMarshallerFilter ####")
        String batchEntryPoint = "b${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"

        String format = (request?.format)?request.format.toUpperCase():'JSON'
        HashSet formats = new HashSet()
        formats.add('XML')
        formats.add('JSON')


        if(!doesContentTypeMatch(request)){
                response.status = 401
                response.setHeader('ERROR', 'ContentType does not match Requested Format')
                //response.writer.flush()
                return
        }

        try {
            // Init params

            if (formats.contains(format)) {
                LinkedHashMap dataParams = [:]
                switch (format) {
                    case 'XML':
                        dataParams = request.XML  as LinkedHashMap
                        request.setAttribute('XML', dataParams)
                        break
                    case 'JSON':
                    default:
                        dataParams = request.JSON as LinkedHashMap
                        request.setAttribute('JSON', dataParams)
                        break
                }
            }

        } catch (Exception e) {
            response.status = 401
            response.setHeader('ERROR', 'Badly formatted data')
            //response.writer.flush()
            return
        }

        chain.doFilter(request, response)
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

}
