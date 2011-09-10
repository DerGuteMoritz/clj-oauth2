(ns clj-oauth2.client
  (:use [clj-http.client :only [wrap-request]]
        [clojure.contrib.json :only [read-json]]
        [clojure.contrib.java-utils]
        [clj-oauth2.uri]
        [clojure.pprint])
  (:require [clj-http.client :as http]
            [clojure.string :as str])
  (:import [clj_oauth2 OAuth2Exception OAuth2StateMismatchException]))

(defn make-auth-request
  [{:keys [authorization-uri client-id client-secret redirect-uri scope]}
   & [state]]
  (let [uri (parse-uri authorization-uri)
        query (assoc (:query uri)
                :client_id client-id
                :redirect_uri redirect-uri
                :response_type "code")
        query (if state (assoc query :state state) query)
        query (if scope
                (assoc query :scope (str/join " " scope))
                query)]
    {:uri (str (make-uri (assoc uri :query query)))
     :scope scope
     :state state}))

(defn- request-access-token
  [{:keys [access-token-uri client-id client-secret redirect-uri access-query-param]} code]
  (let [{:keys [body headers status]}
        (http/post access-token-uri
                   {:content-type "application/x-www-form-urlencoded"
                    :throw-exceptions false
                    :body (url-encode
                           {:code code
                            :grant_type "authorization_code"
                            :client_id client-id
                            :client_secret client-secret
                            :redirect_uri redirect-uri})})
        content-type (headers "content-type")
        body (if (or (.startsWith content-type "application/json")
                     (.startsWith content-type "text/javascript")) ; Facebookism
               (read-json body)
               (form-url-decode body))  ; Facebookism
        error (:error body)]
    
    (if error
      (throw (OAuth2Exception. (if (string? error)
                                 (:error_description body)
                                 (:message error)) ; Facebookism
                               (if (string? error)
                                 error
                                 (:type error)))) ; Facebookism 
      {:access-token (:access_token body)
       :query-param access-query-param})))

(defn get-access-token [endpoint
                        {error-description :error_description
                         :keys [code state error]}
                        & [{expected-state :state expected-scope :scope}]]
  (cond (string? error)
        (throw (OAuth2Exception. error-description error))

        (and expected-state (not (= state expected-state)))
        (throw (OAuth2StateMismatchException.
                (format "Expected state %s but got %s"
                        state expected-state)
                state
                expected-state))
        
        :else
        (request-access-token endpoint code)))


(defn wrap-oauth2 [client]
  (fn [{:keys [oauth2] :as req}]
    (let [{:keys [access-token query-param throw-exceptions]} oauth2]
      (if (and query-param access-token)
        (client (assoc-in (dissoc req :query-param :access-token)
                          [:query-params query-param]
                          access-token))
        (if throw-exceptions
          (throw (OAuth2Exception. "Missing :oauth2 params"))
          (client req))))))

(defn request [req]
  ((wrap-oauth2 http/request) req))

(defmacro def-request-shortcut-fn [method]
  (let [method-key (keyword method)]
    `(defn ~method [url# & [req#]]
       (request (merge req#
                       {:method ~method-key
                        :url url#})))))

(def-request-shortcut-fn get)
(def-request-shortcut-fn post)
(def-request-shortcut-fn put)
(def-request-shortcut-fn delete)
(def-request-shortcut-fn head)
