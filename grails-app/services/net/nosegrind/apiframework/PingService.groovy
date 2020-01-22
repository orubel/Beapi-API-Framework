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


import static groovyx.gpars.GParsPool.withPool
import grails.util.Holders
import grails.core.GrailsApplication
import grails.util.Metadata

import javax.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * A class for 'pinging' all the other API servers in your architecture to test for uptime
 *
 * @author Owen Rubel
 */
class PingService {

    /**
     * Application Class
     */
    GrailsApplication grailsApplication



    /**
     * Number of processor cores; read from config
     */
    Integer cores = Holders.grailsApplication.config.apitoolkit.procCores as Integer
    String entryPoint = "v${Metadata.current.getProperty(Metadata.APPLICATION_VERSION, String.class)}"
    static transactional = false

    private HttpServletRequest getRequest(){
        HttpServletRequest request = ((ServletRequestAttributes) RCH.currentRequestAttributes()).getRequest()
        return request
    }

    /**
     * Given the data to be sent and 'service' for which hook is defined,
     * will send data to all 'subscribers'
     * @param String URI of local endpoint being hooked into
     * @param String data to be sent to all subscribers
     * @return
     */
    private HashMap send(List servers) {
        //List servers = Holders.grailsApplication.config.apitoolkit.apiServer as List
        HashMap results = [:]
        HttpServletRequest request = getRequest()

        withPool(this.cores) { pool ->
            HttpURLConnection conn = null
            servers.eachParallel { server ->

                String endpoint = server + "/" + entryPoint + "/arch/ping"

                String token = request.getHeader("Authorization")

                try {
                    URL hostURL = new URL(endpoint)
                    conn = (HttpURLConnection) hostURL.openConnection()
                    conn.setRequestMethod("GET")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", "${token}")

                    conn.setUseCaches(false)
                    conn.setDoInput(true)
                    conn.setDoOutput(true)
                    conn.setReadTimeout(15 * 1000)
                    conn.connect()
                    int code = conn.getResponseCode()
                    if (HttpURLConnection.HTTP_OK == code) {
                        println("200:"+results)
                        results[endpoint] = 'true'
                    }else{
                        println(code+":"+results)
                        results[endpoint] = 'false'
                    }
                } finally {
                    conn.disconnect()
                }

            }
        }
        return results
    }

}

