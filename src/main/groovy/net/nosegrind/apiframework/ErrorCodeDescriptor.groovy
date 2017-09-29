package net.nosegrind.apiframework;

import grails.validation.Validateable
import grails.compiler.GrailsCompileStatic

//@Validateable
//@GrailsCompileStatic
class ErrorCodeDescriptor implements Validateable{

	Integer code
	String description

	static constraints = { 
		code(nullable:false,inList: [200, 304, 400,403,404,405,409,412,413,416,500,503])
		description(nullable:false,maxSize:1000)
	} 
}
