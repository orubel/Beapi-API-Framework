import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler

beans = {
    requestForwarder(grails.api.framework.RequestForwarder)
    tokenStorageService(net.nosegrind.apiframework.ApiTokenStorageService)

}
