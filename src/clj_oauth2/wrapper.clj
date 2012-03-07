(ns clj-oauth2.wrapper
	(:require [clojure.contrib.java-utils :as java-utils]
		        [clj-oauth2.client :as oauth2]))

; Random mixed case alphanumeric
(defn- random-string [length]
	(let [ascii-codes (concat (range 48 58) (range 65 91) (range 97 123))]
	     (apply str (repeatedly length #(char (rand-nth ascii-codes))))))
	
(defn- excluded? [uri oauth-params]
	(let [exclusion (:exclude oauth-params)]
		(cond 
			(coll? exclusion) 
				(contains? exclusion uri)
			(string? exclusion) 
				(= exclusion uri)
			(fn? exclusion) 
				(exclusion uri)
			(instance? java.util.regex.Pattern exclusion) 
				(re-matches exclusion uri))))

; This Ring wrapper acts as a filter, ensuring that the user has an OAuth
; token for all but a set of explicitly excluded URLs. The response from
; oauth2/get-access-token is stored in the session under the :oauth key
; and exposed in the request via the same :oauth key. 
; Requires ring.middleware.params/wrap-params
; ring.middleware.keyword-params/wrap-keyword-params,
; and ring.middleware.session/wrap-session to have been called first.
(defn wrap-oauth
  [handler oauth-params]
     (fn [request]
		  (if (excluded? (:uri request) oauth-params)
				(handler request)
				(let [session (:session request)]
	        ; Is the request uri path the same as the redirect URI path?
	        (if (= (:uri request) (.getPath (java-utils/as-url (:redirect-uri oauth-params))))
	        	; We should have an authorization code - get the access token,
	        	; put it in the session and redirect to the originally requested
					  ; URL
					  (let [xsrf-protection (:xsrf-protection session)
					        auth-req (oauth2/make-auth-request oauth-params xsrf-protection)]
		          {:status 302
		           :headers {"Location" (:url session)}
		        	 :session (let [auth-req (oauth2/make-auth-request oauth-params xsrf-protection)
			                        oauth-response (oauth2/get-access-token oauth-params (:params request) auth-req)]
	        	                  {:oauth (merge oauth-response {:trace-messages (:trace-messages oauth-params)})})})
					  ; We're not handling the callback
	         	(let [oauth-response (:oauth session)]
	         		(if (nil? oauth-response) 
	              ; No oauth data in session
	  						(let [xsrf-protection (random-string 20)
	                    auth-req (oauth2/make-auth-request oauth-params xsrf-protection)]
		         			; Redirect to OAuth authentication/authorization
		         			{:status 302 
			             :headers {"Location" (:uri auth-req)} 
			             :session {:xsrf-protection xsrf-protection 
				                     :url (str (:uri request) (if (:query-string request) (str "?" (:query-string request))))}})
	         			; Put the OAuth response on the request and invoke handler
	         			(if-let [response (handler (assoc request :oauth oauth-response))]
	         			    (if-let [session (response :session)]
											  ; Handler has put data in the session
	             			    (if-let [new-oauth (find response :oauth)]
	             			        ; Handler has set oauth - merge it all together
	             			        (assoc response :session (merge (response :session) {:oauth (:oauth response)}))
	             			        ; Add our oauth data to the session
	             			        (assoc response :session (merge (response :session) {:oauth oauth-response})))
												; Handler has not modified session
	             			    (if-let [new-oauth (find response :oauth)]
	             			        ; Handler has set oauth - merge the new oauth data
														; into the session from the request
	             			        (assoc response :session (merge session {:oauth (:oauth response)}))
	             			        ; No change to session - our oauth data will
	             			        ; be fine. If we were to set it here, we would 
	             			        ; wipe out the existing session state!
	             			        response))))))))))

