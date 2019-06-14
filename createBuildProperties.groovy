#!/usr/bin/env groovy



String userHome = System.properties['user.home']

def appVersion = (System.getenv('BEAPI_BUILD_VERSION'))?System.getenv('BEAPI_BUILD_VERSION'):'1'
def patch = System.getenv('BUILD_NUMBER')
def version = "${appVersion}.${patch}"



Properties props2 = new Properties()
FileOutputStream out2 = new FileOutputStream("${userHome}/.jenkins/workspace/api-framework/gradle.properties")
FileInputStream in2 = new FileInputStream("${userHome}/.jenkins/workspace/api-framework/gradle.properties")

//props2.load(in2)
//props2.remove('patchVersion')
//props2.store(out2, null)

props2.load(in2)
props2.setProperty('patchVersion', patch)
props2.store(out2, null)
in2.close()




