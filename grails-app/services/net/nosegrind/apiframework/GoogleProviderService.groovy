package net.nosegrind.apiframework


import org.springframework.security.core.GrantedAuthority;

// google verifier
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.json.JsonFactory;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.http.javanet.NetHttpTransport;
//import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.http.HttpTransport;

import grails.gorm.transactions.Transactional
import groovy.util.logging.Slf4j

@Transactional
@Slf4j
class GoogleProviderService {



    private static final String CLIENT_ID = "AIzaSyBbhaneS8R3wz43wwtl9XNke255qU61xY4"
    //private static final String CLIENT_ID = "615500127472-uvaj58iufu76pp64ej5a7dqp43ae8m9q.apps.googleusercontent.com";

    def verify(String provider, String tokenString) {
        println("provider/auth")
        def out = verifyWithProvider(provider,tokenString)
        if(out==[:]){
            println('token not verified')
        }else{
            println out
        }
	return out
    }

    def verifyWithProvider(String provider, String tokenString) {
        switch (provider) {
            case 'google':
                return getGooglePayload(provider,tokenString)
                break
            case 'ios':
                break
            case 'twitter':
            case 'facebook':
            default:
                break
        }
    }


    private LinkedHashMap getGooglePayload(String provider, String tokenString) throws Exception {
        // Default transportation layer for Google Apis Java client.
        HttpTransport TRANSPORT = new NetHttpTransport();

        // Default JSON factory for Google Apis Java client.
        JsonFactory JSON_FACTORY = new JacksonFactory();

        GoogleIdTokenVerifier googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(TRANSPORT, JSON_FACTORY)
                .setAudience(Collections.singletonList((String)CLIENT_ID))
                .build();

        println("verifier called...")

        GoogleIdToken token = googleIdTokenVerifier.verify(tokenString)
        println("token:"+token)
        Payload payload = token.getPayload();
        println(payload.getEmail())

        LinkedHashMap out = [:]
        if (token != null) {
            //Payload payload = token.getPayload();

            // Print user identifier
            out['userId'] = payload.getSubject();
            //System.out.println("User ID: " + userId);

            // Get profile information from payload
            out['email'] = payload.getEmail();

            out['emailVerified'] = Boolean.valueOf(payload.getEmailVerified());
            out['name'] = (String) payload.get("name");
            out['pictureUrl'] = (String) payload.get("picture");
            out['locale'] = (String) payload.get("locale");
        }else {
            println('token not verified')
        }
        return out
    }
}
