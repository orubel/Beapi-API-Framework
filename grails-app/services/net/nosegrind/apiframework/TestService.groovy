package net.nosegrind.apiframework

import javax.servlet.http.HttpServletRequest
import org.springframework.web.context.request.ServletRequestAttributes

import org.grails.plugin.cache.GrailsCacheManager
import grails.plugin.cache.GrailsConcurrentMapCache
import grails.plugin.cache.GrailsValueWrapper

import grails.util.Metadata
import groovy.json.JsonSlurper

class TestService {

    String version
    String controller
    String action
    LinkedHashMap cache

    String adminToken
    LinkedHashMap user
    String testDomain
    String appVersion = "v${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
    String loginUri
    String login


    GrailsCacheManager grailsCacheManager

    private HttpServletRequest getRequest(){
        HttpServletRequest request = ((ServletRequestAttributes) RCH.currentRequestAttributes()).getRequest()
        return request
    }


    void initTest(String controller){
        this.controller = controller
        this.cache = getApiCache(controller)
        adminLogin()
        List userRoles = getUserRoles(controller)
        String username = "${controller}test"
        String password = 'testamundo'
        String email = "${controller}test@${controller}test.com"
        String id = createUser(username, password, email, roles)
        String token = loginUser(username,password)
        this.user = ['id':id,'token':token]
    }

    boolean cleanupTest(){
        String id = deleteUser(this.user.id as String)
        if(id==this.user.id){
            return true
        }
        return false
    }

    private void adminLogin(){
        this.testDomain = Holders.grailsApplication.config.environments.test.grails.serverURL
        this.loginUri = Holders.grailsApplication.config.grails.plugin.springsecurity.rest.login.endpointUrl
        this.adminToken = loginUser(login, password)
    }



    private List getNetworkRoles(String controller){
        HttpServletRequest request = getRequest()
        String actualUri = request.requestURI - request.contextPath
        String[] params = actualUri.split('/')
        String[] temp = ((String)params[1]).split('-')
        this.version = (temp.size()>1) ? temp[1].toString() : '1'
        this.action = params[3]

        String networkGrp = this.cache[this.version][this.action]['networkGrp']
        List networkRoles = Holders.grailsApplication.config.apitoolkit.networkRoles."${networkGrp}"
        return networkRoles
    }

    // api call to get all roles
    private List getRoleList(){
        def proc = ["curl","-H","Origin: http://localhost","-H","Access-Control-Request-Headers: Origin,X-Requested-With","-H", "Content-Type: application/json", "-H", "Authorization: Bearer ${this.adminToken}","--request","GET", "--verbose",  "${this.testDomain}/${this.appVersion}/role/list"].execute()
        proc.waitFor()
        def outputStream = new StringBuffer()
        def error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)
        String output = outputStream.toString()
        if(output){
            def info = new JsonSlurper().parseText(output)
            println("### GETrOLElIST: "+info)
        }else{
            println(error)
            throw new Exception("[TestService : createUser] : Problem creating user:",e)
        }

    }


    private String createUser(String username, String password, String email, List roles) {
        String guestdata = "{'username': '${username}','password':'${password}','email':'${email}'}"
        def proc = ["curl","-H","Origin: http://localhost","-H","Access-Control-Request-Headers: Origin,X-Requested-With","-H", "Content-Type: application/json", "-H", "Authorization: Bearer ${this.adminToken}","--request","POST", "--verbose", "-d", "${guestdata}", "${this.testDomain}/${this.appVersion}/person/create"].execute()
        proc.waitFor()
        def outputStream = new StringBuffer()
        def error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)
        String output = outputStream.toString()
        if(output){
            def info = new JsonSlurper().parseText(output)
            if(createUserRoles(info['id'], roles)){
                return info['id']
            }else{
                deleteUser(info['id'])
                throw new Exception("[TestService : createUser] : Problem creating user role:",e)
            }
        }else{
            println(error)
            throw new Exception("[TestService : createUser] : Problem creating user:",e)
        }


    }


    private boolean createUserRoles(String personId, List roles) {
        roles.each { it ->
            String data = "{'personId': '${personId}','roleId':'${it}'}"
            def proc = ["curl", "-H", "Origin: http://localhost", "-H", "Access-Control-Request-Headers: Origin,X-Requested-With", "--request", "POST", "-H", "Content-Type: application/json", "-H", "Authorization: Bearer ${this.adminToken}", "-d", "${data}", "${this.testDomain}/${this.appVersion}/personRole/create"].execute()
            proc.waitFor()
            def outputStream = new StringBuffer()
            def error = new StringWriter()
            proc.waitForProcessOutput(outputStream, error)
            String output = outputStream.toString()
            if(output) {
                def info = new JsonSlurper().parseText(output)
                if (!info['roleId']) {
                  return false
                }
            }else{
                return false
            }
        }
        return true
    }

    private String loginUser(String username, String password){
        String url = "curl -H 'Content-Type: application/json' -X POST -d '{\"username\":\"${username}\",\"password\":\"${password}\"}' ${this.testDomain}${this.loginUri}"
        def proc = ['bash','-c',url].execute()
        proc.waitFor()
        def info = new JsonSlurper().parseText(proc.text)
        return info.access_token
    }

    private String deleteUser(String personId) {
        def proc = ["curl","-H","Origin: http://localhost","-H","Access-Control-Request-Headers: Origin,X-Requested-With","--request","DELETE", "-H","Content-Type: application/json","-H","Authorization: Bearer ${this.adminToken}","${this.testDomain}/${this.appVersion}/person/delete?id=${personId}"].execute()
        proc.waitFor()
        def outputStream = new StringBuffer()
        def error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)
        String output = outputStream.toString()

        ArrayList stdErr = error.toString().split( '> \n' )
        println(stdErr)
        //ArrayList response1 = stdErr[0].split("> ")
        //ArrayList response2 = stdErr[1].split("< ")


        def info = new JsonSlurper().parseText(output)
        when:"info is not null"
        assert info!=null
        then:"delete created user"
        assert this.guestId == info.id
    }



    private List getUserRoles(String controller){
        // get list of roles we can install with testUser
        List userRoles = []
        List roles = getNetworkRoles(controller)
        LinkedHashMap allRoles = getRoleList()
        allRoles.each() { k,v ->
            if(roles.contains(v)){
                userRoles.add(k)
            }
        }
        return userRoles
    }

    boolean getApiCall(){}

    boolean  putApiCall(){}

    boolean  postApiCall(){}

    boolean deleteApiCall(){}

    LinkedHashMap getApiCache(String controllername){
        try{
            GrailsConcurrentMapCache temp = grailsCacheManager?.getCache('ApiCache')
            List cacheNames=temp.getAllKeys() as List
            GrailsValueWrapper cache
            cacheNames.each() { it2 ->
                if (it2.simpleKey == controllername) {
                    cache = temp.get(it2)
                }
            }


            if(cache?.get()){
                return cache.get() as LinkedHashMap
            }else{
                return [:]
            }

        }catch(Exception e){
            throw new Exception("[TestService :: getApiCache] : Exception - full stack trace follows:",e)
        }
    }

}
