package net.nosegrind.apiframework

import org.grails.web.json.JSONObject

class IostateController {

	def springSecurityService
	def apiObjectService
	def apiCacheService
	def webhookService

	LinkedHashMap update() {
		if(isSuperuser()){
		    def file = request.getFile('iostate')
		    
		    if (file.empty) {
		        render(status:HttpServletResponse.SC_BAD_REQUEST)
		        return null
		    }
		    
			JSONObject json = JSON.parse(file.text)
			if(!apiObjectService.parseFile(json)){
				render(status:HttpServletResponse.SC_BAD_REQUEST)
				return null
			}
			
			def cache = apiCacheService.getApiCache(json.NAME)
			LinkedHashMap model = [name:cache.name,cacheversion:cache.cacheversion]
			webhookService.postData('Iostate', model,'update')
			return ['iostate':model]
		}
	}


	protected boolean isSuperuser() {
		springSecurityService.principal.authorities*.authority.any { grailsApplication.config.apitoolkit.admin.roles.contains(it) }
	}
}
