#!/usr/bin/env groovy

String userHome = System.properties['user.home']

Properties props = new Properties()
def propsFile = new File("${userHome}/.jenkins/workspace/api-framework/gradle.properties")
props.load(propsFile.newDataInputStream())

def appVersion = props.getProperty('buildVersion')
def patch = System.getenv('BUILD_NUMBER')
def version = "${appVersion}"

props.setProperty('patchVersion', patch)
props.setProperty('buildVersion', version)
props.store(propsFile.newWriter(), null)



