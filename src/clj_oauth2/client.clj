(ns clj-oauth2.client
  (:use [clj-http.client :only [wrap-request]]
        [clojure.contrib.json :only [read-json]]
        [clojure.contrib.java-utils])
  (:require [clj-http.client :as http]
            [clojure.string :as str])
  (:import (java.net URL URLEncoder URLDecoder)))


;; taken from https://github.com/marktriggs/clojure-http-client/blob/master/src/clojure/http/client.clj
;; modified to use ASCII instead of UTF-8
(defn url-encode
  "Wrapper around java.net.URLEncoder returning an ASCII URL encoded
 representation of argument, either a string or map."
  [arg]
  (if (map? arg)
    (str/join \& (map #(str/join \= (map url-encode %)) arg))
    (URLEncoder/encode (as-str arg) "ASCII")))

(defn url-decode [str]
  (URLDecoder/decode str "ASCII"))

(defn form-url-decode [str]
  (into {}
        (map (fn [p] (vector (keyword (first p)) (second p)))
             (map (fn [s] (map url-decode (str/split s #"=")))
                  (str/split str #"&")))))

(defn parse-uri [uri]
  (let [uri (http/parse-url uri)]
    (assoc uri :query-params (form-url-decode (:query-string uri)))))

(defn make-request [endpoint]
  (let [uri (as-url (:authorization-uri endpoint))
        query-str (. uri getQuery)
        query (if query-str (form-url-decode query-str) {})
        query (assoc query :response_type "code")]
    ;; (. uri setQuery (url-encode query))
    {:uri (. (URL. uri (str "?" (url-encode query))) toString)}))