# clj-oauth2

This library implements the OAuth 2.0 protocol for the Clojure
programming language. Currently, only the client side is implemented
and of that only the "Authorization Code" grant type. It aims to
comply with the future [RFC which is currently in draft
status](http://tools.ietf.org/html/draft-ietf-oauth-v2-12) but also
contains work-arounds for popular but non-compliant OAuth 2.0
implementations such as Facebook to be practical.

clj-oauth2 wraps clj-http for accessing protected resources.

## Basic Usage

```clojure
(:require [clj-oauth2.client :as oauth2])

(def facebook-oauth2
  {:authorization-uri "https://graph.facebook.com/oauth/authorize"
   :access-token-uri "https://graph.facebook.com/oauth/access_token"
   :redirect-uri "http://example.com/oauth2-callback"
   :client-id "1234567890"
   :client-secret "0987654321"
   :access-query-param :access_token
   :scope ["user_photos" "friends_photos"]
   :grant-type "authorization_code"})

;; redirect user to (:uri auth-req) afterwards
(def auth-req
  (oauth2/make-auth-request facebook-oauth2 "some-csrf-protection-string"))


;; auth-resp is a keyword map of the query parameters added to the
;; redirect-uri by the authorization server
;; e.g. {:code "abc123"}
(def access-token
  (oauth2/get-access-token facebook-oauth2 auth-resp auth-req))

;; access protected resource
(oauth2/get "https://graph.facebook.com/me" {:oauth2 access-token})
```

## Ring Middleware

```clojure
(:require [clj-oauth2.client :as oauth2]
          [clj-oauth2.ring :as oauth2-ring])

(def login-uri
  (get (System/getenv) "LOGIN_URI" "https://login.salesforce.com"))

(def force-com-oauth2
  {:authorization-uri (str login-uri "/services/oauth2/authorize")
   :access-token-uri (str login-uri "/services/oauth2/token")
   :redirect-uri (System/getenv "REDIRECT_URI")
   :client-id (System/getenv "CLIENT_ID")
   :client-secret (System/getenv "CLIENT_SECRET")
   :scope ["id" "api" "refresh_token"]
   :grant-type "authorization_code"
   :force-https (System/getenv "FORCE_HTTPS") ; on Heroku the app thinks it is always http
   :trace-messages (Boolean/valueOf (get (System/getenv) "DEBUG" "false"))
   :get-state oauth2-ring/get-state-from-session
   :put-state oauth2-ring/put-state-in-session
   :get-target oauth2-ring/get-target-from-session
   :put-target oauth2-ring/put-target-in-session
   :get-oauth2-data oauth2-ring/get-oauth2-data-from-session
   :put-oauth2-data oauth2-ring/put-oauth2-data-in-session
   :exclude #"^/public.*"})

 ; This is the mapping of URL paths to actions
 (defroutes handler
   ; Just do a 'describe' on the Account object and dump the resulting
   ; output
   (GET "/" 
     {params :params session :session oauth :oauth} 
       (let [url (str 
                   (:instance_url (:params oauth)) 
                   "/services/data/v24.0/sobjects/Account/describe/")
             response (oauth2/get url {:oauth2 oauth})]
        {:headers {"Content-type" "text/plain; charset=UTF-8"}
         :body (with-out-str (pprint response))}))
   (route/files "/public" {:root "www/public"})
   (route/not-found "Page not found"))

 ; Set up the wrappers
 (def app 
    (-> handler 
        (wrap-oauth2 force-com-oauth2)
        wrap-session 
        wrap-keyword-params
        wrap-params))

 (defn -main []
   (let [port (Integer/parseInt (get (System/getenv) "PORT" "8080"))]
        (jetty/run-jetty app {:port port})))
```

## Contributors

Many thanks to Robert Levy, Manoj Waikar, Pat Patterson and Anthony
Grimes for their contributions.

## License

    Copyright (c) 2011-2012, Moritz Heidkamp
    All rights reserved.

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are
    met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
    "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
    LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
    A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
    HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
    SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
    LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
    DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
    THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
    (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
    OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
