package net.nosegrind.apiframework

import grails.util.Metadata


class ApiFrameworkUrlMappings {

    static mappings = {

        String apiVersion = Metadata.current.getApplicationVersion()
        String api = "v${apiVersion}"
        String batchEntryPoint = "b${apiVersion}"
        String chainEntryPoint = "c${apiVersion}"
        String profilerEntryPoint = "p${apiVersion}"


        // REGULAR API ENDPOINTS
        "/$api/$controller/$action/$id?**"{
            entryPoint = api
            parseRequest = true
        }

        "/$api/$controller/$action" {
            if(action?.toInteger()==action && action!=null){
                id=action
                action = null
            }
            entryPoint = api
        }


        "/${api}-$apiObjectVersion/$controller/$action/$id?**" {
            entryPoint = api
            apiObject = apiObjectVersion
            constraints {
                apiObjectVersion(matches:/^[0-9]?[0-9]?(\\.[0-9][0-9]?)?/)
            }
        }

        "/${api}-$apiObjectVersion/$controller/$action" {
            if(action?.toInteger()==action && action!=null){
                id=action
                action = null
            }
            entryPoint = api
            apiObject = apiObjectVersion
            constraints {
                apiObjectVersion(matches:/^[0-9]?[0-9]?(\\.[0-9][0-9]?)?/)
            }
        }


        // BATCH API ENDPOINTS
        "/$batchEntryPoint/$controller/$action/$id?**"{
            entryPoint = batchEntryPoint
        }

        "/$batchEntryPoint/$controller/$action"{
            if(action?.toInteger()==action && action!=null){
                id=action
                action = null
            }
            entryPoint = batchEntryPoint
        }

        "/${batchEntryPoint}-$apiObjectVersion/$controller/$action/$id**" {
            entryPoint = batchEntryPoint
            apiObjectVersion = apiObjectVersion
            constraints {
                apiObjectVersion(matches:/^[0-9]?[0-9]?(\\.[0-9][0-9]?)?/)
            }
        }


        "/${batchEntryPoint}-$apiObjectVersion/$controller/$action" {
            if(action?.toInteger()==action && action!=null){
                id=action
                action = null
            }
            entryPoint = batchEntryPoint
            apiObjectVersion = apiObjectVersion
            constraints {
                apiObjectVersion(matches:/^[0-9]?[0-9]?(\\.[0-9][0-9]?)?/)
            }
        }


        // CHAIN API ENDPOINTS
        "/$chainEntryPoint/$controller/$action/$id?**"{
            entryPoint = chainEntryPoint
        }

        "/$chainEntryPoint/$controller/$action"{
            if(action?.toInteger()==action && action!=null){
                id=action
                action = null
            }
            entryPoint = chainEntryPoint
        }

        "/${chainEntryPoint}-$apiObjectVersion/$controller/$action/$id**" {
            entryPoint = chainEntryPoint
            apiObjectVersion = apiObjectVersion
            constraints {
                apiObjectVersion(matches:/^[0-9]?[0-9]?(\\.[0-9][0-9]?)?/)
            }
        }

        "/${chainEntryPoint}-$apiObjectVersion/$controller/$action" {
            if(action?.toInteger()==action && action!=null){
                id=action
                action = null
            }
            entryPoint = chainEntryPoint
            apiObjectVersion = apiObjectVersion
            constraints {
                apiObjectVersion(matches:/^[0-9]?[0-9]?(\\.[0-9][0-9]?)?/)
            }
        }

        // PROFILER API ENDPOINTS
        "/$profilerEntryPoint/$controller/$action/$id?**"{
            entryPoint = profilerEntryPoint
        }

        "/$profilerEntryPoint/$controller/$action"{
            if(action?.toInteger()==action && action!=null){
                id=action
                action = null
            }
            entryPoint = profilerEntryPoint
        }

        "/${profilerEntryPoint}-$apiObjectVersion/$controller/$action/$id**" {
            entryPoint = profilerEntryPoint
            apiObjectVersion = apiObjectVersion
            constraints {
                apiObjectVersion(matches:/^[0-9]?[0-9]?(\\.[0-9][0-9]?)?/)
            }
        }

        "/${profilerEntryPoint}-$apiObjectVersion/$controller/$action" {
            if(action?.toInteger()==action && action!=null){
                id=action
                action = null
            }
            entryPoint = profilerEntryPoint
            apiObjectVersion = apiObjectVersion
            constraints {
                apiObjectVersion(matches:/^[0-9]?[0-9]?(\\.[0-9][0-9]?)?/)
            }
        }

        "200"{
            id = '200'
            controller = "error"
        }
        "302"{
            id = '302'
            controller = "error"
        }
        "304"{
            id = '304'
            controller = "error"
        }
        "400"{
            id = '400'
            controller = "error"
        }
        "401"{
            id = '401'
            controller = "error"
        }
        "403" {
            id = '403'
            controller = "error"
        }
        "404" {
            id = '404'
            controller = "error"
        }
        "405" {
            id = '405'
            controller = "error"
        }
        "409"{
            id = '409'
            controller = "error"
        }
        "412"{
            id = '412'
            controller = "error"
        }
        "413"{
            id = '413'
            controller = "error"
        }
        "416"{
            id = '416'
            controller = "error"
        }
        "500" {
            id = '500'
            controller = "error"
        }
        "500" {
            id = '500'
            exception = NullPointerException
            controller = "error"
        }

        "503"{
            id = '503'
            controller = "error"
        }
    }

}
