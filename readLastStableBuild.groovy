#!/usr/bin/env groovy
import org.apache.commons.configuration2.PropertiesConfiguration

println "ls -all".execute().text

String userHome = System.properties['user.home']

def appVersion = (System.getenv('BEAPI_BUILD_VERSION'))?System.getenv('BEAPI_BUILD_VERSION'):'1'
def patch = System.getenv('BUILD_NUMBER')
def version = "${appVersion}.${patch}"


PropertiesConfiguration config = new PropertiesConfiguration("${userHome}/.jenkins/workspace/beapi-backend/gradle.properties")
config.setProperty('apiFrameworkVersion', version)
config.save()

/*
Properties props = new Properties()
def propsFile = new File("${userHome}/.jenkins/workspace/beapi-backend/gradle.properties")
props.load(propsFile.newDataInputStream())
println("### writing to '${userHome}/.jenkins/workspace/beapi-backend/gradle.properties' > ${version}")
props.setProperty('apiFrameworkVersion', version)
props.store(propsFile.newWriter(), null)
try {
	props.store(propsFile.newWriter(), null)
} catch (IOException ex) {
	println(ex)
}
*/

PropertiesConfiguration config = new PropertiesConfiguration("${userHome}/.jenkins/workspace/api-framework/gradle.properties")
config.setProperty('patchVersion', patch)
config.save()

/*
Properties props2 = new Properties()
def propsFile2 = new File("${userHome}/.jenkins/workspace/api-framework/gradle.properties")
props2.load(propsFile2.newDataInputStream())
println("### writing to '${userHome}/.jenkins/workspace/api-framework/gradle.properties' > ${patch}")
props2.setProperty('patchVersion', patch)
OutputStream out = new FileOutputStream( f );
try {
	props2.store(propsFile2.newWriter(), null)
} catch (IOException ex) {
	println(ex)
}
*/
