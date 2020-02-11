/*
 * Copyright 2013-2019 Beapi.io
 * API Chaining(R) 2019 USPTO
 *
 * Licensed under the MPL-2.0 License;
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.nosegrind.apiframework

import org.grails.web.json.JSONObject

/**
 * @author Owen Rubel
 */
class IostateController {

	def springSecurityService
	def apiObjectService
	def apiCacheService
	def webhookService

	HashMap update() {
		if(isSuperuser()){
		    def file = request.getFile('filename')
		    
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
			HashMap model = [name:cache.name,cacheversion:cache.cacheversion]
			webhookService.postData('Iostate', model,'update')
			return ['iostate':model]
		}
	}


	protected boolean isSuperuser() {
		springSecurityService.principal.authorities*.authority.any { grailsApplication.config.apitoolkit.admin.roles.contains(it) }
	}
}
