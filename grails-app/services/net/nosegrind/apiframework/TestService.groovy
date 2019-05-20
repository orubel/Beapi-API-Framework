package net.nosegrind.apiframework


import grails.util.Metadata
import groovy.json.JsonSlurper

class TestService {

    String token
    List authorities
    String currentId

    String testDomain
    String appVersion = "v${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
    String loginUri

    String login
    String password = 'testamundo'


    void adminLogin(){
        this.testDomain = Holders.grailsApplication.config.environments.test.grails.serverURL
        this.loginUri = Holders.grailsApplication.config.grails.plugin.springsecurity.rest.login.endpointUrl
        this.token = loginUser(login, password)
    }


    /**
     * Given the IO State name, returns 'NETWORKGRP'
     */
    private void getNetworkGrp(String controller){

    }

    LinkedHashMap getTestUser(String controller){
        adminLogin()
        getNetworkGrp(controller)
        String username = "${controller}test"
        String email = "${controller}test@${controller}test.com"
        String id = createUser(username, 'testamundo', email)
        String token = loginUser(username,password)
        return ['id':id,'token':token]
    }

    private String createUser(String username, String password, String email) {
        String guestdata = "{'username': '${username}','password':'${password}','email':'${email}'}"
        def proc = ["curl","-H","Origin: http://localhost","-H","Access-Control-Request-Headers: Origin,X-Requested-With","-H", "Content-Type: application/json", "-H", "Authorization: Bearer ${this.token}","--request","POST", "--verbose", "-d", "${guestdata}", "${this.testDomain}/${this.appVersion}/person/create"].execute()
        proc.waitFor()
        def outputStream = new StringBuffer()
        def error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)
        String output = outputStream.toString()
        if(output){
            def info = new JsonSlurper().parseText(output)
            if(createUserRole(info['id'])){
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


    private boolean createUserRole(String personId) {

        String data = "{'personId': '${personId}','roleId':'1'}"
        def proc = ["curl","-H","Origin: http://localhost","-H","Access-Control-Request-Headers: Origin,X-Requested-With","--request","POST","-H", "Content-Type: application/json", "-H", "Authorization: Bearer ${this.token}", "-d", "${data}", "${this.testDomain}/${this.appVersion}/personRole/create"].execute()
        proc.waitFor()
        def outputStream = new StringBuffer()
        def error = new StringWriter()
        proc.waitForProcessOutput(outputStream, error)
        String output = outputStream.toString()
        if(output){
            def info = new JsonSlurper().parseText(output)
            if(info['roleId']){
                return true
            }
        }
        return false
    }

    String loginUser(String username, String password){
        String url = "curl -H 'Content-Type: application/json' -X POST -d '{\"username\":\"${username}\",\"password\":\"${password}\"}' ${this.testDomain}${this.loginUri}"
        def proc = ['bash','-c',url].execute()
        proc.waitFor()
        def info = new JsonSlurper().parseText(proc.text)
        return info.access_token
    }

    private String deleteUser(String personId) {
        def proc = ["curl","-H","Origin: http://localhost","-H","Access-Control-Request-Headers: Origin,X-Requested-With","--request","DELETE", "-H","Content-Type: application/json","-H","Authorization: Bearer ${this.token}","${this.testDomain}/${this.appVersion}/person/delete?id=${personId}"].execute()
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

    void getEndPointCall(){}

    void putEndPointCall(){}

    void postEndPointCall(){}

    void deleteEndPointCall(){}

}
