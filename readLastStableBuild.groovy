#!/usr/bin/env groovy

String userHome = System.properties['user.home']

def appVersion = (System.getenv('BEAPI_BUILD_VERSION'))?System.getenv('BEAPI_BUILD_VERSION'):'1'
def patch = System.getenv('BUILD_NUMBER')
def version = "${appVersion}.${patch}"


Properties props = new Properties()
def propsFile = new File("/${userHome}/.jenkins/workspace/beapi-backend/gradle.properties")
props.load(propsFile.newDataInputStream())

props.setProperty('apiFrameworkVersion', version)
props.store(propsFile.newWriter(), null)




