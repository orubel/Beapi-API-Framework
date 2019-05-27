#!/usr/bin/env groovy

String userHome = System.properties['user.home']
File file = new File("/home/orubel/.jenkins/jobs/api-framework/builds/lastStableBuild/build.xml")

String stringXML = file.text

def build = new XmlSlurper().parseText(stringXML)




println(build.actions."hudson.plugins.git.util.BuildData".buildsByBranchName.entry."hudson.plugins.git.util.Build".hudsonBuildNumber)

def appVersion = (System.getenv('BEAPI_BUILD_VERSION'))?System.getenv('BEAPI_BUILD_VERSION'):'1'
def patch = build.actions."hudson.plugins.git.util.BuildData".buildsByBranchName.entry."hudson.plugins.git.util.Build".hudsonBuildNumber
def version = "${appVersion}.${patch}"

Properties props = new Properties()
def propsFile = new File("/home/orubel/.jenkins/workspace/beapi-backend/gradle.properties")
props.load(propsFile.newDataInputStream())

props.setProperty('apiFrameworkVersion', version)
props.store(propsFile.newWriter(), null)


