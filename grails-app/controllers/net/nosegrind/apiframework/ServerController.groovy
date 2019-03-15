package net.nosegrind.apiframework

import net.nosegrind.apiframework.PingService


/**
 * Provides callbacks for the server; mainly used for webhooks
 * the request/response.
 *
 * @see ApiFrameworkInterceptor
 * @see BatchkInterceptor
 * @see ChainInterceptor
 *
 */
class ServerController {

    def pingService

    LinkedHashMap pingServers(){
        LinkedHashMap servers = pingService.send()

        return [server: [servers:servers]]
    }

    LinkedHashMap ping(){
        return [server:[ping:true]]
    }

}
