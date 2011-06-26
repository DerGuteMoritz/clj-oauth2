(ns clj-oauth2.client
  (:use [clj-http.client :only [wrap-request]]
        [clojure.contrib.json :only [read-json]]
        [clojure.contrib.java-utils]
        [clj-oauth2.uri])
  (:require [clj-http.client :as http]))

(defn make-request [endpoint]
  (let [uri (parse-uri (:authorization-uri endpoint))
        uri (assoc-in uri [:query :response_type] "code")]
    {:uri (str (make-uri uri))}))