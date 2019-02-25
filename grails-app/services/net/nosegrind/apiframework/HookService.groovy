package net.nosegrind.apiframework

import grails.converters.JSON
import grails.converters.XML
import org.grails.validation.routines.UrlValidator
import org.grails.core.artefact.DomainClassArtefactHandler
import grails.core.GrailsDomainClass
import static groovyx.gpars.GParsPool.withPool
import grails.util.Holders

class HookService {

	/**
	 * Application Class
	 */
	GrailsApplication grailsApplication

	/**
	 * Number of processor cores; read from config
	 */
	Integer cores = Holders.grailsApplication.config.apitoolkit.procCores as Integer

    static transactional = false

	/**
	 * Given the  REST Method, the ROLES, the data to be sent and 'service' for which hook is defined,
	 * will sendData(). Exceptions will sendError()
	 * @param String URI of local endpoint being hooked into
	 * @param String data to be sent to all subscribers
	 * @param List Roles associated with given hook endpoint
	 * @param String request method (GET,PUT,POST,DELETE)
	 * @return
	 */
    void postData(String service, String data, List hookRoles,String method) {
		if (hookRoles.size() < 0) {
			String msg = "The hookRoles in your IO State for " + params.controller + "is undefined."
			sendError(msg, service)
		}else if(method=='GET') {
			String msg = "Webhooks are not applicable with GET method. Please check your IO State file as to what endpoints you are using webhooks with."
			sendError(msg, service)
		}else{
			send(data, service)
		}
	}

	/**
	 * Given the data to be sent and 'service' for which hook is defined,
	 * will send data to all 'subscribers'
	 * @param String URI of local endpoint being hooked into
	 * @param String data to be sent to all subscribers
	 * @return
	 */
    private boolean send(String data, String service) {

		def hooks = grailsApplication.getClassForName('net.nosegrind.apiframework.Hook').findAll("from Hook where is_enabled=true and service=?",[service])

		/*
		GrailsDomainClass dc = grailsApplication.getDomainClass('net.nosegrind.apiframework.Hook')
		def tempHook = dc.clazz.newInstance()
		def hooks = tempHook.find("from Hook where service=?",[service])
		*/

		withPool(this.cores) { pool ->

			HttpURLConnection myConn = null
			DataOutputStream os = null
			BufferedReader stdInput = null

			hooks.eachParallel { hook ->
				String format = hook.format.toLowerCase()
				if (hook.attempts >= grailsApplication.config.apitoolkit.attempts) {
					data = [message: 'Number of attempts exceeded. Please reset hook via web interface']
				}


				try {
					URL hostURL = new URL(hook.url.toString())
					myConn = (HttpURLConnection) hostURL.openConnection()
					myConn.setRequestMethod("POST")
					myConn.setRequestProperty("Content-Type", "application/json")
					if (hook?.authorization) {
						myConn.setRequestProperty("Authorization", "${hook.authorization}")
					}
					myConn.setUseCaches(false)
					myConn.setDoInput(true)
					myConn.setDoOutput(true)
					myConn.setReadTimeout(15 * 1000)

					myConn.connect()

					OutputStreamWriter out = new OutputStreamWriter(myConn.getOutputStream())
					out.write(data)
					out.close()

					int code = myConn.getResponseCode()
					myConn.diconnect()

					return code
				} catch (Exception e) {
					try {
						Thread.sleep(15000)
					} catch (InterruptedException ie) {
						println(e)
					}
				} finally {
					if (myConn != null) {
						myConn.disconnect()
					}
				}
				return 400
			}
		}
	}

	/**
	 * Given the data to be sent and 'service' for which hook is defined,
	 * will send error message to all 'subscribers'
	 * @param String URI of local endpoint being hooked into
	 * @param String data to be sent to all subscribers
	 * @return
	 */
	private boolean sendError(String data, String service) {

		def hooks = grailsApplication.getClassForName('net.nosegrind.apiframework.Hook').findAll("from Hook where is_enabled=true and service=?",[service])

		hooks.each { hook ->
			String format = hook.format.toLowerCase()

			String message = 	[message:data]


			HttpURLConnection myConn= null
			DataOutputStream os = null
			BufferedReader stdInput = null
			try{
				URL hostURL = new URL(hook.url.toString())
				myConn= (HttpURLConnection)hostURL.openConnection()
				myConn.setRequestMethod("POST")
				myConn.setRequestProperty("Content-Type", "application/json")
				if(hook?.authorization) {
					myConn.setRequestProperty("Authorization", "${hook.authorization}")
				}
				myConn.setUseCaches(false)
				myConn.setDoInput(true)
				myConn.setDoOutput(true)
				myConn.setReadTimeout(15*1000)

				myConn.connect()

				OutputStreamWriter out = new OutputStreamWriter(myConn.getOutputStream())
				out.write(message)
				out.close()

				int code =  myConn.getResponseCode()
				myConn.diconnect()

				return code
			}catch (Exception e){
				try{
					Thread.sleep(15000)
				}catch (InterruptedException ie){
					println(e)
				}
			} finally{
				if (myConn!= null){
					myConn.disconnect()
				}
			}
			return 400
		}
	}

	/**
	 * Determines if object is valid domain class and return object as a Map
	 * @param Object an object assumed to be a domain object
	 * @return Data as a Map
	 */
	Map formatDomainObject(Object data){
	    def nonPersistent = ["log", "class", "constraints", "properties", "errors", "mapping", "metaClass","maps"]
	    def newMap = [:]
	    data.getProperties().each { key, val ->
	        if (!nonPersistent.contains(key)) {
				if(grailsApplication.isDomainClass(val.getClass())){
					newMap.put key, val.id
				}else{
					newMap.put key, val
				}
	        }
	    }
		return newMap
	}

	/*
	Map processMap(Map data,Map processor){
		processor.each() { key, val ->
			if(!val?.trim()){
				data.remove(key)
			}else{
				def matcher = "${data[key]}" =~ "${data[key]}"
				data[key] = matcher.replaceAll(val)
			}
		}
		return data
	}
	
	boolean validateUrl(String url){
		try {
			String[] schemes = ["http", "https"]
			UrlValidator urlValidator = new UrlValidator(schemes)
			if (urlValidator.isValid(url)) {
				return true
			} else {
				return false
			}
		}catch(Exception e){
			println(e)
		}
		return false
	}
	*/
}
