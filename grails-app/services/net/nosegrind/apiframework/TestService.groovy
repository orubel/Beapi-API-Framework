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
import org.grails.web.json.JSONObject

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
 * loop through PUT of all classes with only FOREIGN  keys
 * loop through PUT of all classes with PRIMARY-FOREIGN keys
 * loop through PUT of all classes with only PRIMARY keys
 *
 *
 * DELETE does cleanup and confirms cleanup is possible
 *
 * loop through DELETE of all classes with only FOREIGN  keys (vars designated as fkeys cause it to sort first)
 * loop through DELETE of all classes with PRIMARY-FOREIGN keys (vars designated as fkeys/pkey cause it to sort first)
 * loop through DELETE of all classes with only PRIMARY keys (vars designated as pkeys cause it to sort first)
 *
 *
 */
class TestService {

    String version
    String controller
    String action
    LinkedHashMap cache

    LinkedHashMap admin
    LinkedHashMap user

    //List testLoadOrder = Holders.grailsApplication.config.apitoolkit.testLoadOrder
    String testDomain = Holders.grailsApplication.config.environments.test.grails.serverURL
    String appVersion = "v${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
    String loginUri
    LinkedHashMap userMockData
    GrailsApplication grailsApplication
    GrailsCacheManager grailsCacheManager
    LinkedHashMap apiObject = [:]


    void initTest(){
        adminLogin()
        initLoop()
    }

    void initLoop(){
        println("### [initTest] ###")
        List testLoadOrder = System.getProperty("testLoadOrder").split(',')

        ['POST','GET','PUT'].each() { method ->

            testLoadOrder.each(){ controller ->

                this.cache = getApiCache(controller)


                // TODO: test to see if cache exists for each controller name

                if(!this.apiObject[controller]) {
                    this.apiObject[controller] = [:]
                }

                if (cache) {
                    this.version = this.cache['currentStable']['value']
                    if (cache[version]['testOrder']) {


                        if(cache[version]['testOrder']) {
                            cache[version]['testOrder'].each() { it ->


                                if (!this.apiObject[controller][it]) {
                                    this.apiObject[controller][it] = [:]
                                }
                                if (!this.apiObject[controller]['values']) {
                                    this.apiObject[controller]['values'] = [:]
                                }

                                if (cache[version][it]['method'] == method) {

                                    LinkedHashMap fkeys
                                    if (cache[version][it]['fkeys']) {

                                        fkeys = getFkeys(cache[version][it]['fkeys'])
                                    }

                                    /*
                                if(method=='DELETE'){
                                    check for fkeys and do those first
                                */


                                    switch (method) {
                                        case 'POST':
                                            List endpoint = [this.testDomain,this.appVersion,controller,it.toString()]
                                            this.apiObject[controller][it]['recieves'] = getMockdata(cache[version][it]['receives'], this.admin.authorities)
                                            this.apiObject[controller][it]['returns'] = getMockdata(cache[version][it]['returns'], this.admin.authorities)
                                            String receivesData = createDataAsJSON(this.apiObject[controller][it]['recieves'], controller, fkeys)
                                            LinkedHashMap returnsData = createReturnsData(this.apiObject[controller][it]['returns'],controller, fkeys)

                                            //println("${controller}/${it} is POST")
                                            println(cache['values'])
                                            def temp = cache['values']
                                            println(temp.getClass())
                                            LinkedHashMap output = postJSON(endpoint, this.admin.token, returnsData, receivesData,cache['values'])
                                            output.each() { k, v ->
                                                this.apiObject[controller]['values'][k] = v
                                            }
                                            break
                                        case 'GET':
                                            List endpoint = [this.testDomain,this.appVersion,controller,it.toString()]
                                            this.apiObject[controller][it]['recieves'] = getMockdata(cache[version][it]['receives'], this.admin.authorities)
                                            this.apiObject[controller][it]['returns'] = getMockdata(cache[version][it]['returns'], this.admin.authorities)
                                            String receivesData = createDataAsJSON(this.apiObject[controller][it]['recieves'], controller, fkeys)
                                            LinkedHashMap returnsData = createReturnsData(this.apiObject[controller][it]['returns'],controller, fkeys)

                                            //println("${controller}/${it} is GET")
                                            println(cache['values'])
                                            def temp = cache['values']
                                            println(temp.getClass())
                                            LinkedHashMap output = getJSON(endpoint, this.admin.token, returnsData, receivesData,cache['values'])
                                            output.each() { k, v ->
                                                this.apiObject[controller]['values'][k] = v
                                            }
                                            break
                                        case 'PUT':
                                            List endpoint = [this.testDomain,this.appVersion,controller,it.toString()]
                                            this.apiObject[controller][it]['recieves'] = getMockdata(cache[version][it]['receives'], this.admin.authorities)
                                            this.apiObject[controller][it]['returns'] = getMockdata(cache[version][it]['returns'], this.admin.authorities)
                                            String receivesData = createDataAsJSON(this.apiObject[controller][it]['recieves'], controller, fkeys)
                                            LinkedHashMap returnsData = createReturnsData(this.apiObject[controller][it]['returns'],controller, fkeys)

                                            //println("${controller}/${it} is PUT")
                                            println(cache['values'])
                                            def temp = cache['values']
                                            println(temp.getClass())
                                            LinkedHashMap output = putJSON(endpoint, this.admin.token, returnsData, receivesData,cache['values'])
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

                    }
                }
            }
        }

        cleanupTest(testLoadOrder)
    }

    private LinkedHashMap getFkeys(LinkedHashSet fkeys){
        LinkedHashMap keys = [:]
        fkeys.each() { it ->
            it.each(){ k,v ->
                String tempController = v.uncapitalize()
                if(this.apiObject[tempController]) {
                    keys[k] = this.apiObject[tempController]['values']['id']
                }
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


    private String createDataAsJSON(LinkedHashMap mockdata, String controller, LinkedHashMap fkeys){
        try{
            String data = "{"
            mockdata.each(){ k, v ->
                    String key = k
                    if(fkeys){
                        if (fkeys[k]){
                            data += "'${key}': '" + fkeys[k] + "',"
                        }
                    }else{
                        if(this.apiObject[controller]['values'][k]){
                            data += "'${key}': '" + this.apiObject[controller]['values'][k] + "',"
                        }else if(v){
                            data += "'${key}': '" + v + "',"
                        }
                    }
            }
            data += "}"
            return data
        }catch(Exception e){
            throw new Exception("[TestService : createDataAsJSON] : ERROR- Exception follows : ",e)
        }
    }

    private LinkedHashMap createReturnsData(LinkedHashMap returnsData, String controller, LinkedHashMap fkeys){
        try{
            LinkedHashMap data = [:]
            returnsData.each(){ k, v ->
                String key = k
                if(fkeys){
                    if (fkeys[k]){
                        data[key] = fkeys[k]
                    }
                }else{
                    if(this.apiObject[controller]['values'][k]){
                        data[key] = this.apiObject[controller]['values'][k]
                    }else if(v){
                        data[key] = v
                    }
                }
            }

            return data
        }catch(Exception e){
            throw new Exception("[TestService : createReturnsData] : ERROR- Exception follows : ",e)
        }
    }

    boolean cleanupTest(List testLoadOrder){
        println("### [CleanupTest and Exit] ###")

        testLoadOrder = testLoadOrder.reverse()
        ['DELETE'].each() { method ->
            testLoadOrder.each(){ controller ->
                this.cache = getApiCache(controller)


                if(!this.apiObject[controller]) {
                    this.apiObject[controller] = [:]
                }
                if (cache) {
                    this.version = this.cache['currentStable']['value']
                    if (cache[version]['testOrder']) {


                        List newTestOrder = cache[version]['testOrder'].reverse()

                        newTestOrder.each() { it ->


                            if (!this.apiObject[controller][it]) {
                                this.apiObject[controller][it] = [:]
                            }
                            if (!this.apiObject[controller]['values']) {
                                this.apiObject[controller]['values'] = [:]
                            }

                            if (cache[version][it]['method'] == method) {
                                LinkedHashMap fkeys
                                if (cache[version][it]['fkeys']) {
                                    fkeys = getFkeys(cache[version][it]['fkeys'])
                                }


                                switch (method) {
                                    case 'DELETE':
                                        List endpoint = [this.testDomain,this.appVersion,controller,it.toString()]
                                        this.apiObject[controller][it]['recieves'] = getMockdata(cache[version][it]['receives'], this.admin.authorities)
                                        this.apiObject[controller][it]['returns'] = getMockdata(cache[version][it]['returns'], this.admin.authorities)
                                        String receivesData = createDataAsJSON(this.apiObject[controller][it]['recieves'], controller, fkeys)
                                        LinkedHashMap returnsData = createReturnsData(this.apiObject[controller][it]['returns'],controller, fkeys)

                                        LinkedHashMap output = deleteJSON(endpoint, this.admin.token, returnsData, receivesData,cache['values']['pkey'],cache['values']['fkeys'])
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
                }
            }
        }
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

    private LinkedHashMap getJSON(List endpoint, String token, LinkedHashMap returnsData, String receivesData=null, JSONObject values){
        String newEndpoint = "${endpoint[0]}/${endpoint[1]}/${endpoint[2]}/${endpoint[3]}"
        String controller = endpoint[2]
        String action = endpoint[3]

        //def recieves = new JsonSlurper().parseText(receivesData.replaceAll("'","\""))
        //def returns = this.apiObject[controller][action]['returns']

        println("[GET TEST for ${newEndpoint}] - starting")
        def info
        String url
        if(receivesData) {
            url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${token}' --request GET -d '${receivesData}' ${newEndpoint}"
        }else{
            url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${token}' --request GET ${newEndpoint}"
        }

        def proc = ['bash','-c',"${url}"].execute()
        proc.waitFor()
        StringBuffer outputStream = new StringBuffer()
        StringWriter error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)

        String output = outputStream.toString()

        if(output){
            info = new JsonSlurper().parseText(output)
        }else{
            throw new Exception("[TestService: getJSON] ERROR : No output when calling '${newEndpoint}': ${error}")
        }


        // TODO : regex to see if action contains string '[l|L]ist'
        if(action=='list'){
            Class clazz = grailsApplication.domainClasses.find { it.clazz.simpleName == controller.capitalize() }.clazz
            println("LISTTEST: ${info.size()} == ${clazz.count()}")
            assert info.size()==clazz.count()
        }else {
            info.each() { k, v ->
                if (!['PRIMARY', 'FOREIGN'].contains(values[k]['key']) && k != 'version') {
                    assert returnsData[k].toString() == info[k].toString()
                }
            }
        }


        println("[GET TEST for ${newEndpoint}] - ending")
	    return info
    }

    // TODO
    void getXML(String endpoint, String token, LinkedHashMap returnsData, String receivesData=null){

    }

    private LinkedHashMap putJSON(List endpoint, String token, LinkedHashMap returnsData, String receivesData, JSONObject values){
        String newEndpoint = "${endpoint[0]}/${endpoint[1]}/${endpoint[2]}/${endpoint[3]}"
        String controller = endpoint[2]
        String action = endpoint[3]

        def recieves = new JsonSlurper().parseText(receivesData.replaceAll("'","\""))
        def returns = this.apiObject[controller][action]['returns']

        println("[PUT TEST for ${newEndpoint}] - starting")

        // TODO: check for differences from recieves and 'info'

        def info
        String url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${token}' --request PUT -d '${receivesData}' ${newEndpoint}"
println(url)
        def proc = ['bash','-c',"${url}"].execute()
        proc.waitFor()
        StringBuffer outputStream = new StringBuffer()
        StringWriter error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)

        String output = outputStream.toString()

        if(output){
            info = new JsonSlurper().parseText(output)
        }else{
            throw new Exception("[TestService: putJSON] ERROR : No output when calling '${newEndpoint}': ${error}")
        }

        println(returnsData)
        println(info)

        if(info.version){
            assert info.version.toInteger()==((returnsData.version.toInteger())+1)
        }

        List returnedKeys = new ArrayList(info.keySet())
        List expectedKeys = new ArrayList(returnsData.keySet())

        assert returnedKeys.size() == expectedKeys.intersect(returnedKeys).size()


        println("[PUT TEST for ${newEndpoint}] - ending")
        return info
    }

    // TODO
    void putXML(String endpoint, String token, LinkedHashMap returnsData, String receivesData){

    }

    private LinkedHashMap postJSON(List endpoint, String token, LinkedHashMap returnsData, String receivesData, JSONObject values){
        String newEndpoint = "${endpoint[0]}/${endpoint[1]}/${endpoint[2]}/${endpoint[3]}"
        String controller = endpoint[2]
        String action = endpoint[3]

        def recieves = new JsonSlurper().parseText(receivesData.replaceAll("'","\""))
        def returns = this.apiObject[controller][action]['returns']

        println("[POST TEST for ${newEndpoint}] - starting")
        def info
        String url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${token}' --request POST -d '${receivesData}' ${newEndpoint}"
        println(url)
        def proc = ['bash','-c',"${url}"].execute()
        proc.waitFor()
        StringBuffer outputStream = new StringBuffer()
        StringWriter error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)

        String output = outputStream.toString()

        if(output){
            info = new JsonSlurper().parseText(output)
        }else{
            throw new Exception("[TestService: postJSON] ERROR : No output when calling '${newEndpoint}': ${error}")
        }

        println(returnsData)
        println(info)
        println(recieves)
        println(returns)

        info.each(){ k,v ->
            if (!['PRIMARY', 'FOREIGN'].contains(values[k]['key']) && k!='version') {
                println("key:" + values[k]['key'])
                assert returnsData[k].toString() == info[k].toString()
            }
        }

        println("[POST TEST for ${newEndpoint}] - ending")
        return info
    }

    // TODO
    void postXML(String endpoint, String token, LinkedHashMap returnsData, String receivesData){

    }

    private LinkedHashMap deleteJSON(List endpoint, String token, LinkedHashMap returnsData, String receivesData, JSONObject values){
        String newEndpoint = "${endpoint[0]}/${endpoint[1]}/${endpoint[2]}/${endpoint[3]}"
        String controller = endpoint[2]
        String action = endpoint[3]

        def recieves = new JsonSlurper().parseText(receivesData.replaceAll("'","\""))
        def returns = this.apiObject[controller][action]['returns']

        println("[DELETE TEST for ${newEndpoint}] - starting")
        def info
        String url = "curl -v -H 'Content-Type: application/json' -H 'Authorization: Bearer ${token}' --request DELETE -d '${receivesData}' ${newEndpoint}"
        println(url)
        def proc = ['bash','-c',"${url}"].execute()
        proc.waitFor()
        StringBuffer outputStream = new StringBuffer()
        StringWriter error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)

        String output = outputStream.toString()

        if(output){
            info = new JsonSlurper().parseText(output)
        }else{
            throw new Exception("[TestService: deleteJSON] ERROR : No output when calling '${newEndpoint}': ${error}")
        }

        println(returnsData)
        println(info)
        println(recieves)
        println(returns)

        info.each(){ k,v ->
            if (!['PRIMARY', 'FOREIGN'].contains(values[k]['key']) && k!='version') {
                println("type:" + values[k]['key'])
                assert returnsData[k].toString() == info[k].toString()
            }
        }


        println("[DELETE TEST for ${newEndpoint}] - ending")
        return info
    }

    // TODO
    void deleteXML(String endpoint, String token, LinkedHashMap returnsData, String receivesData){

    }

    boolean isNumeric(String strNum) {
        try {
            double d = Double.parseDouble(strNum)
        } catch (NumberFormatException | NullPointerException nfe) {
            return false
        }
        return true
    }
}
