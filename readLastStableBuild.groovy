#!/usr/bin/env groovy

String userHome = System.properties['user.home']
File file = new File("/home/orubel/.jenkins/jobs/api-framework/builds/lastStableBuild/build.xml")

String stringXML = file.text

def build = new XmlSlurper().parseText(stringXML)

String version
boolean next = false
build.actions."hudson.plugins.parameterizedtrigger.CapturedEnvironmentAction".env."tree-map"."string".each(){
	if(next){
		version = (version)?"${version}.${it}":it
		next = false
	}
	switch(it){
		case 'BEAPI_BUILD_VERSION':
		case 'BUILD_NUMBER':
			next = true
			break
	}
}

Properties props = new Properties()
def propsFile = new File("/home/orubel/.jenkins/workspace/beapi-backend/gradle.properties")
props.load(propsFile.newDataInputStream())
println props.getProperty('apiFrameworkVersion')

props.setProperty('apiFrameworkVersion', version)
props.store(propsFile.newWriter(), null)

println props.getProperty('apiFrameworkVersion')
