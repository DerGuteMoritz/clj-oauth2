(ns clj-oauth2.client
  (:use [clj-http.client :only [wrap-request]]
        [clojure.contrib.json :only [read-json]]
        [clojure.contrib.java-utils]
        [clj-oauth2.uri])
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
                        {:keys [code]}]
  (let [resp (http/post access-token-uri
                        {:body (url-encode
                                {:code code
                                 :grant_type "authorization_code"
                                 :client_id client-id
                                 :client_secret client-secret
                                 :redirect_uri redirect-uri})})]
    {:access-token (:access_token (read-json (:body resp)))}))
