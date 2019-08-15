/*
 * Copyright 2013-2019 Beapi.io
 * API Chaining(R) 2019 USPTO
 *
 * Licensed under the MPL-2.0 License;
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.nosegrind.apiframework

import javax.servlet.http.HttpServletRequest
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.grails.plugin.cache.GrailsCacheManager
import grails.plugin.cache.GrailsConcurrentMapCache
import grails.plugin.cache.GrailsValueWrapper
import grails.util.Holders
import grails.util.Metadata
import groovy.json.JsonSlurper
import grails.core.GrailsApplication

/**
 * TestService. 
 */
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
    LinkedHashMap userMockData
    GrailsApplication grailsApplication
    GrailsCacheManager grailsCacheManager


    void initTest(String controller){
        this.controller = controller
        this.cache = getApiCache(controller)
        this.version = this.cache['cacheversion']
        println("### [initTest : ${controller}] ###")
        adminLogin()
        List userRoles = getUserRoles(controller)
        String username = "${controller}test"
        String password = 'testamundo'
        String email = "${controller}test@${controller}test.com"
        String id = createUser(username, password, email, userRoles)
        LinkedHashMap temp = loginUser(username,password)
        this.user = ['id':id,'token':temp.token,'authorities':temp.authorities]
	this.userMockData.username = username
	this.userMockData.email=email
	this.userMockData.enabled=true
	this.userMockData.accountExpired=false
    }

    boolean cleanupTest(){
        if(this.user.id) {
            String id = deleteUser(this.user.id as String)
            if (id == this.user.id) {
                return true
            }
        }
        return false
    }

    private void adminLogin(){
        println("[adminLogin : ${controller}] - logging in")
        try {
            String login = Holders.grailsApplication.config.root.login
            String password = Holders.grailsApplication.config.root.password
            this.testDomain = Holders.grailsApplication.config.environments.test.grails.serverURL
            this.loginUri = Holders.grailsApplication.config.grails.plugin.springsecurity.rest.login.endpointUrl

            LinkedHashMap temp = loginUser(login, password)
            println("[adminLogin : ${controller}] - successfully loggedIn")
            this.adminToken = temp.token
        }catch(Exception e){
            throw new Exception("[TestService : adminLogin] : Admin Login Failed. Please check privileges in 'beapi_api.yml' file :"+e)
        }
    }
    

    private List getNetworkRoles(String controller){
        println("[getNetworkRoles : ${this.controller}] - retrieving network roles")
        try {
            String action = this.cache[this.version]['defaultAction']
            String networkGrp = this.cache[this.version][action]['networkGrp']
            List networkRoles = Holders.grailsApplication.config.apitoolkit.networkRoles."${networkGrp}"

            return networkRoles
        }catch(Exception e){
            throw new Exception("[TestService : getNetworkRoles] : Controller action '${controller}/${action}' does not exist. Check your IO State file and try again :"+e)
        }
    }

    // api call to get all roles
    private List getRoleList(){
        println("[getRoleList : ${this.controller}] - retrieving endpoint roles")
        List roles = []
        def proc = ["curl","-H","Origin: http://localhost","-H","Access-Control-Request-Headers: Origin,X-Requested-With","-H", "Content-Type: application/json", "-H", "Authorization: Bearer ${this.adminToken}","--request","GET", "--verbose",  "${this.testDomain}/${this.appVersion}/role/list"].execute()
        proc.waitFor()
        StringBuffer outputStream = new StringBuffer()
        StringWriter error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)
        String output = outputStream.toString()
        if(output){
            def info = new JsonSlurper().parseText(output)
            info.each(){ k, v ->
                if(v['id']){
                    roles.add(['id':v.id,'name':v.authority])
                }
            }
            return roles
        }else{
            throw new Exception("[TestService : createUser] : Problem creating user:",e)
        }

    }

    private String createUser(String username, String password, String email, List roles) {
        println("[createUser : ${this.controller}] - creating user ${username}")
        String guestdata = "{'username': '${username}','password':'${password}','email':'${email}'}"
        def proc = ["curl","-H","Origin: http://localhost","-H","Access-Control-Request-Headers: Origin,X-Requested-With","-H", "Content-Type: application/json", "-H", "Authorization: Bearer ${this.adminToken}","--request","POST", "--verbose", "-d", "${guestdata}", "${this.testDomain}/${this.appVersion}/person/create"].execute()
        proc.waitFor()
        StringBuffer outputStream = new StringBuffer()
        StringWriter error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)
        String output = outputStream.toString()
        if(output){
            try {
                def info = new JsonSlurper().parseText(output)
                if(createUserRoles(info['id'] as String, roles)){
                    this.userMockData = ['id':info['id'],'version':info['version'],'username':'','email':'','enabled':'','accountExpired':'']
                    return info['id']
                }else{
                    deleteUser(info['id'])
                    throw new Exception("[TestService : createUser] : Problem creating user role:",e)
                }
            }catch(Exception e){
                throw new Exception("[TestService : createUser] : User already exists :"+e)
            }

        }else{
            throw new Exception("[TestService : createUser] : Problem creating user:",e)
        }


    }


    private boolean createUserRoles(String personId, List roles) {
        roles.each { it ->
            String data = "{'personId': '${personId}','roleId':'${it}'}"
            def proc = ["curl", "-H", "Origin: http://localhost", "-H", "Access-Control-Request-Headers: Origin,X-Requested-With", "--request", "POST", "-H", "Content-Type: application/json", "-H", "Authorization: Bearer ${this.adminToken}", "-d", "${data}", "${this.testDomain}/${this.appVersion}/personRole/create"].execute()
            proc.waitFor()
            StringBuffer outputStream = new StringBuffer()
            StringWriter error = new StringWriter()
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

    private LinkedHashMap loginUser(String username, String password){
        try{
            String url = "curl -H 'Content-Type: application/json' -X POST -d '{\"username\":\"${username}\",\"password\":\"${password}\"}' ${this.testDomain}${this.loginUri}"
            def proc = ['bash','-c',url].execute()
            proc.waitFor()
            def info = new JsonSlurper().parseText(proc.text)
            List authorities = info.authorities
            String token = info.access_token
            return ['token':token,'authorities':authorities]
        }catch(Exception e){
            throw new Exception("[TestService : loginUser] : Unable to login user. Please check permissions :"+e)
        }
    }

    private String deleteUser(String personId) {
        def proc = ["curl","-H","Origin: http://localhost","-H","Access-Control-Request-Headers: Origin,X-Requested-With","--request","DELETE", "-H","Content-Type: application/json","-H","Authorization: Bearer ${this.adminToken}","${this.testDomain}/${this.appVersion}/person/delete?id=${personId}"].execute()
        proc.waitFor()
        StringBuffer outputStream = new StringBuffer()
        StringWriter error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)
        String output = outputStream.toString()

        ArrayList stdErr = error.toString().split( '> \n' )
        //println(stdErr)
        //ArrayList response1 = stdErr[0].split("> ")
        //ArrayList response2 = stdErr[1].split("< ")


        def info = new JsonSlurper().parseText(output)
        when:"info is not null"
        assert info!=null
        then:"delete created user"
        assert this.user.id == info.id
    }



    private List getUserRoles(String controller){
        // get list of roles we can install with testUser
        List userRoles = []
        List roles = getNetworkRoles(controller)
        List adminRoles = grailsApplication.config.apitoolkit.admin.roles as List
        List allRoles = getRoleList()
        List finalRoles = roles - roles.intersect( adminRoles )
        allRoles.each() { it ->
            if(finalRoles.contains(it.name)){
                userRoles.add(it.id)
            }
        }
        return userRoles
    }

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
            throw new Exception("[TestService :: getApiCache] : Exception - full stack trace follows:"+e)
        }
    }

    String createRecievesMockData(String action){
        String data = "{"
        this.cache?."${this.version}"?."${action}".receives.each(){ k,v ->
            v.each(){
                data += "'"+it.name+"': '"+it.mockData+"',"
            }
        }
        data += "}"
        return data
    }

    LinkedHashMap createReturnsMockData(String action){
        LinkedHashMap returns = [:]
        this.cache?."${this.version}"?."${action}".receives.each(){ k,v ->
            v.each(){
                returns[it.name] = it.mockData
            }
        }
        return returns
    }

    LinkedHashMap getJSON(String endpoint, String data=null){
        def info
        String url
        if(data) {
            url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${this.user.token}' --request GET -d '${data}' ${endpoint}"
        }else{
            url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${this.user.token}' --request GET ${endpoint}"
        }



        def proc = ['bash','-c',"${url}"].execute()
        proc.waitFor()
        StringBuffer outputStream = new StringBuffer()
        StringWriter error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)

        String output = outputStream.toString()

	if(output){
		info = new JsonSlurper().parseText(output)
		if(info){
		    return info
		}else{
		    throw new Exception("[ERROR] : ${output} : ${error}")
		}
	}else{
		throw new Exception("[ERROR] : No output when calling '${endpoint}': ${error}")
	}
	return info
    }

    void getXML(String token, String data=null, String endpoint){

    }

    void putJSON(String token, String data, String endpoint){

    }

    void putXML(String token, String data, String endpoint){

    }

    void postJSON(String token, String data, String endpoint){

    }

    void postXML(String token, String data, String endpoint){

    }

    void deleteJSON(String token, String data=null, String endpoint){

    }

    void deleteXML(String token, String data=null, String endpoint){

    }
}
