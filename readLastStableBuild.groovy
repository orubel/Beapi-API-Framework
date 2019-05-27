#!/usr/bin/env groovy

String userHome = System.properties['user.home']

println(System.getenv('BEAPI_BUILD_VERSION'))
println(System.getenv('BUILD_NUMBER'))

	def appVersion = (System.getenv('BEAPI_BUILD_VERSION'))?System.getenv('BEAPI_BUILD_VERSION'):'1'
	def patch = (file.text as Integer)-1
	def version = "${appVersion}.${patch}"

	println(version)

	/*
	Properties props = new Properties()
	def propsFile = new File("/home/orubel/.jenkins/workspace/beapi-backend/gradle.properties")
	props.load(propsFile.newDataInputStream())

	props.setProperty('apiFrameworkVersion', version)
	props.store(propsFile.newWriter(), null)
*/



