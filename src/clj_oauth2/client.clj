(ns clj-oauth2.client
  (:use [clj-http.client :only [wrap-request]]
        [clojure.contrib.json :only [read-json]]
        [clojure.contrib.java-utils]
        [clj-oauth2.uri])
  (:require [clj-http.client :as http]
            [clojure.string :as str]))

(defn make-request [endpoint]
  (let [uri (parse-uri (:authorization-uri endpoint))
        uri (assoc-in uri [:query :response_type] "code")
        uri (if (:state endpoint)
              (assoc-in uri [:query :state] (:state endpoint))
              uri)
        uri (if (:scope endpoint)
              (assoc-in uri [:query :scope]
                        (str/join " " (:scope endpoint)))
              uri)]
    {:uri (str (make-uri uri))
     :scope (:scope endpoint)
     :state (:state endpoint)}))
