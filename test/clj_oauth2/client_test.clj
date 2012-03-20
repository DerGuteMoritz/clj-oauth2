(ns clj-oauth2.client-test
  (:use clojure.test
        [clojure.data.json :only [json-str]]
        [clojure.pprint :only [pprint]])
  (:require [clj-oauth2.client :as base]
            [ring.adapter.jetty :as ring]
            [uri.core :as uri]
            [clojure.contrib.string :as str])
  (:import [clj_oauth2 OAuth2Exception OAuth2StateMismatchException]
           [org.apache.commons.codec.binary Base64]))


(defn throws?
  [c f & [e]]
  (try (f) false
       (catch Throwable t
         (if (instance? c t)
           (do (when e (e t)) true)
           (throw t)))))



(def endpoint
  {:client-id "foo"
   :client-secret "bar"
   :access-query-param :access_token
   :scope ["foo" "bar"]})

(def access-token
  {:access-token "sesame"
   :query-param :access_token
   :token-type "bearer"
   :expires-in 120
   :refresh-token "new-foo"})


(def endpoint-auth-code
  (assoc endpoint
    :redirect-uri "http://my.host/cb"
    :grant-type "authorization_code"
    :authorization-uri "http://localhost:18080/auth"
    :access-token-uri "http://localhost:18080/token-auth-code"))

(def endpoint-resource-owner
  (assoc endpoint
    :grant-type "password"
    :access-token-uri "http://localhost:18080/token-password"))

(def resource-owner-credentials
  {:username "foo"
   :password "bar"})

(defn parse-base64-auth-header [req]
  (let [header (get-in req [:headers "authorization"] "")
        [scheme param] (rest (re-matches #"\s*(\w+)\s+(.+)" header))]
    (when-let [scheme (and scheme param (.toLowerCase scheme))]
      [scheme (String. (Base64/decodeBase64 param) "UTF-8")])))

(defn parse-basic-auth-header [req]
  (let [[scheme param] (parse-base64-auth-header req)]
    (and scheme param
         (= "basic" scheme)
         (str/split #":" 2 param))))

(defn handle-protected-resource [req grant & [deny]]
  (let [query (uri/form-url-decode (:query-string req))
        [scheme param] (parse-base64-auth-header req)
        bearer-token (and (= scheme "bearer") param)
        token (or bearer-token (:access_token query))]
    (if (= token (:access-token access-token))
      {:status 200 :body (if (fn? grant) (grant token) grant)}
      {:status 400 :body (or deny "nope")})))

(defn client-authenticated? [req endpoint]
  (let [body (:body req)
        [client-id client-secret]
        (or (parse-basic-auth-header req)
            [(:client_id body) (:client_secret body)])]
    (and (= client-id (:client-id endpoint))
         (= client-secret (:client-secret endpoint)))))

(defn token-response [req]
  {:status 200
   :headers {"content-type" (str "application/"
                                 (if (contains? (:query-params req) :formurlenc)
                                   "x-www-form-urlencoded"
                                   "json")
                                 "; charset=UTF-8")}
   :body ((if (contains? (:query-params req) :formurlenc)
            uri/form-url-encode
            json-str)
          (let [{:keys [access-token
                        token-type
                        expires-in
                        refresh-token]}
                access-token]
            {:access_token access-token
             :token_type token-type
             :expires_in expires-in
             :refresh_token refresh-token}))})

;; shamelessly copied from clj-http tests
(defn handler [req]
  ;; (pprint req)
  ;; (println)
  ;; (println)
  (let [req (assoc req :query-params
                   (and (:query-string req)
                        (uri/form-url-decode (:query-string req))))]
    (condp = [(:request-method req) (:uri req)]
      [:post "/token-auth-code"]
      (let [body (uri/form-url-decode (slurp (:body req)))
            req (assoc req :body body)]
        (if (and (= (:code body) "abracadabra")
                 (= (:grant_type body) "authorization_code")
                 (client-authenticated? req endpoint-auth-code)
                 (= (:redirect_uri body) (:redirect-uri endpoint-auth-code)))
          (token-response req)
          {:status 400 :body "error=fail&error_description=invalid"}))
      [:post "/token-password"]
      (let [body (uri/form-url-decode (slurp (:body req)))
            req (assoc req :body body)]
        (if (and (= (:grant_type body) "password")
                 (= (:username body) (:username resource-owner-credentials))
                 (= (:password body) (:password resource-owner-credentials))
                 (client-authenticated? req endpoint-resource-owner))
          (token-response req)
          {:status 400 :body "error=fail&error_description=invalid"}))
      [:post "/token-error"]
      {:status 400
       :headers {"content-type" "application/json"}
       :body (json-str {:error "unauthorized_client"
                        :error_description "not good"})}
      [:get "/some-resource"]
      (handle-protected-resource req "that's gold jerry!")
      [:get "/query-echo"]
      (handle-protected-resource req (:query-string req))
      [:get "/query-and-token-echo"]
      (handle-protected-resource req
                                 (fn [token]
                                   (uri/form-url-encode
                                    (assoc (:query-params req)
                                      :access_token token))))
      [:get "/get"]
      (handle-protected-resource req "get")
      [:post "/post"]
      (handle-protected-resource req "post")
      [:put "/put"]
      (handle-protected-resource req "put")
      [:delete "/delete"]
      (handle-protected-resource req "delete")
      [:head "/head"]
      (handle-protected-resource req "head"))))

(defonce server
  (future (ring/run-jetty handler {:port 18080})))

(deftest grant-type-auth-code
  (let [req (base/make-auth-request endpoint-auth-code "bazqux")
        uri (uri/uri->map (uri/make (:uri req)) true)]
    (testing
      "constructs a uri for the authorization redirect"
      (is (= (:scheme uri) "http"))
      (is (= (:host uri) "localhost"))
      (is (= (:port uri) 18080))
      (is (= (:path uri) "/auth"))
      (is (= (:query uri) {:response_type "code"
                           :client_id "foo"
                           :redirect_uri "http://my.host/cb"
                           :scope "foo bar"
                           :state "bazqux"})))
    (testing
      "contains the passed in scope and state"
      (is (= (:scope req) ["foo" "bar"]))
      (is (= (:state req) "bazqux"))))

  (testing
    base/get-access-token
    (testing
      "returns an access token hash-map on success"
      (is (= (:access-token (base/get-access-token endpoint-auth-code
                                                   {:code "abracadabra" :state "foo"}
                                                   {:state "foo"}))
             "sesame")))
    (testing
      "also works with client credentials passed in the authorization header"
      (is (= (:access-token (base/get-access-token (assoc endpoint-auth-code
                                                          :authorization-header? true)
                                                   {:code "abracadabra" :state "foo"}
                                                   {:state "foo"}))
             "sesame")))
    (testing
      "also works with application/x-www-form-urlencoded responses (as produced by Facebook)"
      (is (= (:access-token (base/get-access-token (assoc endpoint-auth-code :access-token-uri
                                                          (str (:access-token-uri endpoint-auth-code)
                                                               "?formurlenc"))
                                                   {:code "abracadabra" :state "foo"}
                                                   {:state "foo"}))
             "sesame")))
    (testing
      "returns an access token when no state is given"
      (is (= (:access-token (base/get-access-token endpoint-auth-code {:code "abracadabra"}))
             "sesame")))
    (testing
      "fails when state differs from expected state"
      (is (thrown? OAuth2StateMismatchException
                   (base/get-access-token endpoint-auth-code
                                          {:code "abracadabra" :state "foo"}
                                          {:state "bar"}))))
    (testing
      "fails when an error response is passed in"
      (is (thrown? OAuth2Exception
                   (base/get-access-token endpoint-auth-code
                                          {:error "invalid_client"
                                           :error_description "something went wrong"}))))
    (testing
      "raises on error response"
      (is (thrown? OAuth2Exception
                   (base/get-access-token (assoc endpoint-auth-code
                                                 :access-token-uri
                                                 "http://localhost:18080/token-error")
                                          {:code "abracadabra" :state "foo"}
                                          {:state "foo"}))))))

(deftest grant-type-resource-owner
  (testing
    "returns an access token hash-map on success"
    (is (= (:access-token (base/get-access-token endpoint-resource-owner resource-owner-credentials))
           "sesame")))
  (testing
    "fails when invalid credentials are given"
    (is (thrown? OAuth2Exception
                 (base/get-access-token
                   endpoint-resource-owner
                   {:username "foo" :password "qux"})))))

(deftest token-usage
  (testing
    "should grant access to protected resources"
    (is (= "that's gold jerry!"
           (:body (base/request {:method :get
                                 :oauth2 access-token
                                 :url "http://localhost:18080/some-resource"})))))

  (testing
    "should preserve the url's query string when adding the access-token"
    (is (= {:foo "123" (:query-param access-token) (:access-token access-token)}
           (uri/form-url-decode
             (:body (base/request {:method :get
                                   :oauth2 access-token
                                   :query-params {:foo "123"}
                                   :url "http://localhost:18080/query-echo"}))))))

  (testing
    "should support passing bearer tokens through the authorization header"
    (is (= {:foo "123" :access_token (:access-token access-token)}
           (uri/form-url-decode
             (:body (base/request {:method :get
                                   :oauth2 (dissoc access-token :query-param)
                                   :query-params {:foo "123"}
                                   :url "http://localhost:18080/query-and-token-echo"}))))))

  (testing
    "should deny access to protected resource given an invalid access token"
    (is (= "nope"
           (:body (base/request {:method :get
                                 :oauth2 (assoc access-token :access-token "nope")
                                 :url "http://localhost:18080/some-resource"
                                 :throw-exceptions false})))))

  (testing
    "pre-defined shortcut request functions"
    (let [req {:oauth2 access-token}]
      (is (= "get" (:body (base/get "http://localhost:18080/get" req))))
      (is (= "post" (:body (base/post "http://localhost:18080/post" req))))
      (is (= "put" (:body (base/put "http://localhost:18080/put" req))))
      (is (= "delete" (:body (base/delete "http://localhost:18080/delete" req))))
      (is (= 200 (:status (base/head "http://localhost:18080/head" req)))))))
