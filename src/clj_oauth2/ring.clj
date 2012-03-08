(ns clj-oauth2.ring
	(:require [clojure.contrib.java-utils :as java-utils]
		        [clj-oauth2.client :as oauth2]))

;; Random mixed case alphanumeric
(defn- random-string [length]
	(let [ascii-codes (concat (range 48 58) (range 65 91) (range 97 123))]
	     (apply str (repeatedly length #(char (rand-nth ascii-codes))))))
	
(defn- excluded? [uri oauth-params]
	(let [exclusion (:exclude oauth-params)]
		(cond 
			(coll? exclusion) 
				(some = exclusion uri)
			(string? exclusion) 
				(= exclusion uri)
			(fn? exclusion) 
				(exclusion uri)
			(instance? java.util.regex.Pattern exclusion) 
				(re-matches exclusion uri))))

;; This Ring wrapper acts as a filter, ensuring that the user has an OAuth
;; token for all but a set of explicitly excluded URLs. The response from
;; oauth2/get-access-token is stored in the session under the :oauth2 key
;; and exposed in the request via the same :oauth2 key. 
;; Requires ring.middleware.params/wrap-params
;; ring.middleware.keyword-params/wrap-keyword-params,
;; and ring.middleware.session/wrap-session to have been called first.
(defn wrap-oauth2
  [handler oauth-params]
     (fn [request]
		  (if (excluded? (:uri request) oauth-params)
				(handler request)
				(let [req-session (:session request)]
	        ;; Is the request uri the same as the redirect URI?
					;; Use string compare, since java.net.URL.equals resolves hostnames - very slow!
				  (if (= (.toString (java.net.URL. (name (:scheme request)) (:server-name request) (:server-port request) (:uri request)))
				         (:redirect-uri oauth-params))
	        	;; We should have an authorization code - get the access token,
	        	;; put it in the session and redirect to the originally requested
					  ;; URL
					  (let [xsrf-protection (:xsrf-protection req-session)
					        auth-req (oauth2/make-auth-request oauth-params xsrf-protection)]
		          {:status 302
		           :headers {"Location" (:url req-session)}
		        	 :session (let [auth-req (oauth2/make-auth-request oauth-params xsrf-protection)
			                        oauth-response (oauth2/get-access-token oauth-params (:params request) auth-req)]
	        	                  {:oauth2 (merge oauth-response {:trace-messages (:trace-messages oauth-params)})})})
					  ;; We're not handling the callback
	         	(let [oauth-response (:oauth2 req-session)]
	         		(if (nil? oauth-response) 
	              ;; No oauth data in session
	  						(let [xsrf-protection (or (:xsrf-protection req-session) (random-string 20))
	                    auth-req (oauth2/make-auth-request oauth-params xsrf-protection)]
		         			;; Redirect to OAuth authentication/authorization
		         			{:status 302 
			             :headers {"Location" (:uri auth-req)} 
			             :session {:xsrf-protection xsrf-protection 
				                     :url (str (:uri request) (if (:query-string request) (str "?" (:query-string request))))}})
	         			;; Put the OAuth response on the request and invoke handler
	         			(if-let [response (handler (assoc request :oauth2 oauth-response))]
	         			    (if-let [rsp-session (:session response)]
											  ;; Handler has put data in the session
	             			    (if-let [new-oauth (find response :oauth2)]
	             			        ;; Handler has set oauth - merge it all together
	             			        (assoc response :session (merge rsp-session new-oauth))
	             			        ;; Add our oauth data to the session
	             			        (assoc response :session (merge rsp-session {:oauth2 oauth-response})))
												;; Handler has not modified session
	             			    (if-let [new-oauth (find response :oauth2)]
	             			        ;; Handler has set oauth - merge the new oauth data
                            ;; into the session from the request
	             			        (assoc response :session (merge req-session {:oauth2 (:oauth2 response)}))
	             			        ;; No change to session - our oauth data will
	             			        ;; be fine. If we were to set it here, we would 
	             			        ;; wipe out the existing session state!
	             			        response))))))))))

