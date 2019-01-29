package net.nosegrind.apiframework

import grails.util.Holders
import grails.dev.commands.*
import grails.core.GrailsApplication
import grails.util.Environment
import grails.dev.commands.ApplicationCommand
import grails.dev.commands.ExecutionContext
import org.hibernate.metadata.ClassMetadata

import grails.core.GrailsDomainClass

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.regex.Matcher
import java.util.regex.Pattern



/**
 *
 * Commandline tool for bootstrapping IO State files based on pre-existing domain classes
 *
 * Usage:
 * ./gradlew GenerateIostate
 *
 * This will generate IO state files for all domains
 *
 * ./gradlew GenerateIostate -Dfile=Person
 *
 * This will generate the IO State file for the domain class 'Person'
 *
 * @see ApiCommLayer
 *
 */
class GenerateIostateCommand implements ApplicationCommand {


	//@Autowired
	//GrailsApplication grailsApplication
    String iostateDir = ""
    List reservedNames = ['hook','iostate','apidoc']
    LinkedHashMap mocks = [
            "String":'Mock String',
            "Date":'Mock Date',
            "Long":987,
            "Boolean":true,
            "Float":987.654,
            "BigDecimal":987654321,
            "List":['this','is','mock','data'],
            "Map":['key1':'value1','key2':'value2'],
            "Email":'test@test.com',
            "Url":'http://www.test.com'
    ]

    boolean handle(ExecutionContext ctx) {
        // SET IOSTATE FILES PATH
        switch(Environment.current){
            case Environment.DEVELOPMENT:
                iostateDir = Holders.grailsApplication.config.environments.development.iostate.preloadDir
                break
            case Environment.TEST:
                iostateDir = Holders.grailsApplication.config.environments.test.iostate.preloadDir
                break
            case Environment.PRODUCTION:
                iostateDir = Holders.grailsApplication.config.environments.production.iostate.preloadDir
                break
        }

        String file = (System.properties['file']) ?System.properties['file']: null
        println "### Bootstrapping IOState Files ..."


        String logicalName
        String realName
        def sessionFactory = Holders.grailsApplication.mainContext.sessionFactory

        if(file){
            // single file bootstrap
            logicalName = file[0].toLowerCase()+file.substring(1)
            realName = file
            def controller = Holders.grailsApplication.getArtefactByLogicalPropertyName('Controller', logicalName)
            def domain = Holders.grailsApplication.getArtefactByLogicalPropertyName('Domain', logicalName)
            ClassMetadata hibernateMetaClass = sessionFactory.getClassMetadata(domain.clazz)

            String[] keys = hibernateMetaClass.getKeyColumnNames()
            String values = """
\t\t\"id\": {
\t\t\t"key\": \"PRIMARY\",
\t\t\t\"type\": \"Long\",
\t\t\t\"description\": \"Primary Key\",
\t\t\t"mockData": \"${mocks['LONG']}\",
\t\t},
"""
            String uris = "\r"

            def constraints = domain.getConstrainedProperties()

            if (controller && !reservedNames.contains(logicalName)) {
                List actions = controller.actions as List


                def domainProperties = hibernateMetaClass.getPropertyNames()

                List variables = []
                variables.add("\"id\"")
                domainProperties.each() { it2 ->
                    List ignoreList = ['constrainedProperties', 'gormPersistentEntity', 'properties', 'async', 'gormDynamicFinders', 'all', 'attached', 'class', 'constraints', 'reports', 'dirtyPropertyNames', 'errors', 'dirty', 'transients', 'count']

                    String type = ""
                    String key = ""

                    if (!ignoreList.contains(it2)) {
                        String thisType = hibernateMetaClass.getPropertyType(it2).class as String
                        if (keys.contains(it2) || thisType == 'class org.hibernate.type.ManyToOneType') {
                            key = "FOREIGN"
                            type = 'Long'
                        } else {
                            type = getValueType(thisType)
                        }
                        String name = (['FOREIGN', 'INDEX'].contains(key)) ? "${it2}Id".toString() : it2
                        variables.add("\"${name}\"")
                        String value = ""
                        String mock = mocks."${type}"
                        if (constraints[name]) {
                            if (thisType == 'class org.hibernate.type.StringType') {
                                if (constraints[name]?.isEmail()) {
                                    mock = mocks['Email']
                                }
                                if (constraints[name]?.isUrl()) {
                                    mock = mocks['Url']
                                }
                            }
                        }

                        if (key) {
                            value = """\t\t\"${name}\": {
\t\t\t"key": \"${key}\",
\t\t\t"reference": \"${it2}\",
\t\t\t\"type\": \"${type}\",
\t\t\t\"description\": \"Description for ${it2}\",
\t\t\t"mockData": \"${mock}\",
\t\t},
"""
                        } else {

                            value = """\t\t\"${name}\": {
\t\t\t\"type\": \"${type}\",
\t\t\t\"description\": \"Description for ${it2}\",
\t\t\t"mockData": "${mock}\",
\t\t},
"""
                        }

                        values <<= value
                    }
                }
                //println values


                actions.each() { it4 ->
                    String method = ""
                    List req = []
                    List resp = []
                    Pattern listPattern = Pattern.compile("list")
                    Pattern getPattern = Pattern.compile("get|getBy|show|listBy")
                    Pattern postPattern = Pattern.compile("create|make|generate|build|save")
                    Pattern putPattern = Pattern.compile("edit|update|")
                    Pattern deletePattern = Pattern.compile("delete|destroy|kill|reset")


                    Matcher getm = getPattern.matcher(it4)
                    if (getm.find()) {
                        method = 'GET'
                        req.add('\"id\"')
                        resp = variables
                    }

                    Matcher listm = listPattern.matcher(it4)
                    if (listm.find()) {
                        method = 'GET'
                        resp = variables
                    }

                    if (method.isEmpty()) {
                        Matcher postm = postPattern.matcher(it4)
                        if (postm.find()) {
                            method = 'POST'
                            req = variables
                            resp.add('\"id\"')
                        }
                    }

                    if (method.isEmpty()) {
                        Matcher putm = putPattern.matcher(it4);
                        if (putm.find()) {
                            method = 'PUT'
                            req = variables
                            resp.add('\"id\"')
                        }
                    }

                    if (method.isEmpty()) {
                        Matcher delm = deletePattern.matcher(it4);
                        if (delm.find()) {
                            method = 'DELETE'
                            req.add('\"id\"')
                            resp.add('\"id\"')
                        }
                    }
                    //response.collect{ '"' + it + '"'}
                    //request.collect{ '"' + it + '"'}

                    String uri = """
\t\t\t\t\"${it4}\": {
\t\t\t\t\t\"METHOD\": "${method}",
\t\t\t\t\t\"DESCRIPTION\": \"Description for ${it4}\",
\t\t\t\t    \t\"ROLES\": {
\t\t\t\t\t    \"DEFAULT\": [\"permitAll\"],
\t\t\t\t\t    \"BATCH\": [\"ROLE_ADMIN\"]
\t\t\t\t\t},
\t\t\t\t\t\"REQUEST\": {
\t\t\t\t\t\t\"permitAll\": ${req}
\t\t\t\t\t},
\t\t\t\t\t\"RESPONSE\": {
\t\t\t\t\t\t\"permitAll\": ${resp}
\t\t\t\t\t}
\t\t\t\t},
"""
                    uris <<= uri
                }
            }
            if (logicalName.length() > 0 && values.length() > 0 && uris.length() > 1) {
                createTemplate(iostateDir, realName, logicalName, values, uris)
            }









        }else {
            // GET BINDING VARIABLES
            def domains = Holders.grailsApplication.getArtefacts("Domain")
            domains.each() { it ->
                logicalName = it.getLogicalPropertyName()
                realName = it.getName()
                ClassMetadata hibernateMetaClass = sessionFactory.getClassMetadata(it.clazz)

                String[] keys = hibernateMetaClass.getKeyColumnNames()
                String values = """
\t\t\"id\": {
\t\t\t"key\": \"PRIMARY\",
\t\t\t\"type\": \"Long\",
\t\t\t\"description\": \"Primary Key\",
\t\t\t"mockData": \"${mocks['LONG']}\",
\t\t},
"""
                String uris = "\r"
                def controller = Holders.grailsApplication.getArtefactByLogicalPropertyName('Controller', logicalName)
                def domain = Holders.grailsApplication.getArtefactByLogicalPropertyName('Domain', logicalName)

                //println("[" + logicalName + "]:" + domain.getConstrainedProperties())
                def constraints = domain.getConstrainedProperties()
                if (controller && !reservedNames.contains(logicalName)) {
                    List actions = controller.actions as List


                    def domainProperties = hibernateMetaClass.getPropertyNames()

                    List variables = []
                    variables.add("\"id\"")
                    domainProperties.each() { it2 ->
                        List ignoreList = ['constrainedProperties', 'gormPersistentEntity', 'properties', 'async', 'gormDynamicFinders', 'all', 'attached', 'class', 'constraints', 'reports', 'dirtyPropertyNames', 'errors', 'dirty', 'transients', 'count']

                        String type = ""
                        String key = ""

                        if (!ignoreList.contains(it2)) {
                            String thisType = hibernateMetaClass.getPropertyType(it2).class as String
                            if (keys.contains(it2) || thisType == 'class org.hibernate.type.ManyToOneType') {
                                key = "FOREIGN"
                                type = 'Long'
                            } else {
                                type = getValueType(thisType)
                            }
                            String name = (['FOREIGN', 'INDEX'].contains(key)) ? "${it2}Id".toString() : it2
                            variables.add("\"${name}\"")
                            String value = ""
                            String mock = mocks."${type}"
                            if (constraints[name]) {
                                if (thisType == 'class org.hibernate.type.StringType') {
                                    if (constraints[name]?.isEmail()) {
                                        mock = mocks['Email']
                                    }
                                    if (constraints[name]?.isUrl()) {
                                        mock = mocks['Url']
                                    }
                                }
                            }

                            if (key) {
                                value = """\t\t\"${name}\": {
\t\t\t"key": \"${key}\",
\t\t\t"reference": \"${it2}\",
\t\t\t\"type\": \"${type}\",
\t\t\t\"description\": \"Description for ${it2}\",
\t\t\t"mockData": \"${mock}\",
\t\t},
"""
                            } else {

                                value = """\t\t\"${name}\": {
\t\t\t\"type\": \"${type}\",
\t\t\t\"description\": \"Description for ${it2}\",
\t\t\t"mockData": "${mock}\",
\t\t},
"""
                            }

                            values <<= value
                        }
                    }
                    //println values


                    actions.each() { it4 ->
                        String method = ""
                        List req = []
                        List resp = []
                        Pattern listPattern = Pattern.compile("list")
                        Pattern getPattern = Pattern.compile("get|getBy|show|listBy")
                        Pattern postPattern = Pattern.compile("create|make|generate|build|save")
                        Pattern putPattern = Pattern.compile("edit|update|")
                        Pattern deletePattern = Pattern.compile("delete|destroy|kill|reset")


                        Matcher getm = getPattern.matcher(it4)
                        if (getm.find()) {
                            method = 'GET'
                            req.add('\"id\"')
                            resp = variables
                        }

                        Matcher listm = listPattern.matcher(it4)
                        if (listm.find()) {
                            method = 'GET'
                            resp = variables
                        }

                        if (method.isEmpty()) {
                            Matcher postm = postPattern.matcher(it4)
                            if (postm.find()) {
                                method = 'POST'
                                req = variables
                                resp.add('\"id\"')
                            }
                        }

                        if (method.isEmpty()) {
                            Matcher putm = putPattern.matcher(it4);
                            if (putm.find()) {
                                method = 'PUT'
                                req = variables
                                resp.add('\"id\"')
                            }
                        }

                        if (method.isEmpty()) {
                            Matcher delm = deletePattern.matcher(it4);
                            if (delm.find()) {
                                method = 'DELETE'
                                req.add('\"id\"')
                                resp.add('\"id\"')
                            }
                        }
                        //response.collect{ '"' + it + '"'}
                        //request.collect{ '"' + it + '"'}

                        String uri = """
\t\t\t\t\"${it4}\": {
\t\t\t\t\t\"METHOD\": "${method}",
\t\t\t\t\t\"DESCRIPTION\": \"Description for ${it4}\",
\t\t\t\t    \t\"ROLES\": {
\t\t\t\t\t    \"DEFAULT\": [\"permitAll\"],
\t\t\t\t\t    \"BATCH\": [\"ROLE_ADMIN\"]
\t\t\t\t\t},
\t\t\t\t\t\"REQUEST\": {
\t\t\t\t\t\t\"permitAll\": ${req}
\t\t\t\t\t},
\t\t\t\t\t\"RESPONSE\": {
\t\t\t\t\t\t\"permitAll\": ${resp}
\t\t\t\t\t}
\t\t\t\t},
"""
                        uris <<= uri
                    }
                }
                if (logicalName.length() > 0 && values.length() > 0 && uris.length() > 1) {

                    createTemplate(iostateDir, realName, logicalName, values, uris)
                }
            }
        }

        // write templates

        return true
    }

    private String getValueType(String type){
        switch(type){
            case 'class org.hibernate.type.TextType':
            case 'class org.hibernate.type.StringType':
            case 'java.lang.String':
                return 'String'
                break
            case 'class org.hibernate.type.IntegerType':
            case 'class org.hibernate.type.LongType':
            case 'java.lang.Integer':
            case 'java.lang.Long':
                return 'Long'
                break
            case 'class org.hibernate.type.BooleanType':
            case 'java.lang.Boolean':
                return 'Boolean'
                break
            case 'class org.hibernate.type.DoubleType':
            case 'class org.hibernate.type.FloatType':
            case 'java.lang.Double':
            case 'java.lang.Float':
                return 'Float'
                break
            case 'class org.hibernate.type.TimestampType':
            case 'class org.hibernate.type.DateType':
            case 'java.util.Date':
                return 'Date'
                break
            case 'class org.hibernate.type.BigDecimalType':
            case 'java.math.BigDecimal':
                return 'BigDecimal'
                break
            case 'class org.hibernate.type.MapType':
            case 'class java.util.HashMap':
            case 'java.util.LinkedHashMap':
                return 'Map'
                break
            case 'class org.hibernate.type.ArrayType':
            case 'class java.util.ArrayList':
            case 'java.util.Arraylist':
                return 'List'
                break
            default:
                return 'Composite'
                break
        }
    }


    private void createTemplate(String iostateDir, String realName, String logicalName, String values, String uris){
        // MAKE SURE DIRECTORY EXISTS

        String userHome = System.properties['user.home']
        iostateDir = userHome + "/" + iostateDir

        File ioFile = new File(iostateDir)
        if (ioFile.exists() && ioFile.isDirectory()) {

            String template = """{
\t\"NAME\": \"${logicalName}\",
\t\"VALUES\": { ${values}
\t},
\t\"CURRENTSTABLE\": \"1\",
\t\"VERSION\": {
\t\t\"1\": {
\t\t\t\"DEFAULTACTION\":\"list\",
\t\t\t\"URI\": { ${uris}

\t\t\t}
\t\t}
\t}
}
"""

                //String basedir = BuildSettings.BASE_DIR
                //def ant = new AntBuilder()
                //basedir = basedir.substring(0,basedir.length())

            try {
                //Whatever the file path is.

                String path = iostateDir+"/${realName}.json" as String
                File iostateFile = new File(path)
                if (!iostateFile.exists()) {
                    println(iostateDir + "/${realName}.json")

                    FileOutputStream is = new FileOutputStream(iostateFile)
                    OutputStreamWriter osw = new OutputStreamWriter(is)
                    Writer w = new BufferedWriter(osw)
                    w.write(template)
                    w.close()
                }
            } catch (IOException e) {
                println("Problem writing to the file ${path}")
            }


        }
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


}
