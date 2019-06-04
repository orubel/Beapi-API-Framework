/*
 * Copyright 2013-2019 Beapi.io
 * API Chaining(R) 2019 USPTO
 *
 * Licensed under the MIT License;
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.nosegrind.apiframework

import net.nosegrind.apiframework.PingService


/**
 * Provides callbacks for the server; mainly used for webhooks
 * the request/response.
 * @author Owen Rubel
 *
 * @see ApiFrameworkInterceptor
 * @see BatchkInterceptor
 * @see ChainInterceptor
 *
 */
class ServerController {

    def pingService

    HashMap pingServers(){
        HashMap list
        Arch servers = Arch.list()
        servers.each(){
            list.add(it.url)
        }
        HashMap servers = pingService.send(list)

        return [server: [servers:servers]]
    }

    HashMap ping(){
        return [server:[ping:true]]
    }

}
