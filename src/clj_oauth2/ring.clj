(ns clj-oauth2.ring
	(:require [clojure.contrib.java-utils :as java-utils]
		        [clj-oauth2.client :as oauth2]))

;; Random mixed case alphanumeric
(defn- random-string [length]
	(let [ascii-codes (concat (range 48 58) (range 65 91) (range 97 123))]
	     (apply str (repeatedly length #(char (rand-nth ascii-codes))))))
	
(defn- excluded? [uri oauth2-params]
	(let [exclusion (:exclude oauth2-params)]
		(cond 
			(coll? exclusion) 
				(some = exclusion uri)
			(string? exclusion) 
				(= exclusion uri)
			(fn? exclusion) 
				(exclusion uri)
			(instance? java.util.regex.Pattern exclusion) 
				(re-matches exclusion uri))))

;; Functions to store state, target URL, OAuth2 data in session		
;; requires ring.middleware.session/wrap-session
(defn get-state-from-session [request]
  (:state (:session request)))

(defn put-state-in-session [response state]
  (assoc response :session (merge (response :session) {:state state})))
  
(defn get-target-from-session [request]
  (:target (:session request)))

(defn put-target-in-session [response target]
  (assoc response :session (merge (response :session) {:target target})))

(defn get-oauth2-data-from-session [request] 
  (:oauth2 (:session request)))

(defn put-oauth2-data-in-session [request response oauth2-data]
  (assoc 
    response 
    :session (merge 
               (or (:session response) (:session request)) 
               (or (find response :oauth2) {:oauth2 oauth2-data}))))

;; This Ring wrapper acts as a filter, ensuring that the user has an OAuth
;; token for all but a set of explicitly excluded URLs. The response from
;; oauth2/get-access-token is exposed in the request via the :oauth2 key. 
;; Requires ring.middleware.params/wrap-params and
;; ring.middleware.keyword-params/wrap-keyword-params to have been called 
;; first.
(defn wrap-oauth2
  [handler oauth2-params]
     (fn [request]
		  (if (excluded? (:uri request) oauth2-params)
				(handler request)
        ;; Is the request uri the same as the redirect URI?
				;; Use string compare, since java.net.URL.equals resolves hostnames - very slow!
			  (if (= (.toString 
			           (java.net.URL. 
			             (name (:scheme request)) 
			             (:server-name request) 
			             (:server-port request) 
			             (:uri request)))
			         (:redirect-uri oauth2-params))
        	;; We should have an authorization code - get the access token, put
        	;; it in the response and redirect to the originally requested URL
				  (let [response {:status 302
	                        :headers {"Location" ((:get-target oauth2-params) request)}}
	              oauth2-data (oauth2/get-access-token 
                              oauth2-params 
                              (:params request) 
                              (oauth2/make-auth-request 
                                oauth2-params 
                                ((:get-state oauth2-params) request)))]
            ((:put-oauth2-data oauth2-params) request response oauth2-data))
				  ;; We're not handling the callback
         	(let [oauth2-data ((:get-oauth2-data oauth2-params) request)]
         		(if (nil? oauth2-data) 
  						(let [xsrf-protection (or ((:get-state oauth2-params) request) (random-string 20))
                    auth-req (oauth2/make-auth-request oauth2-params xsrf-protection)
                    target (str (:uri request) (if (:query-string request) (str "?" (:query-string request))))
  		         			;; Redirect to OAuth 2.0 authentication/authorization
                    response {:status 302 
    			                    :headers {"Location" (:uri auth-req)}}]
    			      ((:put-target oauth2-params)  ((:put-state oauth2-params) response xsrf-protection) target))
  			      ;; We have oauth2 data - invoke the handler
         			(if-let [response (handler (assoc request :oauth2 oauth2-data))]
         			  ((:put-oauth2-data oauth2-params) request response oauth2-data))))))))
