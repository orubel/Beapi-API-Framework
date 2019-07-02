

buildscript {
    ext {
        grailsVersion = project.grailsVersion
        gradleWrapperVersion = project.gradleWrapperVersion
    }
    repositories {
        flatDir {
            dirs 'lib'
        }
        mavenLocal()
        mavenCentral()
        maven { url "https://repo.grails.org/grails/core" }
        maven { url "${System.getProperty('user.home')}/.m2/repository" }
        jcenter {
            url "http://jcenter.bintray.com/"
        }
    }
    dependencies {
        classpath group: 'org.codehaus.gpars', name: 'gpars', version: '1.2.1'
        classpath "org.grails:grails-gradle-plugin:$grailsVersion"
        classpath "org.grails.plugins:hibernate5:${gormVersion-".RELEASE"}"
        classpath 'com.jfrog.bintray.gradle:gradle-bintray-plugin:1.7.3'
    }
}

plugins {
    id "io.spring.dependency-management" version "0.6.0.RELEASE"
    id "com.jfrog.bintray" version "1.2"
}

def appVersion = project.buildVersion
def patch = (System.getenv('BUILD_NUMBER'))?System.getenv('BUILD_NUMBER'):project.patchVersion
version = "${appVersion}.${patch}"
group "org.grails.plugins"


apply plugin: "maven-publish"
apply plugin: "org.springframework.boot"
apply plugin: "com.jfrog.bintray"

// Used for publishing to central repository, remove if not needed
apply plugin: "org.grails.grails-plugin-publish"
apply plugin: "org.grails.grails-plugin"
apply plugin: "org.grails.grails-doc"


grailsPublish {
    user = System.getenv('BINTRAY_USER')
    key = System.getenv('BINTRAY_KEY')
    githubSlug = 'orubel/Beapi-API-Framework'
    license { name = 'MPL-2.0' }
    title = "BeAPI API Framework"
    desc = "BeAPI API Framework is a fully reactive plug-n-play API Framework for Distributed Architectures providing api abstraction, cached IO state, automated batching and more. It is meant to autmoate alot of the issues behind setting up and maintaining API's in distributed architectures as well as handling and simplifying automation."
    developers = [orubel:"Owen Rubel"]
}

//ext {
//    grailsVersion = project.grailsVersion
//    gradleWrapperVersion = project.gradleWrapperVersion
//}

sourceCompatibility = 1.8
targetCompatibility = 1.8


repositories {
    mavenLocal()
    mavenCentral()
    maven { url "https://repo.grails.org/grails/core" }
    maven { url "${System.getProperty('user.home')}/.m2/repository" }
    jcenter {
        url "http://jcenter.bintray.com/"
    }
}

//dependencyManagement {
//    imports {
//        mavenBom "org.grails:grails-bom:$grailsVersion"
//    }
//    applyMavenExclusions false
//}

dependencies {
    compile 'org.grails.plugins:cache:4.0.0'
    //compile 'org.grails.plugins:cache:3.0.2'
    //compile 'org.springframework.boot:spring-boot-starter-logging'
    compile 'org.springframework.boot:spring-boot-autoconfigure'
    provided 'org.springframework.boot:spring-boot-starter-tomcat'


    compile "org.grails:grails-logging"
    compile "org.grails:grails-plugin-i18n"
    compile "org.grails:grails-plugin-services"
    compile "org.grails:grails-plugin-interceptors"
    compile 'org.grails.plugins:converters:3.3.1'
    compile "org.grails.plugins:events"

    compile('org.grails.plugins:spring-security-core:3.2.0.M1') {
        exclude(module: 'org.springframework.security:spring-security-web')
        exclude(module: 'org.grails.plugins:cors')
    }
    compile group: 'com.google.guava', name: 'guava', version: '14.0'

    compile('org.grails.plugins:spring-security-rest:2.0.0.M2') {
        exclude(module: 'com.google.guava:guava-io')
        exclude(module: 'org.springframework.security:spring-security-web')
        exclude(module: 'org.grails.plugins:spring-security-core')
        exclude(module: 'org.grails.plugins:cors')
    }

    /**
     * These are not excluded because spring-security-rest
     * has GSP dependencies; may rewrite that plugin in future
     */
    runtime('org.grails:grails-web') {
        //exclude(module: 'org.grails:grails-web-jsp')
        //exclude(module: 'org.grails:grails-web-sitemesh')
        //exclude(module: 'org.grails:grails-web-gsp')
        //exclude(module: 'org.grails:grails-web-databinding:5.0.1')
    }

    // UNCOMMENT NOSQL DB AS NEEDED FOR LIBRARIES
    //compile 'org.grails.plugins:mongodb:5.0.2'


    compile 'org.codehaus.gpars:gpars:1.2.1', {
        exclude group:'org.multiverse', module:'multiverse-core'
        exclude group:'org.codehaus.groovy', module: 'groovy-all'
    }

    //compile('org.grails:grails-logging:3.1.1')
    compile("org.codehaus.groovy:groovy-ant:$groovyVersion")

    compile "org.grails.plugins:hibernate5"
    compile "org.hibernate:hibernate-core:5.1.5.Final"
    compile "org.hibernate:hibernate-ehcache:5.1.5.Final"
}

task wrapper(type: Wrapper) {
    gradleVersion = gradleWrapperVersion
}

bootRun {
    systemProperties = System.properties
}

test {
    systemProperties = System.properties
}
