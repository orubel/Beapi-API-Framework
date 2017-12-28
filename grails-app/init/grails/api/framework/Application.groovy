/*
 * Academic Free License ("AFL") v. 3.0
 * Copyright 2014-2017 Owen Rubel
 *
 * IO State (tm) Owen Rubel 2014
 * API Chaining (tm) Owen Rubel 2013
 *
 *   https://opensource.org/licenses/AFL-3.0
 */

package grails.api.framework

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
//import org.springframework.context.annotation.ComponentScan
import javax.servlet.*;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.security.SecurityFilterAutoConfiguration


//@ComponentScan
//@EnableAutoConfiguration(exclude = [SecurityFilterAutoConfiguration])
@EnableAutoConfiguration
class Application extends GrailsAutoConfiguration {
	
    static void main(String[] args) {
        GrailsApp.run(Application)
    }

}
