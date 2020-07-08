package net.nosegrind.apiframework

import grails.gorm.transactions.Transactional

import grails.util.Holders
import grails.core.GrailsApplication

import org.grails.web.json.JSONObject

import org.springframework.context.ApplicationContext

@Transactional
class IostateParserService {

    GrailsApplication grailsApplication
    ApiCacheService apiCacheService = new ApiCacheService()

    /*
    Method to dynamically Generate the values for schemas using the Domain Objects
     */
    void parseValues(JSONObject json){
        LinkedHashMap values = [:]

        json.OBJECTS.each(){ k, v ->

            def clazz = Holders.grailsApplication.getDomainClass(k)
            def props = clazz.getProperties()
            def props2 = clazz.getConstrainedProperties()
            props.each() { it ->
                // attribute
                if (it.getFieldName() != it.getName()) {
                    Class typ = it.getType()
                    values[it.getName()] = [:]
                    values[it.getName()]['name'] = typ.getSimpleName()
                    values[it.getName()]['constraints'] = [:]
                }
            }

            props2.each(){ k2,v2 ->
                def uniq = null
                if(v2.getAppliedConstraint('unique')?.valid){
                    uniq = v2.getAppliedConstraint('unique').valid
                }

                LinkedHashMap constraints = []
                if(v2.getInList()) { constraints['list'] = v2.getInList() }
                try {
                    if(v2.getMatches()) { constraints['regx'] = v2.getMatches() }
                }catch(Exception e){}
                if(v2.getMaxSize()) { constraints['maxSize'] = v2.getMaxSize() }
                if(v2.getMinSize()) { constraints['minSize'] = v2.getMinSize() }
                if(v2.getNotEqual()) { constraints['notEqual'] = v2.getNotEqual() }
                if(v2.getOrder()) { constraints['order'] = v2.getOrder() }
                if(v2.getRange()) { constraints['range'] = v2.getRange() }
                if(v2.getScale()) { constraints['scale'] = v2.getScale() }
                if(v2.getSize()) { constraints['size'] = v2.getSize() }
                if(v2.isBlank()) { constraints['isBlank'] = v2.isBlank() }
                try{
                    if(v2.isCreditCard()) { constraints['isCC'] = v2.isCreditCard() }
                }catch(Exception e){}
                if(v2.isDisplay()) { constraints['isDisplay'] = v2.isDisplay() }
                if(v2.isEditable()) { constraints['isEditable'] = v2.isEditable() }
                try {
                    if (v2.isEmail()) { constraints['isEmail'] = v2.isEmail() }
                }catch(Exception e){}

                try {
                    if (v2.isNullable()) { constraints['isNullable'] = v2.isNullable() }
                }catch(Exception e){}

                try {
                    if (v2.isPassword()) { constraints['isPassword'] = v2.isPassword() }
                }catch(Exception e){}

                try {
                    if (v2.isUrl()) { constraints['isUrl'] = v2.isUrl() }
                }catch(Exception e){}

                if(uniq){ constraints['isUnique'] = uniq }

                values[k2]['constraints'] = constraints
            }
        }

        // now we need to write the values to the file and also return the values
        println(values)
    }

    LinkedHashMap getConstraints(){
        linkedHashMap constraints = ['isNullable':null,'isBlank':null,'isUnique':null,'isEmail':null,'maxSize':null]
    }


    LinkedHashMap parseJson(String apiName,JSONObject json, ApplicationContext applicationContext){
        //parseValues(json)

        LinkedHashMap methods = [:]

        String networkGrp = json.NETWORKGRP
        String testUser = json.TESTUSER
        json.VERSION.each() { vers ->
            //def versKey = vers.key
            String defaultAction = (vers.value['DEFAULTACTION'])?vers.value.DEFAULTACTION:'index'

            //Set testOrder = (vers.value['TESTORDER'])?vers.value.TESTORDER:[]



            Set deprecated = (vers.value.DEPRECATED)?vers.value.DEPRECATED:[]
            String domainPackage = (vers.value.DOMAINPACKAGE!=null || vers.value.DOMAINPACKAGE?.size()>0)?vers.value.DOMAINPACKAGE:null

            String actionname
            vers.value.URI.each() { it ->

                //def cache = apiCacheService.getApiCache(apiName.toString())
                //def cache = (temp?.get(apiName))?temp?.get(apiName):[:]

                methods['cacheversion'] = 1

                JSONObject apiVersion = json.VERSION[vers.key]

                actionname = it.key

                ApiDescriptor apiDescriptor
                //Map apiParams

                String apiMethod = it.value.METHOD
                String apiDescription = it.value.DESCRIPTION

                List apiRoles = (it.value.ROLES.DEFAULT)?it.value.ROLES.DEFAULT as List:null
                List networkRoles = grails.util.Holders.grailsApplication.config.apitoolkit.networkRoles."${networkGrp}"
                if(apiRoles) {
                    if(!(apiRoles-networkRoles.intersect(apiRoles).isEmpty())){
                        throw new Exception("[Runtime :: parseJson] : ${it.key}.ROLES.DEFAULT does not match any networkRoles for ${apiName} NETWORKGRP :",e)
                    }
                }else{
                    apiRoles = networkRoles
                }


                Set batchRoles = it.value.ROLES.BATCH
                Set hookRoles = it.value.ROLES.HOOK

                String uri = it.key
                apiDescriptor = createApiDescriptor(networkGrp, apiName, apiMethod, apiDescription, apiRoles, batchRoles, hookRoles, uri, json.get('VALUES'), apiVersion)
                if(!methods[vers.key]){
                    methods[vers.key] = [:]
                }

                if(!methods['values']){
                    methods['values'] = [:]
                    methods['values'] = json.get('VALUES')
                }

                if(!methods['currentStable']){
                    methods['currentStable'] = [:]
                    methods['currentStable']['value'] = json.CURRENTSTABLE
                }

                if(!methods[vers.key]['deprecated']){
                    methods[vers.key]['deprecated'] = []
                    methods[vers.key]['deprecated'] = deprecated
                }

                if(!methods[vers.key]['defaultAction']){
                    methods[vers.key]['defaultAction'] = defaultAction
                }

                if(!methods[vers.key]['testOrder']){
                    methods[vers.key]['testOrder'] = []
                    //methods[vers.key]['testOrder'] = testOrder
                }

                if(!methods[vers.key]['testUser']){
                    methods[vers.key]['testUser'] = testUser
                }

                methods[vers.key][actionname] = apiDescriptor

            }

            if(methods){
                def cache = apiCacheService.setApiCache(apiName,methods)

                cache[vers.key].each(){ key1,val1 ->

                    if(!['deprecated','defaultAction','testOrder','testUser'].contains(key1)){
                        apiCacheService.setApiCache(apiName,key1, val1, vers.key)
                    }
                }

            }

        }
        return methods
    }


    private ApiDescriptor createApiDescriptor(String networkGrp, String apiname,String apiMethod, String apiDescription, List apiRoles, Set batchRoles, Set hookRoles, String uri, JSONObject values, JSONObject json){
        LinkedHashMap<String, ParamsDescriptor> apiObject = [:]
        ApiParams param = new ApiParams()

        Set fkeys = []
        Set pkeys= []
        List keys = []
        //try {
        values.each { k, v ->

            // get all vales from object -> k
            keys.add(k)
            v.reference = (v.reference) ? v.reference : 'self'
            param.setParam(v.type, k)

            String hasKey = (v?.key) ? v.key : null

            if (hasKey != null) {
                param.setKey(hasKey)

                String hasReference = (v?.reference) ? v.reference : 'self'
                param.setReference(hasReference)

                if (['FOREIGN', 'INDEX', 'PRIMARY'].contains(v.key?.toUpperCase())) {
                    switch (v.key) {
                        case 'INDEX':
                            if (v.reference != 'self') {
                                LinkedHashMap fkey = ["${k}": "${v.reference}"]
                                fkeys.add(fkey)
                            }
                            break;
                        case 'FOREIGN':
                            LinkedHashMap fkey = ["${k}": "${v.reference}"]
                            fkeys.add(fkey)
                            break;
                        case 'PRIMARY':
                            pkeys.add(k)
                            break;
                    }
                }
            }

            String hasDescription = (v?.description) ? v.description : ''
            param.setDescription(hasDescription)

            if (v.mockData!=null) {
                if(v.mockData.isEmpty()){
                    param.setMockData('')
                }else {
                    param.setMockData(v.mockData.toString())
                }
            } else {
                throw new Exception("[Runtime :: createApiDescriptor] : MockData Required for type '" + k + "' in IO State[" + apiname + "]")
            }

            // collect api vars into list to use in apiDescriptor

            apiObject[param.param.name] = param.toObject()
        }
        //}catch(Exception e){
        //    throw new Exception("[Runtime :: createApiDescriptor] : Badly Formatted IO State :",e)
        //}

        LinkedHashMap receives = getIOSet(json.URI[uri]?.REQUEST,apiObject,keys,apiname)
        LinkedHashMap returns = getIOSet(json.URI[uri]?.RESPONSE,apiObject,keys,apiname)

        ApiDescriptor service = new ApiDescriptor(
                'empty':false,
                'method':"$apiMethod",
                'networkGrp': "$networkGrp",
                'pkey':pkeys,
                'fkeys':fkeys,
                'description':"$apiDescription",
                'roles':apiRoles,
                'batchRoles':[],
                'hookRoles':[],
                'doc':[:],
                'receives':receives,
                'returns':returns,
                'cachedResult': [:]
        )

        // override networkRoles with 'DEFAULT' in IO State



        batchRoles.each{
            if(!apiRoles.contains(it)){
                throw new Exception("[Runtime :: createApiDescriptor] : BatchRoles in IO State[" + apiname + "] do not match default/networkRoles")
            }
        }
        service['batchRoles'] = batchRoles

        hookRoles.each{
            if(!apiRoles.contains(it)){
                throw new Exception("[Runtime :: createApiDescriptor] : HookRoles in IO State[" + apiname + "] do not match default/networkRoles")
            }
        }
        service['hookRoles'] = hookRoles

        return service
    }

    private LinkedHashMap getIOSet(JSONObject io,LinkedHashMap apiObject,List valueKeys,String apiName){
        LinkedHashMap<String,ParamsDescriptor> ioSet = [:]

        io.each{ k, v ->
            // init
            if(!ioSet[k]){ ioSet[k] = [] }

            def roleVars=v.toList()
            roleVars.each{ val ->
                if(v.contains(val)){
                    if(!ioSet[k].contains(apiObject[val])) {
                        if (apiObject[val]){
                            ioSet[k].add(apiObject[val])
                        }else {
                            throw new Exception("VALUE '"+val+"' is not a valid key for IO State [${apiName}]. Please check that this 'VALUE' exists")
                        }
                    }
                }
            }
        }

        def permitAll = ioSet['permitAll']
        ioSet.each(){ key, val ->
            if(key!='permitAll'){
                permitAll.each(){ it ->
                    if(!ioSet[key].contains("-${it}")) {
                        ioSet[key].add(it)
                    }
                }
            }
        }

        //List ioKeys = []
        ioSet.each(){ k, v ->
            List ioKeys = v.collect(){ it -> it.name }
            if (!ioKeys.minus(valueKeys).isEmpty()) {
                throw new Exception("[Runtime :: getIOSet] : VALUES for IO State [" + apiName + "] do not match REQUEST/RESPONSE values for endpoints")
            }
        }

        return ioSet
    }
}
