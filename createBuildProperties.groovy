#!/usr/bin/env groovy

String userHome = System.properties['user.home']


Properties props = new Properties()

// update 3.x gradle.properties
def propsFile = new File("/var/lib/jenkins/workspace/api-framework/gradle.properties")
props.load(propsFile.newDataInputStream())

def appVersion = props.getProperty('buildVersion')
def patch = (System.getenv('BUILD_NUMBER'))?System.getenv('BUILD_NUMBER'):props.getProperty('patchVersion')
def version = "${appVersion}"
def job = System.getenv('JOB_NAME')

println("job:"+job)
println("patch:"+patch)
println("version:"+version)

props.setProperty('patchVersion', patch)
props.setProperty('buildVersion', version)
props.store(propsFile.newWriter(), null)


// update default gradle.properties
def propsFile2 = new File("/var/lib/jenkins/workspace/api-framework/gradle.properties")
props.load(propsFile2.newDataInputStream())

appVersion = props.getProperty('buildVersion')
patch = (System.getenv('BUILD_NUMBER'))?System.getenv('BUILD_NUMBER'):props.getProperty('patchVersion')
version = "${appVersion}"

println("patch:"+patch)
println("version:"+version)

props.setProperty('patchVersion', patch)
props.setProperty('buildVersion', version)
props.store(propsFile2.newWriter(), null)



