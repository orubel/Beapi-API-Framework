import grails.util.Metadata

String apiVersion = Metadata.current.getApplicationVersion()
// fix for dots not working with spring security pathing
String entryPoint = "/v${apiVersion}".toString()
String batchEntryPoint = "/b${apiVersion}".toString()
String chainEntryPoint = "/c${apiVersion}".toString()
String metricsEntryPoint = "/m${apiVersion}".toString()
//String domainEntryPoint = "d${apiVersion}".toString()

cors.headers = ['Access-Control-Allow-Headers': 'Content-Type, Authorization']

// The ACCEPT header will not be used for content negotiation for user agents containing the following strings (defaults to the 4 major rendering engines)
grails.mime.use.accept.header = false // Default value is true.
grails.mime.disable.accept.header.userAgents = ['Gecko', 'WebKit', 'Presto', 'Trident']
grails.mime.types = [ // the first one is the default format
                      all:           '*/*', // 'all' maps to '*' or the first available format in withFormat
                      atom:          'application/atom+xml',
                      css:           'text/css',
                      csv:           'text/csv',
                      form:          'application/x-www-form-urlencoded',
                      html:          ['text/html','application/xhtml+xml'],
                      js:            'text/javascript',
                      json:          ['application/json', 'text/json'],
                      multipartForm: 'multipart/form-data',
                      rss:           'application/rss+xml',
                      text:          'text/plain',
                      hal:           ['application/hal+json','application/hal+xml'],
                      xml:           ['text/xml', 'application/xml']
]

// URL Mapping Cache Max Size, defaults to 5000
grails.urlmapping.cache.maxsize = 1000

// Legacy setting for codec used to encode data with ${}
grails.views.default.codec = "json"


// move to RequestMap once stabilized
grails.plugin.springsecurity.securityConfigType = "InterceptUrlMap"
grails.plugin.springsecurity.rejectIfNoRule = false
grails.plugin.springsecurity.fii.rejectPublicInvocations = false

grails.plugin.springsecurity.userLookup.userDomainClassName = 'net.nosegrind.apiframework.Person'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'net.nosegrind.apiframework.PersonRole'
grails.plugin.springsecurity.authority.className = 'net.nosegrind.apiframework.Role'

// grails.plugin.springsecurity.rememberMe.persistent = true		  // grails.plugin.springsecurity.rememberMe.persistent = true
// grails.plugin.springsecurity.rememberMe.persistentToken.domainClassName = 'net.nosegrind.apiframework.PersistentLogin'		  // grails.plugin.springsecurity.rememberMe.persistentToken.domainClassName = 'net.nosegrind.apiframework.PersistentLogin'

grails.plugin.springsecurity.adh.errorPage = null

grails.plugin.springsecurity.providerNames = ['daoAuthenticationProvider', 'anonymousAuthenticationProvider', 'rememberMeAuthenticationProvider']

grails.plugin.springsecurity.rememberMe.alwaysRemember = true
grails.plugin.springsecurity.rememberMe.cookieName = 'apiTest'
grails.plugin.springsecurity.rememberMe.key = '_grails_'

grails.plugin.springsecurity.logout.postOnly = false
grails.plugin.springsecurity.ui.encodePassword = false
grails.plugin.springsecurity.auth.forceHttps = false
grails.plugin.springsecurity.auth.loginFormUrl = '/login/auth/'
//grails.plugin.springsecurity.auth.ajaxLoginFormUrl = '/login/authAjax/'

//grails.plugin.springsecurity.successHandler.defaultTargetUrl = '/login/ajaxSuccess'
//grails.plugin.springsecurity.failureHandler.defaultFailureUrl = '/login/ajaxDenied'
//grails.plugin.springsecurity.failureHandler.ajaxAuthFailUrl = '/login/ajaxDenied'

grails.plugin.springsecurity.interceptUrlMap = [
        [pattern:'/api/**',            access:['permitAll']],

        [pattern:"/${entryPoint}/**",   access:["permitAll && \"{'GET','PUT','POST','DELETE','OPTIONS'}\".contains(request.getMethod())"]],
        [pattern:'/',                   access:['permitAll']],
        [pattern:'/error',              access:['permitAll']],
        [pattern:'/error/**',           access:['permitAll']],
        [pattern:'/index',              access:['permitAll']],
        [pattern:'/index.gsp',          access:['permitAll']],
        [pattern:'/assets/**',          access:['permitAll']],
        [pattern:'/auth',               access:['permitAll']],
        [pattern:'/auth/**',            access:['permitAll']],
        [pattern:'/login',              access:["permitAll"]],
        [pattern:'/login/**',           access:["permitAll"]],
        [pattern:'/logout',             access:["permitAll"]],
        [pattern:'/logout/**',          access:["permitAll"]]
]

grails.plugin.springsecurity.rest.login.active  = true
grails.plugin.springsecurity.rest.login.endpointUrl = '/api/login'
grails.plugin.springsecurity.rest.logout.endpointUrl = '/api/logout'
grails.plugin.springsecurity.rest.login.failureStatusCode = '401'

grails.plugin.springsecurity.rest.login.useJsonCredentials  = true
grails.plugin.springsecurity.rest.login.usernamePropertyName =  'username'
grails.plugin.springsecurity.rest.login.passwordPropertyName =  'password'

grails.plugin.springsecurity.rest.token.generation.useSecureRandom  = true
grails.plugin.springsecurity.rest.token.generation.useUUID  = false

grails.plugin.springsecurity.rest.token.storage.useGorm = true
grails.plugin.springsecurity.rest.token.storage.gorm.tokenDomainClassName   = 'net.nosegrind.apiframework.AuthenticationToken'
grails.plugin.springsecurity.rest.token.storage.gorm.tokenValuePropertyName = 'tokenValue'
grails.plugin.springsecurity.rest.token.storage.gorm.usernamePropertyName   = 'username'

grails.plugin.springsecurity.rest.token.rendering.usernamePropertyName  = 'username'
grails.plugin.springsecurity.rest.token.rendering.authoritiesPropertyName = 'authorities'

grails.plugin.springsecurity.rest.token.validation.useBearerToken = true
//grails.plugin.springsecurity.rest.token.validation.active   = true
//grails.plugin.springsecurity.rest.token.validation.headerName   = 'X-Auth-Token'
//grails.plugin.springsecurity.rest.token.validation.endpointUrl  = '/api/validate'

grails.plugin.springsecurity.rememberMe.alwaysRemember = true
grails.plugin.springsecurity.rememberMe.persistent = false
//grails.plugin.springsecurity.rememberMe.persistentToken.domainClassName = 'net.nosegrind.apiframework.PersistentLogin'

// makes the application easier to work with
grails.plugin.springsecurity.logout.postOnly = false

grails.plugin.springsecurity.useSecurityEventListener = false

// rest token
grails.plugin.springsecurity.rest.token.storage.jwt.useSignedJwt = true
grails.plugin.springsecurity.rest.token.storage.jwt.secret = 'szdxgerklaslcxhiouweas'
grails.plugin.springsecurity.rest.token.storage.jwt.expiration = -1


