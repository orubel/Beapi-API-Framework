#!/usr/bin/env groovy


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


def propsFile = new File("/home/orubel/.jenkins/workspace/beapi-backend/gradle.properties")

if (propsFile.exists()) {
	propsFile.withReader { r ->
		def props = new Properties()
		props.load(r)
		props.setProperty('apiFrameworkVersion', version)
		props.store(propsFile.newWriter(), null)
	}
}
