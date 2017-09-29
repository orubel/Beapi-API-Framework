package net.nosegrind.apiframework


import grails.validation.Validateable
import grails.compiler.GrailsCompileStatic

// name is name of the object used

//@GrailsCompileStatic
class ApiDescriptor implements Validateable {

	boolean empty = false
	String defaultAction
	List deprecated
	String method
	List pkey
	List fkeys
	List roles
	List batchRoles
	List hookRoles
	String name
    String description
	Map doc
    LinkedHashMap<String,ParamsDescriptor> receives
    LinkedHashMap<String,ParamsDescriptor> returns
	LinkedHashMap cachedResult


	static constraints = { 
		method(nullable:false,inList: ["GET","POST","PUT","DELETE"])
		pkey(nullable:true)
		fkeys(nullable:true)
		roles(nullable:true)
		batchRoles(nullable:true, validator: { val, obj ->
			if (batchRoles){
				if(obj?.roles.containsAll(batchRoles)) {
				  return true
				}else {
				  return false
				}
			}
		})
		hookRoles(nullable:true, validator: { val, obj ->
			if (hookRoles){
				if(obj?.roles.containsAll(hookRoles)) {
					return true
				}else {
					return false
				}
			}
		})
		name(nullable:false,maxSize:200)
		description(nullable:true,maxSize:1000)
		doc(nullable:true)
		receives(nullable:true)
		returns(nullable:true)
		cachedResult(nullable:true)
	}

}
