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


@Slf4j
//@CompileStatic
class ContentTypeMarshallerFilter extends OncePerRequestFilter {

    String headerName


    @Override
    protected void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain chain) throws ServletException, IOException {
        //println("#### ContentTypeMarshallerFilter ####")
        HttpServletRequest request = servletRequest as HttpServletRequest
        HttpServletResponse response = servletResponse as HttpServletResponse

        String format = (request?.format)?request.format.toUpperCase():'JSON'
        HashSet formats = ['XML', 'JSON']

        if(!doesContentTypeMatch(request)){
                //println("ContentType ["+request.getContentType()+"] does not match Requested Format ["+request.format.toUpperCase()+"]")
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
                        String xml = request.XML.toString()
                        if(xml!='null') {
                            def xslurper = new XmlSlurper()
                            xslurper.parseText(xml).each() { k, v ->
                                dataParams[k] = v
                            }
                            request.setAttribute('XML', dataParams)
                        }
                        break
                    case 'JSON':
                    default:
                        def json = request.JSON.toString()
                        if(json!='[:]') {
                            def slurper = new JsonSlurperClassic()
                            slurper.parseText(json).each() { k, v ->
                                dataParams[k] = v
                            }
                            request.setAttribute('JSON', dataParams)
                        }
                        break
                }

            }
        } catch (Exception e) {
            response.status = 401
            response.setHeader('ERROR', 'Failed')
            //response.writer.flush()
            return
        }

        chain.doFilter(servletRequest, servletResponse)
    }

    boolean doesContentTypeMatch(HttpServletRequest request){
        String format = (request?.format)?request.format.toUpperCase():'JSON'
        String contentType = request.getContentType()
        try{
            switch(contentType){
                case 'text/xml':
                case 'application/xml':
                    return 'XML'==format
                    break
                case 'text/json':
                case 'application/json':
                default:
                    return 'JSON'==format
                    break
            }
            return false
        }catch(Exception e){
            throw new Exception("[ContentTypeMarshallerFilter :: getContentType] : Exception - full stack trace follows:",e)
        }
    }

}
