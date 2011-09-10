(ns clj-oauth2.uri
  (:require [clojure.string :as str]
            [clj-http.client :only parse-url :as http])
  (:import [java.net URI URLEncoder URLDecoder]))

(def url-encode)

(defn form-url-encode [arg]
  (str/join \& (map (fn [[k v]]
                      (if (vector? v)
                        (form-url-encode (map (fn [v] [k v]) v))
                        (str (url-encode (name k))
                             \=
                             (url-encode v))))
                    arg)))

;; taken from https://github.com/marktriggs/clojure-http-client/blob/master/src/clojure/http/client.clj
(defn url-encode
  "Wrapper around java.net.URLEncoder returning an UTF-8 URL encoded
representation of argument, either a string or map."
  [arg]
  (if (map? arg)
    (form-url-encode arg)
    (URLEncoder/encode (str arg) "UTF-8")))

(defn url-decode [str]
  (URLDecoder/decode str "UTF-8"))

(defn form-url-decode [str]
  (into {}
        (map (fn [p] (vector (keyword (first p)) (second p)))
             (map (fn [s] (map url-decode (str/split s #"=")))
                  (str/split str #"&")))))

(defmacro uri-as-map [uri]
  `(hash-map
    ~@(mapcat
       (fn [[key getter]]
         `(~key (. ~uri ~getter)))
       '((:scheme getScheme)
         (:user-info getUserInfo)
         (:host getHost)
         (:port getPort)
         (:path getPath)
         (:query getQuery)
         (:fragment getFragment)))))

(defn parse-uri
  ([uri] (parse-uri uri true))
  ([uri form-url-decode-query?]
     (let [uri (uri-as-map (URI. uri))]
       (if (and form-url-decode-query? (:query uri))
         (assoc uri :query (form-url-decode (:query uri)))
         uri))))

(defn format* [str a b]
  (if b (format str a b) a))

(defn make-uri [{:keys [scheme user-info host port path fragment query]}]
  (let [uri (format* "%2$s://%1$s" "" scheme)
        uri (format* "%s%s@" uri user-info)
        uri (format* "%s%s" uri host)
        uri (format* "%s:%d" uri (and (not (= -1 port)) port))
        uri (format* "%s%s" uri path)
        uri (format* "%s?%s" uri (and query (if (map? query)
                                              (url-encode query)
                                              query)))
        uri (format* "%s#%s" uri fragment)]
    (URI. uri)))