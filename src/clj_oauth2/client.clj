(ns clj-oauth2.client
  (:refer-clojure :exclude [get])
  (:use [clj-http.client :only [wrap-request]]
        [clojure.data.json :only [read-json]])
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [uri.core :as uri])
  (:import [clj_oauth2 OAuth2Exception OAuth2StateMismatchException]
           [org.apache.commons.codec.binary Base64]))

(defn make-auth-request
  [{:keys [authorization-uri client-id client-secret redirect-uri scope]}
   & [state]]
  (let [uri (uri/uri->map (uri/make authorization-uri) true)
        query (assoc (:query uri)
                :client_id client-id
                :redirect_uri redirect-uri
                :response_type "code")
        query (if state (assoc query :state state) query)
        query (if scope
                (assoc query :scope (str/join " " scope))
                query)]
    {:uri (str (uri/make (assoc uri :query query)))
     :scope scope
     :state state}))

(defn- add-base64-auth-header [req scheme param]
  (let [param (Base64/encodeBase64String (.getBytes param))
        header (str scheme " " param)]
    (assoc-in req [:headers "Authorization"] header)))

(defn- request-access-token
  [endpoint code]
  (let [{:keys [access-token-uri client-id client-secret
                redirect-uri access-query-param
                grant-type authorization-header?]}
        endpoint
        request
        {:content-type "application/x-www-form-urlencoded"
         :throw-exceptions false
         :body {:code code
                :grant_type grant-type
                :redirect_uri redirect-uri}}
        request
        (if authorization-header?
          (add-base64-auth-header
           request
           "Basic"
           (str client-id ":" client-secret))
          (merge-with
           merge
           request
           {:body
            {:client_id client-id
             :client_secret client-secret}}))
        request (update-in request [:body] uri/form-url-encode)
        {:keys [body headers status]} (http/post access-token-uri request)
        content-type (headers "content-type")
        body (if (and content-type
                      (or (.startsWith content-type "application/json")
                          (.startsWith content-type "text/javascript"))) ; Facebookism
               (read-json body)
               (uri/form-url-decode body)) ; Facebookism
        error (:error body)]
    
    (if error
      (throw (OAuth2Exception. (if (string? error)
                                 (:error_description body)
                                 (:message error)) ; Facebookism
                               (if (string? error)
                                 error
                                 (:type error)))) ; Facebookism 
      {:access-token (:access_token body)
       :token-type (:token_type body)
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

(defn with-access-token [uri {:keys [access-token query-param]}]
  (str (uri/make (assoc-in (uri/uri->map (uri/make uri) true)
                           [:query query-param]
                           access-token))))

(defmulti add-access-token-to-request
  (fn [req oauth2]
    (:token-type oauth2)))

(defmethod add-access-token-to-request
  :default [req oauth2]
  (let [{:keys [token-type]} oauth2]
    (if (:throw-exceptions req)
      (throw (OAuth2Exception. (str "Unknown token type: " token-type)))
      [req false])))

(defmethod add-access-token-to-request
  "bearer" [req oauth2]
  (let [{:keys [access-token query-param]} oauth2]
    (if access-token
      [(if query-param
         (assoc-in req [:query-params query-param] access-token)
         (add-base64-auth-header req "Bearer" access-token))
       true]
      [req false])))

(defn wrap-oauth2 [client]
  (fn [req]
    (let [{:keys [oauth2 throw-exceptions]} req
          [req token-added?] (add-access-token-to-request req oauth2)
          req (dissoc req :oauth2)]
      (if token-added?
        (client req)
        (if throw-exceptions
          (throw (OAuth2Exception. "Missing :oauth2 params"))
          (client req))))))

(def request
  (wrap-oauth2 http/request))

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
