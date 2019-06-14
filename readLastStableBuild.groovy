#!/usr/bin/env groovy



String userHome = System.properties['user.home']

def appVersion = (System.getenv('BEAPI_BUILD_VERSION'))?System.getenv('BEAPI_BUILD_VERSION'):'1'
def patch = System.getenv('BUILD_NUMBER')
def version = "${appVersion}.${patch}"


Properties props = new Properties()
FileOutputStream out1 = new FileOutputStream("${userHome}/.jenkins/workspace/beapi-backend/gradle.properties")
FileInputStream in1 = new FileInputStream("${userHome}/.jenkins/workspace/beapi-backend/gradle.properties")

props.load(in1)
//props.remove('apiFrameworkVersion')
//props.store(out1, null)
//out1.close()

props.load(in1)
props.setProperty('apiFrameworkVersion', version)
props.store(out1, null)
out1.close()


Properties props2 = new Properties()
FileOutputStream out2 = new FileOutputStream("${userHome}/.jenkins/workspace/api-framework/gradle.properties")
FileInputStream in2 = new FileInputStream("${userHome}/.jenkins/workspace/api-framework/gradle.properties")

//props2.load(in2)
//props2.remove('patchVersion')
//props2.store(out2, null)


props2.load(in2)
props2.setProperty('patchVersion', patch)
props2.store(out2, null)
out2.close()

/*
Properties props = new Properties()
FileOutputStream out = new FileOutputStream("${userHome}/.jenkins/workspace/beapi-backend/gradle.properties");
FileInputStream in = new FileInputStream("${userHome}/.jenkins/workspace/beapi-backend/gradle.properties");
//def propsFile = new File("${userHome}/.jenkins/workspace/beapi-backend/gradle.properties")
println("### writing to '${userHome}/.jenkins/workspace/beapi-backend/gradle.properties' > ${version}")
props.setProperty('apiFrameworkVersion', version)
props.store(propsFile.newWriter(), null)
try {
	props.store(propsFile.newWriter(), null)
	props.close()
} catch (IOException ex) {
	println(ex)
}



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


