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


import org.grails.plugin.cache.GrailsCacheManager
import grails.plugin.cache.GrailsConcurrentMapCache
import grails.plugin.cache.GrailsValueWrapper
import grails.util.Holders
import grails.util.Metadata
import groovy.json.JsonSlurper
import grails.core.GrailsApplication

/**
 * TestService.
 *
 * Future implementations: rather than using TESTORDER in IO State:
 *
 * POST/CREATE creates initial ID for tuple
 *
 * loop through POST of all classes with only PRIMARY keys
 * loop through POST of all classes with PRIMARY-FOREIGN keys
 * loop through POST of all classes with only FOREIGN  keys
 *
 *
 * GET confirms tuple/ID was created
 *
 * loop through GET of all classes with only PRIMARY keys
 * loop through GET of all classes with PRIMARY-FOREIGN keys
 * loop through GET of all classes with only FOREIGN  keys
 *
 *
 * PUT/EDIT confirms editing of tuple is possible
 *
 * loop through PUT of all classes with only PRIMARY keys
 * loop through PUT of all classes with PRIMARY-FOREIGN keys
 * loop through PUT of all classes with only FOREIGN  keys
 *
 *
 * DELETE does cleanup and confirms cleanup is possible
 *
 * loop through DELETE of all classes with only PRIMARY keys
 * loop through DELETE of all classes with PRIMARY-FOREIGN keys
 * loop through DELETE of all classes with only FOREIGN  keys
 *
 */
class TestService {

    String version
    String controller
    String action
    LinkedHashMap cache

    LinkedHashMap admin
    LinkedHashMap user

    List testLoadOrder = Holders.grailsApplication.config.apitoolkit.testLoadOrder
    String testDomain = Holders.grailsApplication.config.environments.test.grails.serverURL
    String appVersion = "v${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
    String loginUri
    LinkedHashMap userMockData
    GrailsApplication grailsApplication
    GrailsCacheManager grailsCacheManager
    LinkedHashMap apiObject = [:]


    void initTest(){
        println("### [initTest Users] ###")
        adminLogin()
        // create recieves mockdata for each controller/action

        // create returns mockdata for each controller/action

        // create JSON from recieves mockdata

        // create call order
        initLoop()
    }

    void initLoop(){
        println("### [initTest] ###")
        // Controllers Loop - this needs to be moved into TEST below
        //grailsApplication.controllerClasses.each { controllerArtefact ->

        // setting call order
        ['POST','GET','PUT','DELETE'].each() { method ->
            testLoadOrder.each() { controller ->
                findDomainClass(controller.capitalize())
                this.apiObject[controller] = [:]
                this.controller = controller

                this.cache = getApiCache(controller)
                if (cache) {

                    this.version = this.cache['currentStable']['value']
                    if (cache[version]['testOrder']) {
                        cache[version]['testOrder'].each() {

                            this.apiObject[controller][it] = [:]
                            this.apiObject[controller]['values'] = [:]

                            this.apiObject[controller]['pkey'] = [:]

                            if(cache[version][it]['pkey']) {
                                this.apiObject[controller]['pkey']['id'] = ""
                            }

                            this.apiObject[controller]['fkeys'] = [:]
                            this.apiObject[controller]['fkeys'] = getFkeys(cache[version][it]['fkeys'] as LinkedHashMap)


                            if(cache[version][it]['method']==method) {
                                String endpoint = "${this.testDomain}/${this.appVersion}/${controller}/${it}"

                                this.apiObject[controller][it]['recieves'] = getMockdata(cache[version][it]['receives'],this.admin.authorities)
                                this.apiObject[controller][it]['returns'] = getMockdata(cache[version][it]['returns'],this.admin.authorities)
                                String receivesData = createDataAsJSON(this.apiObject[controller][it]['recieves'],controller)
                                LinkedHashMap returnsData = this.apiObject[controller][it]['returns']
                                switch (method) {
                                    case 'POST':
                                        println("${controller}/${it} is POST")
                                        LinkedHashMap output = postJSON(endpoint, this.admin.token, returnsData, receivesData)
                                        output.each() { k, v ->
                                            this.apiObject[controller]['values'][k] = v
                                        }
                                        break
                                    case 'GET':
                                        println("${controller}/${it} is GET")
                                        LinkedHashMap output = getJSON(endpoint, this.admin.token, returnsData, receivesData)
                                        output.each() { k, v ->
                                            this.apiObject[controller]['values'][k] = v
                                        }
                                        break

                                    case 'PUT':
                                        println("${controller}/${it} is PUT")
                                        LinkedHashMap output = postJSON(endpoint, this.admin.token, returnsData, receivesData)
                                        output.each() { k, v ->
                                            this.apiObject[controller]['values'][k] = v
                                        }
                                        break
                                    case 'DELETE':
                                        println("${controller}/${it} is DELETE")
                                        LinkedHashMap output = deleteJSON(endpoint, this.admin.token, returnsData, receivesData)
                                        output.each() { k, v ->
                                            this.apiObject[controller]['values'][k] = v
                                        }
                                        break
                                    default:
                                        //println("ERROR")
                                        break

                                }
                            }
                        }

                    }
                    //cleanupTest()
                }
            }
        }
    }


    private boolean findDomainClass(String className){
        try{
            Class clazz = grailsApplication.domainClasses.find { it.clazz.simpleName == className }.clazz
            if(clazz){
                return true
            }else{
                return false
            }
        }catch(Exception e){
            throw new Exception("[TestService : findDomainClass] : ERROR - No Domain Class of the name '${className}' exists. Please check spelling/case for 'testLoadOrder' in beapi_api.yml and try again.",e)
        }
    }

    private LinkedHashMap getFkeys(LinkedHashMap fkeys){
        // if values, return fkey value
        // else return mockdata value for key (or throw error as you are
        // trying to insert child row BEFORE parent row exists)
        LinkedHashMap keys = [:]
        fkeys.each() { k, v ->
            if(this.apiObject[v]) {
                keys[v] = this.apiObject[v]['id']
            }
        }
        return keys
    }

    private LinkedHashMap getMockdata(LinkedHashMap mockdata, List authorities){
        try {
            LinkedHashMap output = [:]
            if (mockdata['permitAll']) {
                mockdata['permitAll'].each() { it ->

                    output[it.name] = it.mockData
                }
            }
            authorities.each() {
                if (mockdata[it]) {
                    mockdata[it].each() { it2 ->
                        output[it2.name] = it2.mockData
                    }
                }
            }
            return output
        }catch(Exception e){
            throw new Exception("[TestService : getMockdata] : ERROR- Exception follows : ",e)
        }
    }


    private String createDataAsJSON(LinkedHashMap mockdata, String controller){
        try{
            String data = "{"
            mockdata.each(){ k, v ->
                    if(v) {
                        data += "'" + k + "': '" + v + "',"
                    }else{
                        if(this.apiObject[controller]['values'][k]){
                            data += "'" + k + "': '" + this.apiObject[controller]['values'][k] + "',"
                        }
                    }
            }
            data += "}"
            return data
        }catch(Exception e){
            throw new Exception("[TestService : createDataAsJSON] : ERROR- Exception follows : ",e)
        }
    }

    boolean cleanupTest(){
        println("### [CleanupTest and Exit] ###")
        /*
        if(this.user.id) {
            String id = deleteUser(this.user.id as String)
            if (id == this.user.id) {
                return true
            }
        }
        */
        return false
    }

    private void adminLogin(){
        println("[adminLogin] - logging in")
        try {
            String login = Holders.grailsApplication.config.root.login
            String password = Holders.grailsApplication.config.root.password
            this.testDomain = Holders.grailsApplication.config.environments.test.grails.serverURL
            this.loginUri = Holders.grailsApplication.config.grails.plugin.springsecurity.rest.login.endpointUrl

            LinkedHashMap temp = loginUser(login, password)
            println("[adminLogin] - successfully loggedIn")
            this.admin = ['token':temp.token,'authorities':temp.authorities]
        }catch(Exception e){
            throw new Exception("[TestService : adminLogin] : Admin Login Failed. Please check privileges in 'beapi_api.yml' file :"+e)
        }
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
            println("[TestService : loginUser] : Unable to login user. Please check permissions :"+e)
            throw new Exception("[TestService : loginUser] : Unable to login user. Please check permissions :"+e)
        }
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
            throw new Exception("[TestService : getApiCache] : Exception - full stack trace follows:"+e)
        }
    }

    private LinkedHashMap getJSON(String endpoint, String token, LinkedHashMap returnsData, String receivesData=null){
        def info
        String url
        if(receivesData) {
            url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${token}' --request GET -d '${receivesData}' ${endpoint}"
        }else{
            url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${token}' --request GET ${endpoint}"
        }
println(url)
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
                //throw new Exception("[ERROR] : ${output} : ${error}")
                //println("[OUTPUT] : ${output} [END OUTPUT]")
                //println("[ERROR] : ${error} [END ERROR]")
                ArrayList stdErr = error.toString().split( '> \n' )
                //ArrayList response1 = stdErr[0].split("> ")
                ArrayList response2 = stdErr[1].split("< ")
                println("[response2] ${response2} [end response2]")
            }
        }else{
            throw new Exception("[TestService: getJSON] ERROR : No output when calling '${endpoint}': ${error}")
        }
	    return info
    }

    void getXML(String endpoint, String token, LinkedHashMap returnsData, String receivesData=null){

    }

    private LinkedHashMap putJSON(String endpoint, String token, LinkedHashMap returnsData, String receivesData){
        def info
        String url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${token}' --request PUT -d '${receivesData}' ${endpoint}"
        println(url)
        def proc = ['bash','-c',"${url}"].execute()
        proc.waitFor()
        StringBuffer outputStream = new StringBuffer()
        StringWriter error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)

        String output = outputStream.toString()

        if(error) {
            ArrayList stdErr = error.toString().split( '> \n' )
            ArrayList response2 = stdErr[1].split("< ")
            println("[response2] ${response2} [end response2]")
        }else{
            info = new JsonSlurper().parseText(output)
            if(info){
                return info
            }else{
                throw new Exception("[TestService: postJSON] ERROR : No output when calling '${endpoint}': ${error}")
            }
        }

        return info
    }

    void putXML(String endpoint, String token, LinkedHashMap returnsData, String receivesData){

    }

    private LinkedHashMap postJSON(String endpoint, String token, LinkedHashMap returnsData, String receivesData){
        def info
        String url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${token}' --request POST -d '${receivesData}' ${endpoint}"
        println(url)
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
                //throw new Exception("[ERROR] : ${output} : ${error}")
                //println("[OUTPUT] : ${output} [END OUTPUT]")
                //println("[ERROR] : ${error} [END ERROR]")
                ArrayList stdErr = error.toString().split( '> \n' )
                //ArrayList response1 = stdErr[0].split("> ")
                ArrayList response2 = stdErr[1].split("< ")
                println("[response2] ${response2} [end response2]")
            }
        }else{
            throw new Exception("[TestService: postJSON] ERROR : No output when calling '${endpoint}': ${error}")
        }
        return info
    }

    void postXML(String endpoint, String token, LinkedHashMap returnsData, String receivesData){

    }

    private LinkedHashMap deleteJSON(String endpoint, String token, LinkedHashMap returnsData, String receivesData){
        def info
        String url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${token}' --request DELETE -d '${receivesData}' ${endpoint}"
        println(url)
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
                //throw new Exception("[ERROR] : ${output} : ${error}")
                //println("[OUTPUT] : ${output} [END OUTPUT]")
                //println("[ERROR] : ${error} [END ERROR]")
                ArrayList stdErr = error.toString().split( '> \n' )
                //ArrayList response1 = stdErr[0].split("> ")
                ArrayList response2 = stdErr[1].split("< ")
                println("[response2] ${response2} [end response2]")
            }
        }else{
            throw new Exception("[TestService: postJSON] ERROR : No output when calling '${endpoint}': ${error}")
        }
        return info
    }

    void deleteXML(String endpoint, String token, LinkedHashMap returnsData, String receivesData){

    }
}
