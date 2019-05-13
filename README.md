
![alt text](https://github.com/orubel/logos/blob/master/beapi_logo_large.png)
# BeApi(tm) Api Framework ( https://www.beapi.io/ )
[ ![Download](https://api.bintray.com/packages/orubel/plugins/api-framework/images/download.svg?version=0.9.8.2) ](https://bintray.com/orubel/plugins/api-framework/0.9.8.2/link)

## Documentation/Installation - http://orubel.github.io/Beapi-API-Framework/
## Forums - http://beapi.freeforums.net/

<!-- ### Backend Implementation - https://github.com/orubel/beapi_backend -->
### Frontend Implementation - https://github.com/orubel/beapi_frontend

***
### Description
The BeAPI Framework is a full featured api automation framework providing a FULL API automation with an AVERAGE response time per call of [0.27 milliseconds per request](https://www.flickr.com/photos/orubel/32194321787/in/dateposted-public/) (Google requires their calls to be UNDER 200 ms). 

Some features include:

- **Automated Batching:** all endpoints are batchable by default with AUTH ROLES assignable to restrict access. Batching can also be TOGGLED to turn this feature ON/OFF per endpoint.

- **Built-in CORS:** Cross Origin Request handling secures all endpoints from domains that you don't want. Added capability for securing different network groups

- **JWT Tokens:** JWT Token handling for Javascript frontends to allow or better abstraction of the VIEW layer

- **Automated Web Hooks:** Enables secured Web Hooks for any endpoint so your developers/users can get push notification on updates.

- **Throttling & Rate/Data Limits:** Data Limits/Rate limits and Throttling for all API's through easy to configure options

- **Autogenerated APIDocs:**  Unlike Swagger/OpenApi which shows the ApiDocs regardless of ROLE, APIDocs are autogenerated based on your ROLE thus showing only the endpoints that you have access to.

- **Shared I/O state:** Also unlike Swagger/OpenApi, the data associated with functionality for REQUEST/RESPONSE does not exist in TWO PLACES and thus can be synchronized making it moire secure, stable, faster and flexible. You can make changes to your apis, security and definitions on the fly all without taking your servers down.

- **API Chaining(tm):** rather than using HATEOASto make a request, get a link, make a request, get a link, make a request, etc... api chaining allows for creation of an 'api monad' wherein the output from a related set of apis can be chained allowing the output from one api to be accepted as the input to the next and so on and be passed with ONE REQUEST AND ONE RESPONSE.

- **Localized API Cache:** returned resources are cached,stored and updated with requesting ROLE/AUTH. Domains extend a base class that auto update this cache upon create/update/delete. This speeds up your api REQUEST/RESPONSE x10

- **Built-In Stats Reporting:** Statistics on all api calls (and errors) are stored and can be called up at any time for reporting tooling.

***

### FAQ

**Q: How hard is this to implement?**  
**A:** BeApi is 'Plug-N-Play'. You dont even have to install the plugin. Implementing for your project is as simple as a one line command:
```
grails create-app name_of_your_app --profile org.grails.profiles:beapi:1.1.2
```

**Q: How do I implement the listener for IO state webhook on my proxy/Message queue?**  
**A:** It merely requires an endpoint to send the data to. As a side project, I may actually supply a simple daemon in the future with ehCache to do this for people.

