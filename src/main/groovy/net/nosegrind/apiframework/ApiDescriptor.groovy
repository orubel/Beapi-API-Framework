package net.nosegrind.apiframework


import grails.validation.Validateable
import grails.compiler.GrailsCompileStatic

/**
 *
 * Api Object used for caching all data associated with endpoint
 * @author Owen Rubel
 *
 * @see ApiCommLayer
 * @see BatchInterceptor
 * @see ChainInterceptor
 *
 */
//@GrailsCompileStatic
class ApiDescriptor implements Validateable {

	boolean empty = false
	String defaultAction
	List deprecated
	String method
	Set pkey
	Set fkeys
	Set roles
	Set batchRoles
	Set hookRoles
	String name
    String description
	Map doc
    LinkedHashMap<String,ParamsDescriptor> receives
    LinkedHashMap<String,ParamsDescriptor> returns
	LinkedHashMap cachedResult
	LinkedHashMap stats


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
		stats(nullable:true)
	}

}
