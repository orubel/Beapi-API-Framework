import grails.util.BuildSettings
import grails.util.Environment


// See http://logback.qos.ch/manual/groovy.html for details on configuration
appender('STDOUT', ConsoleAppender) {
    encoder(PatternLayoutEncoder) {
        pattern = "%level %logger - %msg%n"
    }
}

root(ERROR, ['STDOUT'])
logger 'grails.artefact.Interceptor', DEBUG, ['STDOUT'], false
logger 'grails.artefact.Interceptor', ERROR, ['STDOUT'], false
logger("StackTrace", ERROR, ['FULL_STACKTRACE'],false)

logger 'grails.artefact.Interceptor', INFO, ['STDOUT'], false
logger 'grails.artefact.Interceptor', INFO, ['STDOUT'], false




if(Environment.current == Environment.DEVELOPMENT) {
    def targetDir = BuildSettings.TARGET_DIR
    if(targetDir) {

        appender("FULL_STACKTRACE", FileAppender) {

            file = "${targetDir}/stacktrace.log"
            append = true
            encoder(PatternLayoutEncoder) {
                pattern = "%level %logger - %msg%n"
            }
        }
        logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false )
        logger("net.nosegrind.apiframework", INFO, ['STDOUT'], false)


    }
}
