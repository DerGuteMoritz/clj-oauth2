(ns clj-oauth2.test.client
  (:use [clj-oauth2.client]
        [clj-oauth2.uri]
        [lazytest.describe]
        [clojure.contrib.json :only [json-str]]
        [clojure.contrib.pprint :only [pprint]])
  (:require [ring.adapter.jetty :as ring]))

(def endpoint
  {:authorization-uri "http://localhost:18080/auth"
   :access-token-uri "http://localhost:18080/token"
   :client-id "foo"
   :client-secret "bar"
   :redirect-uri "http://my.host/cb"
   :scope ["foo" "bar"]})

(def endpoint-auth-code
  (assoc endpoint
    :grant-type 'authorization-code))

;; shamelessly copied from clj-http tests
(defn handler [req]
  (pprint req)
  (println) (println)
  (condp = [(:request-method req) (:uri req)]
    [:post "/token"]
    (let [body (form-url-decode (slurp (:body req)))]
      (if (= (:code body) "abracadabra")
        {:status 200
         :body (json-str {:access_token "sesame"
                          :token_type "spell"
                          :expires_in 120
                          :refresh_token "new-foo"})}))))

(defonce server
  (future (ring/run-jetty handler {:port 18080})))

(describe "grant-type authorization-code"
  (given [req (make-request endpoint-auth-code "bazqux")
          uri (parse-uri (:uri req))]
    (it "constructs a uri for the authorization redirect"
      (and (= (:scheme uri) "http")
           (= (:host uri) "localhost")
           (= (:port uri) 18080)
           (= (:path uri) "/auth")
           (= (:query uri) {:response_type "code"
                            :client_id "foo"
                            :client_secret "bar"
                            :redirect_uri "http://my.host/cb"
                            :scope "foo bar"
                            :state "bazqux"})))
    (it "contains the passed in scope and state"
      (and (= (:scope req) ["foo" "bar"])
           (= (:state req) "bazqux"))))

  (testing get-access-token
    (it "returns an access token hash-map on success"
      (= (:access-token (get-access-token endpoint-auth-code {:code "abracadabra"}))
         "sesame"))))

