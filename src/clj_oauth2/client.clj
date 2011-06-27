(ns clj-oauth2.client
  (:use [clj-http.client :only [wrap-request]]
        [clojure.contrib.json :only [read-json]]
        [clojure.contrib.java-utils]
        [clj-oauth2.uri]
        [clojure.contrib.duck-streams :only [to-byte-array]])
  (:require [clj-http.client :as http]
            [clojure.string :as str]))

(defn make-request [endpoint & [state]]
  (let [uri (parse-uri (:authorization-uri endpoint))
        query (assoc (:query uri)
                :client_id (:client-id endpoint)
                :client_secret (:client-secret endpoint)
                :redirect_uri (:redirect-uri endpoint)
                :response_type "code")
        query (if state (assoc query :state state) query)
        query (if (:scope endpoint)
                (assoc query :scope (str/join " " (:scope endpoint)))
                query)]
    {:uri (str (make-uri (assoc uri :query query)))
     :scope (:scope endpoint)
     :state state}))

(defn get-access-token [endpoint params]
  (let [resp (http/post (:access-token-uri endpoint)
                        {:body (url-encode
                                {:code (:code params)
                                 :grant_type "authorization_code"
                                 :client_id (:client-id endpoint)
                                 :client_secret (:client-secret endpoint)
                                 :redirect_uri (:redirect-uri endpoint)})})]
    {:access-token (:access_token (read-json (:body resp)))}))