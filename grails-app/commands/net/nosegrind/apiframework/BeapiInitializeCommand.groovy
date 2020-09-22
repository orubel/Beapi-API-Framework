package net.nosegrind.apiframework

import grails.dev.commands.*
import grails.core.GrailsApplication


class BeapiInitializeCommand implements ApplicationCommand {


	@Autowired
	GrailsApplication grailsApplication


    //List reservedNames = ['hook','iostate','apidoc']

    boolean handle(ExecutionContext ctx) {
        def test = grailsApplication.config.environments.test
        def dev = grailsApplication.config.environments.development
        def prod = grailsApplication.config.environments.production
        def server = grailsApplication.config.server

        // what user will this run under
        // what is their home directory
        // what is the ssh folder in their home directory

        def intro = """
#################################################################
###  -WELCOME TO THE BEAPI FRAMEWORK INITIALIZATION & SETUP-  ###
###                                                           ###
###   Here we will take you step by step through the setup    ###
###   process to simply the initialization of your api frame- ###
###   work and make it as painless a process as possible.     ###
#################################################################
"""
        println intro

        createSSH()

    }

    void createSSH(){

        boolean correct = false
        String path
        while(correct==false){
            path = ''
            def homedir = System.console().readLine '>>> Please enter the home directory of the user this will run under? (DEFAULT: /home/.beapi)'
            def matches = (homedir =~ /(\/[a-zA-Z0-9_-]+)/)
            matches.each(){
                path += it
            }

            def choice = System.console().readLine ">>> Is this correct? [" + path + "] (Y/N)"
            while(!['y','Y','n','N'].contains(choice)){
                println('### CHOICE NOT FOUND. PLEASE TRY AGAIN ###')
                choice = System.console().readLine ">>> Is this correct? [" + path + "] (Y/N)"
            }
            correct = true
        }
        // create key in target dir : path
        String cmd = "keytool -genkey -keyalg RSA -alias tomcat -keystore beapi.jks -validity 365 -keysize 2048"

        def proc = ['bash','-c',cmd].execute()
        proc.waitFor()


        //println "Hello $username"
    }


    def mvBeapiFiles(){

    }

    def mvIoStateFiles(){

    }


}
