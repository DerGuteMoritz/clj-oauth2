(ns clj-oauth2.ring
  (:require [clj-oauth2.client :as oauth2]
            [ring.util.codec :as codec]
            [clojure.string :as string]))

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

(def store-data-in-session
  {:get-state get-state-from-session
   :put-state put-state-in-session
   :get-target get-target-from-session
   :put-target put-target-in-session
   :get-oauth2-data get-oauth2-data-from-session
   :put-oauth2-data put-oauth2-data-in-session})

(defn request-uri [request oauth2-params]
  (let [scheme (if (:force-https oauth2-params) "https" (name (:scheme request)))
        port (if (or (and (= (name (:scheme request)) "http") 
                          (not= (:server-port request) 80))
                     (and (= (name (:scheme request)) "https") 
                          (not= (:server-port request) 443))) 
               (str ":" (:server-port request)))]
    (str scheme "://" (:server-name request) port (:uri request))))

;; Parameter handling code shamelessly plundered from ring.middleware. 
;; Thanks, Mark!
(defn- keyword-syntax? [s]
  (re-matches #"[A-Za-z*+!_?-][A-Za-z0-9*+!_?-]*" s))

(defn- keyify-params [target]
  (cond
   (map? target)
   (into {}
         (for [[k v] target]
           [(if (and (string? k) (keyword-syntax? k))
              (keyword k)
              k)
            (keyify-params v)]))
   (vector? target)
   (vec (map keyify-params target))
   :else
   target))

(defn- assoc-param
  "Associate a key with a value. If the key already exists in the map,
create a vector of values."
  [map key val]
  (assoc map key
         (if-let [cur (map key)]
           (if (vector? cur)
             (conj cur val)
             [cur val])
           val)))

(defn- parse-params
  "Parse parameters from a string into a map."
  [^String param-string encoding]
  (reduce
   (fn [param-map encoded-param]
     (if-let [[_ key val] (re-matches #"([^=]+)=(.*)" encoded-param)]
       (assoc-param param-map
                    (codec/url-decode key encoding)
                    (codec/url-decode (or val "") encoding))
       param-map))
   {}
   (string/split param-string #"&")))

(defn- submap? [map1 map2]
  "Are all the key/value pairs in map1 also in map2?"
  (every?
   (fn [item]
     (= item (find map2 (key item))))
   map1))

(defn is-callback [request oauth2-params]
  "Returns true if this is a request to the callback URL"
  (let [oauth2-url-vector (string/split (.toString (java.net.URI. (:redirect-uri oauth2-params))) #"\?")
        oauth2-uri (nth oauth2-url-vector 0)
        oauth2-url-params (nth oauth2-url-vector 1)
        encoding (or (:character-encoding request) "UTF-8")]
    (and (= oauth2-uri (request-uri request oauth2-params))
         (submap? (keyify-params (parse-params oauth2-url-params encoding)) (:params request)))))

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
      (if (is-callback request oauth2-params)
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
