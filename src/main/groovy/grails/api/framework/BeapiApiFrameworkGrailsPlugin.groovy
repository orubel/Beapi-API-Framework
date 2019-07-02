package grails.api.framework

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.SecurityFilterPosition
import net.nosegrind.apiframework.ApiDescriptor
import net.nosegrind.apiframework.ApiParams
import net.nosegrind.apiframework.ParamsDescriptor
import groovy.util.AntBuilder

import javax.servlet.ServletRegistration
//import java.util.Collections
//import org.grails.web.servlet.mvc.GrailsDispatcherServlet

import grails.plugins.*

import grails.util.GrailsNameUtils
import grails.util.Metadata
import grails.util.BuildSettings
import grails.util.Holders

import grails.converters.JSON
import org.grails.web.json.JSONObject

import org.springframework.context.ApplicationContext

import java.net.URL

import java.util.jar.JarFile
import java.util.jar.JarException
import java.util.jar.JarEntry

import grails.util.Metadata

/**
 * Plugin class for BeAPI API Framework
 * @author Owen Rubel
 */
class BeapiApiFrameworkGrailsPlugin extends Plugin{
	def version = '1.1'
    def grailsVersion = '3.2.1 > *'
    def title = 'BeAPI Api Framework' // Headline display name of the plugin
	def author = 'Owen Rubel'
	def authorEmail = 'orubel@gmail.com'
	def description = 'BeAPI Framework is a fully reactive plug-n-play API Framework for Distributed Architectures providing api abstraction, cached IO state, automated batching and more. It is meant to autmoate alot of the issues behind setting up and maintaining API\'s in distributed architectures as well as handling and simplifying automation.'
	def documentation = 'https://www.beapi.io/documentation'
	def license = 'MPL-2.0'
    def organization = [ name: 'BeAPI', url: 'http://www.beapi.io' ]
    def developers = [[ name: 'Owen Rubel', email: 'orubel@gmail.com' ]]
	def issueManagement = [system: 'GitHub', url: 'https://github.com/orubel/grails-api-toolkit-docs/issues']
	def scm = [url: 'https://github.com/orubel/api-framework']
	
	def dependsOn = [cache: '* > 3.0']
	def loadAfter = ['cache']
    //def loadBefore = ['spring-boot-starter-tomcat']

    def pluginExcludes = [
        'grails-app/views/error.gsp'
    ]
    def profiles = ['web']

    Closure doWithSpring() { { ->

        try{
            def conf = SpringSecurityUtils.securityConfig
            if (!conf || !conf.active) {
                return
            }

            SpringSecurityUtils.loadSecondaryConfig 'DefaultRestSecurityConfig'
            conf = SpringSecurityUtils.securityConfig

            /* restTokenValidationFilter */
            SpringSecurityUtils.registerFilter 'corsSecurityFilter', SecurityFilterPosition.PRE_AUTH_FILTER.order + 1
            SpringSecurityUtils.registerFilter 'tokenCacheValidationFilter', SecurityFilterPosition.PRE_AUTH_FILTER.order + 2
            SpringSecurityUtils.registerFilter 'contentTypeMarshallerFilter', SecurityFilterPosition.PRE_AUTH_FILTER.order + 3

            corsSecurityFilter(CorsSecurityFilter){}

            tokenCacheValidationFilter(TokenCacheValidationFilter) {
                headerName = conf.rest.token.validation.headerName
                validationEndpointUrl = conf.rest.token.validation.endpointUrl
                active = conf.rest.token.validation.active
                tokenReader = ref('tokenReader')
                enableAnonymousAccess = conf.rest.token.validation.enableAnonymousAccess
                authenticationSuccessHandler = ref('restAuthenticationSuccessHandler')
                authenticationFailureHandler = ref('restAuthenticationFailureHandler')
                restAuthenticationProvider = ref('restAuthenticationProvider')
                authenticationEventPublisher = ref('authenticationEventPublisher')
            }
            contentTypeMarshallerFilter(ContentTypeMarshallerFilter){}
        }catch(Exception e){
            throw new Exception("[BeAPIFramework] : Issue creating Filters :",e)
        }
    } }

    def doWithDynamicMethods = { applicationContext ->
        // Configure servlets
        try {
            def config = getBean('grailsApplication').config
            def servletContext = applicationContext.servletContext
            def serverInfo = servletContext.getServerInfo()


            config?.servlets?.each { name, parameters ->
                ServletRegistration servletRegistration = servletContext.addServlet(name, parameters.className)
                servletRegistration.addMapping(parameters.mapping)
                servletRegistration.setAsyncSupported(Boolean.TRUE)
                servletRegistration.setLoadOnStartup(1)

                // servletRegistration.setInitParameter("org.atmosphere.cpr.asyncSupport", "org.atmosphere.container.JettyServlet30AsyncSupportWithWebSocket")

                def initParams = parameters.initParams
                if (initParams != "none") {
                    initParams?.each { param, value ->
                        servletRegistration.setInitParameter(param, value)
                    }
                }
            }
        }catch(Exception e){
            throw new Exception('[BeAPIFramework] : Bad Mapping for initialization :',e)
        }
    }

    void doWithApplicationContext() {

        // Delegate OPTIONS requests to controllers
        try{
            applicationContext.dispatcherServlet.setDispatchOptionsRequest(true)

            String basedir = BuildSettings.BASE_DIR
            String apiObjectSrc = "${System.properties.'user.home'}/${grails.util.Holders.grailsApplication.config.iostate.preloadDir}"


            def ant = new AntBuilder()

            ant.mkdir(dir: "${basedir}/src/iostate")
            ant.mkdir(dir: "${apiObjectSrc}")
            //def ctx = applicationContext.getServletContext()
            //ctx.setInitParameter("dispatchOptionsRequest", "true");

            doInitApiFrameworkInstall(applicationContext)

            def statsService = applicationContext.getBean('statsService')
            statsService.flushAllStatsCache()

            parseFiles(apiObjectSrc.toString(), applicationContext)
        }catch(Exception e){
            throw new Exception('[BeAPIFramework] : Cannot set system properties :',e)
        }
    }

    /**
     * Given the context and path, looks for IO state files, parses and loads them into local cache
     * @param String path for direoctory of IO State files
     * @param ApplicationContext context for application
     */
    private parseFiles(String path, ApplicationContext applicationContext){
        LinkedHashMap methods = [:]

        println '### Loading IO State Files ...'

        try {
            new File(path).eachFile() { file ->
                String fileName = file.name.toString()

                def tmp = fileName.split('\\.')
                String fileChar1 = fileName.charAt(fileName.length() - 1)

                if (tmp[1] == 'json' && fileChar1== "n") {
                    //try{
                        JSONObject json = JSON.parse(file.text)
                        methods[json.NAME.toString()] = parseJson(json.NAME.toString(), json, applicationContext)
                        //parseJson(json.NAME.toString(), json, applicationContext)
                    //}catch(Exception e){
                    //    throw new Exception("[ApiObjectService :: initialize] : Unacceptable file '${file.name}' - full stack trace follows:",e)
                    //}
                }else{
                    println(" # Bad File Type [ ${tmp[1]} ]; Ignoring file : ${fileName}")
                }
            }
        }catch(Exception e){
            throw new Exception('[BeAPIFramework] : No IO State Files found for initialization :',e)
        }
    }

	void doInitApiFrameworkInstall(applicationContext) {
		//String basedir = applicationContext.getResource("../../..").getFile().path
		String basedir = BuildSettings.BASE_DIR
        def ant = new AntBuilder()
		//basedir = basedir.substring(0,basedir.length())

        println '### Installing API Framework ...'

        def iostateDir = "${basedir}/src/iostate/"
        def iofile = new File(iostateDir)
        if(!iofile.exists()) {
            writeFile('templates/iostate/Apidoc.json.template', "${iostateDir}Apidoc.json")
            writeFile('templates/iostate/Hook.json.template', "${iostateDir}Hook.json")
            writeFile('templates/iostate/IOState.json.template', "${iostateDir}IOState.json")
            println ' ... installing IO state dir/files ...'
        }
/*
        def contDir = "${basedir}/grails-app/controllers/net/nosegrind/apiframework/"
        def cfile = new File(contDir)
        if(!cfile.exists()) {
            ant.mkdir(dir: contDir)
            writeFile('templates/controllers/ApidocController.groovy.template', "${contDir}ApidocController.groovy")
            writeFile('templates/controllers/HookController.groovy.template', "${contDir}HookController.groovy")
            writeFile('templates/controllers/IostateController.groovy.template', "${contDir}IostateController.groovy")
            println ' ... installing Controller dir/files ...'
        }

        def domainDir = "${basedir}/grails-app/domain/net/nosegrind/apiframework/"
        def dfile = new File(domainDir)
        if(!dfile.exists()) {
            writeFile('templates/domains/Hook.groovy.template', "${domainDir}Hook.groovy")
            writeFile('templates/domains/HookRole.groovy.template', "${domainDir}HookRole.groovy")
            writeFile('templates/domains/Role.groovy.template', "${domainDir}Role.groovy")
            println ' ... installing Domain dir/files ...'
        }
*/


        if(!grailsApplication.config.apitoolkit){
            println " ... updating config ..."
            String groovyConf = "${basedir}/grails-app/conf/application.groovy"
            def confFile = new File(groovyConf)
            confFile.withWriterAppend { BufferedWriter writer ->
                writer.newLine()
                writer.newLine()
                writer.writeLine '// Added by the Reactive API Framework plugin:'

                writer.writeLine "apitoolkit.attempts= 5"
                writer.writeLine "apitoolkit.roles= ['ROLE_USER','ROLE_ROOT','ROLE_ADMIN','ROLE_ARCH']"
                writer.writeLine "apitoolkit.chaining.enabled= true"
                writer.writeLine "apitoolkit.batching.enabled= true"
                writer.writeLine "apitoolkit.encoding= 'UTF-8'"
                writer.writeLine "apitoolkit.user.roles= ['ROLE_USER']"
                writer.writeLine "apitoolkit.admin.roles= ['ROLE_ROOT','ROLE_ADMIN','ROLE_ARCH']"
                writer.writeLine "apitoolkit.serverType= 'master'"
                writer.writeLine "apitoolkit.webhook.services= ['iostate']"
                // set this per environment
               	writer.writeLine "apitoolkit.iostate.preloadDir= '"+System.getProperty('user.home')+"/.iostate'"
                writer.writeLine "apitoolkit.corsInterceptor.includeEnvironments= ['development','test']"
                writer.writeLine "apitoolkit.corsInterceptor.excludeEnvironments= ['production']"
                writer.writeLine "apitoolkit.corsInterceptor.allowedOrigins= ['localhost:3000']"

                writer.newLine()
            }
        }

        String isBatchServer = grailsApplication.config.apitoolkit.batching.enabled
        String isChainServer = grailsApplication.config.apitoolkit.chaining.enabled
        //final String isLocalAuth = (String)grailsApplication.config.apitoolkit.localauth.enabled

        System.setProperty('isBatchServer', isBatchServer)
        System.setProperty('isChainServer', isChainServer)
        //System.setProperty('isLocalAuth', isLocalAuth)

        println  '... API Framework installed. ###'
	}


    void onChange(Map<String, Object> event) {
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    void onConfigChange(Map<String, Object> event) {
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    void onShutdown(Map<String, Object> event) {
        // TODO Implement code that is executed when the application shuts down (optional)
    }

	void writeFile(String inPath, String outPath){
		String pluginDir = new File(getClass().protectionDomain.codeSource.location.path).path
		def plugin = new File(pluginDir)
        try {
            if (plugin.isFile() && plugin.name.endsWith("jar")) {
                JarFile jar = new JarFile(plugin)

                JarEntry entry = jar.getEntry(inPath)
                InputStream inStream = jar.getInputStream(entry);
                OutputStream out = new FileOutputStream(outPath);
                int c;
                while ((c = inStream.read()) != -1) {
                    out.write(c);
                }
                inStream.close();
                out.close();

                jar.close();
            }
        }catch(Exception e){
            println("Exception :"+e)
        }
	}

    LinkedHashMap parseJson(String apiName,JSONObject json, ApplicationContext applicationContext){
        def apiCacheService = applicationContext.getBean("apiCacheService")
        apiCacheService.flushAllApiCache()

        LinkedHashMap methods = [:]

        String networkGrp = json.NETWORKGRP
        json.VERSION.each() { vers ->
            def versKey = vers.key
            String defaultAction = (vers.value['DEFAULTACTION'])?vers.value.DEFAULTACTION:'index'

            Set deprecated = (vers.value.DEPRECATED)?vers.value.DEPRECATED:[]
            String domainPackage = (vers.value.DOMAINPACKAGE!=null || vers.value.DOMAINPACKAGE?.size()>0)?vers.value.DOMAINPACKAGE:null

            String actionname
            vers.value.URI.each() { it ->

                //def cache = apiCacheService.getApiCache(apiName.toString())
                //def cache = (temp?.get(apiName))?temp?.get(apiName):[:]

                methods['cacheversion'] = 1

                JSONObject apiVersion = json.VERSION[vers.key]

                actionname = it.key

                ApiDescriptor apiDescriptor
                //Map apiParams

                String apiMethod = it.value.METHOD
                String apiDescription = it.value.DESCRIPTION

                List apiRoles = (it.value.ROLES.DEFAULT)?it.value.ROLES.DEFAULT as List:null
                List networkRoles = grails.util.Holders.grailsApplication.config.apitoolkit.networkRoles."${networkGrp}"
                if(apiRoles) {
                    if(!(apiRoles-networkRoles.intersect(apiRoles).isEmpty())){
                        throw new Exception("[Runtime :: parseJson] : ${it.key}.ROLES.DEFAULT does not match any networkRoles for ${apiName} NETWORKGRP :",e)
                    }
                }else{
                    apiRoles = networkRoles
                }


                Set batchRoles = it.value.ROLES.BATCH
                Set hookRoles = it.value.ROLES.HOOK

                String uri = it.key
                apiDescriptor = createApiDescriptor(networkGrp, apiName, apiMethod, apiDescription, apiRoles, batchRoles, hookRoles, uri, json.get('VALUES'), apiVersion)
                if(!methods[vers.key]){
                    methods[vers.key] = [:]
                }

                if(!methods['currentStable']){
                    methods['currentStable'] = [:]
                    methods['currentStable']['value'] = json.CURRENTSTABLE
                }
                if(!methods[vers.key]['deprecated']){
                    methods[vers.key]['deprecated'] = []
                    methods[vers.key]['deprecated'] = deprecated
                }

                if(!methods[vers.key]['defaultAction']){
                    methods[vers.key]['defaultAction'] = defaultAction
                }


                methods[vers.key][actionname] = apiDescriptor

            }

            if(methods){
                def cache = apiCacheService.setApiCache(apiName,methods)

                cache[vers.key].each(){ key1,val1 ->

                    if(!['deprecated','defaultAction'].contains(key1)){
                        apiCacheService.setApiCache(apiName,key1, val1, vers.key)
                    }
                }

            }

        }
        return methods
    }



    private ApiDescriptor createApiDescriptor(String networkGrp, String apiname,String apiMethod, String apiDescription, List apiRoles, Set batchRoles, Set hookRoles, String uri, JSONObject values, JSONObject json){
        LinkedHashMap<String,ParamsDescriptor> apiObject = [:]
        ApiParams param = new ApiParams()

        Set fkeys = []
        Set pkeys= []
        List keys = []
        try {
            values.each { k, v ->
                keys.add(k)
                v.reference = (v.reference) ? v.reference : 'self';
                param.setParam(v.type, k)

                String hasKey = (v?.key) ? v.key : null

                if (hasKey != null) {
                    param.setKey(hasKey)

                    String hasReference = (v?.reference) ? v.reference : 'self'
                    param.setReference(hasReference)

                    if (['FOREIGN', 'INDEX', 'PRIMARY'].contains(v.key?.toUpperCase())) {
                        switch (v.key) {
                            case 'INDEX':
                                if (v.reference != 'self') {
                                    LinkedHashMap fkey = ["${k}": "${v.reference}"]
                                    fkeys.add(fkey)
                                }
                                break;
                            case 'FOREIGN':
                                LinkedHashMap fkey = ["${k}": "${v.reference}"]
                                fkeys.add(fkey)
                                break;
                            case 'PRIMARY':
                                pkeys.add(k)
                                break;
                        }
                    }
                }

                String hasDescription = (v?.description) ? v.description : ''
                param.setDescription(hasDescription)

                if (v.mockData!=null) {
                    if(v.mockData.isEmpty()){
                        param.setMockData('')
                    }else {
                        param.setMockData(v.mockData.toString())
                    }
                } else {
                    throw new Exception("[Runtime :: createApiDescriptor] : MockData Required for type '" + k + "' in IO State[" + apiname + "]")
                }

                // collect api vars into list to use in apiDescriptor

                apiObject[param.param.name] = param.toObject()
            }
        }catch(Exception e){
            throw new Exception("[Runtime :: createApiDescriptor] : Badly Formatted IO State :",e)
        }

        LinkedHashMap receives = getIOSet(json.URI[uri]?.REQUEST,apiObject,keys,apiname)
        LinkedHashMap returns = getIOSet(json.URI[uri]?.RESPONSE,apiObject,keys,apiname)

        ApiDescriptor service = new ApiDescriptor(
                'empty':false,
                'method':"$apiMethod",
                'networkGrp': "$networkGrp",
                'pkey':pkeys,
                'fkeys':fkeys,
                'description':"$apiDescription",
                'roles':apiRoles,
                'batchRoles':[],
                'hookRoles':[],
                'doc':[:],
                'receives':receives,
                'returns':returns
        )

        // override networkRoles with 'DEFAULT' in IO State



        batchRoles.each{
            if(!apiRoles.contains(it)){
                throw new Exception("[Runtime :: createApiDescriptor] : BatchRoles in IO State[" + apiname + "] do not match default/networkRoles")
            }
        }
        service['batchRoles'] = batchRoles

        hookRoles.each{
            if(!apiRoles.contains(it)){
                throw new Exception("[Runtime :: createApiDescriptor] : HookRoles in IO State[" + apiname + "] do not match default/networkRoles")
            }
        }
        service['hookRoles'] = hookRoles

        return service
    }

    private LinkedHashMap getIOSet(JSONObject io,LinkedHashMap apiObject,List valueKeys,String apiName){
        LinkedHashMap<String,ParamsDescriptor> ioSet = [:]

        io.each{ k, v ->
            // init
            if(!ioSet[k]){ ioSet[k] = [] }

            def roleVars=v.toList()
            roleVars.each{ val ->
                if(v.contains(val)){
                    if(!ioSet[k].contains(apiObject[val])) {
                        if (apiObject[val]){
                            ioSet[k].add(apiObject[val])
                        }else {
                            throw new Exception("VALUE '"+val+"' is not a valid key for IO State [${apiName}]. Please check that this 'VALUE' exists")
                        }
                    }
                }
            }
        }

        def permitAll = ioSet['permitAll']
        ioSet.each(){ key, val ->
            if(key!='permitAll'){
                permitAll.each(){ it ->
                    if(!ioSet[key].contains("-${it}")) {
                        ioSet[key].add(it)
                    }
                }
            }
        }

        //List ioKeys = []
        ioSet.each(){ k, v ->
            List ioKeys = v.collect(){ it -> it.name }
            if (!ioKeys.minus(valueKeys).isEmpty()) {
                throw new Exception("[Runtime :: getIOSet] : VALUES for IO State [" + apiName + "] do not match REQUEST/RESPONSE values for endpoints")
            }
        }

        return ioSet
    }

}
