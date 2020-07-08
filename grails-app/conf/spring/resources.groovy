/*
 * Academic Free License ("AFL") v. 3.0
 * Copyright 2014-2017 Owen Rubel
 *
 * IO State (tm) Owen Rubel 2014
 * API Chaining (tm) Owen Rubel 2013
 *
 *   https://opensource.org/licenses/AFL-3.0
 */

import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler]

// Place your Spring DSL code here
beans = {
    requestForwarder(grails.api.framework.RequestForwarder)
    tokenStorageService(net.nosegrind.apiframework.ApiTokenStorageService)
}
