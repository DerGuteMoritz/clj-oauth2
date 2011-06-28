(ns clj-oauth2.client
  (:use [clj-http.client :only [wrap-request]]
        [clojure.contrib.json :only [read-json]]
        [clojure.contrib.java-utils]
        [clj-oauth2.uri]
        [clojure.contrib.condition])
  (:require [clj-http.client :as http]
            [clojure.string :as str]))

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

(defn get-access-token [{:keys [access-token-uri client-id client-secret redirect-uri]}
                        {:keys [code state]}
                        & [expected-state]]
  (if (or (not expected-state) (= state expected-state))
    (let [resp (http/post access-token-uri
                          {:content-type "application/x-www-form-urlencoded"
                           :body (url-encode
                                  {:code code
                                   :grant_type "authorization_code"
                                   :client_id client-id
                                   :client_secret client-secret
                                   :redirect_uri redirect-uri})})]
      {:access-token (:access_token (read-json (:body resp)))})
    (raise :type :state-mismatch
           :message (format "Expected state %s but got %s"
                            state expected-state))))


(defn request [{:keys [access-token]} req]
  (http/request (assoc-in req
                          [:query-params :access_token]
                          access-token)))

(defmacro def-request-shortcut-fn [method]
  (let [method-key (keyword method)]
    `(defn ~method [token# url# & [req#]]
       (request token# (merge req#
                              {:method ~method-key
                               :url url#})))))

(def-request-shortcut-fn get)
(def-request-shortcut-fn post)
(def-request-shortcut-fn put)
(def-request-shortcut-fn delete)
(def-request-shortcut-fn head)
