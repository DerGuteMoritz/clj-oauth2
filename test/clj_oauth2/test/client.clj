(ns clj-oauth2.test.client
  (:use [clj-oauth2.client]
        [clj-oauth2.uri]
        [lazytest.describe]
        [lazytest.expect :only (expect)]
        [clojure.data.json :only [json-str]]
        [clojure.pprint :only [pprint]])
  (:require [ring.adapter.jetty :as ring])
  (:import [clj_oauth2 OAuth2Exception OAuth2StateMismatchException]))


(defn throws?
  [c f & [e]]
  (try (f) false
       (catch Throwable t
         (if (instance? c t)
           (do (when e (e t)) true)
           (throw t)))))



(def endpoint
  {:authorization-uri "http://localhost:18080/auth"
   :access-token-uri "http://localhost:18080/token"
   :client-id "foo"
   :client-secret "bar"
   :redirect-uri "http://my.host/cb"
   :access-query-param :access_token
   :scope ["foo" "bar"]})

(def access-token
  {:access-token "sesame"
   :query-param :access_token
   :token-type "spell"
   :expires-in 120
   :refresh-token "new-foo"})


(def endpoint-auth-code
  (assoc endpoint
    :grant-type 'authorization-code))

(defn handle-protected-resource [req grant & [deny]]
  (let [query (form-url-decode (:query-string req))]
    (if (= (:access_token query) (:access-token access-token))
      {:status 200 :body grant}
      {:status 400 :body (or deny "nope")})))

;; shamelessly copied from clj-http tests
(defn handler [req]
  ;; (pprint req)
  ;; (println)
  ;; (println)
  (condp = [(:request-method req) (:uri req)]
    [:post "/token"]
    (let [body (form-url-decode (slurp (:body req)))]
      (if (and (= (:code body) "abracadabra")
               (= (:grant_type body) "authorization_code")
               (= (:client_id body) (:client-id endpoint))
               (= (:client_secret body) (:client-secret endpoint))
               (= (:redirect_uri body) (:redirect-uri endpoint)))
        {:status 200
         :headers {"content-type" (str "application/"
                                       (condp = (:query-string req)
                                         "formurlenc" "x-www-form-urlencoded"
                                         nil "json")
                                       "; charset=UTF-8")}
         :body ((condp = (:query-string req)
                  "formurlenc" url-encode
                  nil json-str)
                (let [{:keys [access-token
                              token-type
                              expires-in
                              refresh-token]}
                      access-token]
                  {:access_token access-token
                   :token_type token-type
                   :expires_in expires-in
                   :refresh_token refresh-token}))}
        {:status 400 :body "error"}))
    [:post "/token-error"]
    {:status 400
     :headers {"content-type" "application/json"}
     :body (json-str {:error "unauthorized_client"
                      :error_description "not good"})}
    [:get "/some-resource"]
    (handle-protected-resource req "that's gold jerry!")
    [:get "/query-echo"]
    (handle-protected-resource req (:query-string req))
    [:get "/get"]
    (handle-protected-resource req "get")
    [:post "/post"]
    (handle-protected-resource req "post")
    [:put "/put"]
    (handle-protected-resource req "put")
    [:delete "/delete"]
    (handle-protected-resource req "delete")
    [:head "/head"]
    (handle-protected-resource req "head")))

(defonce server
  (future (ring/run-jetty handler {:port 18080})))

(describe "grant-type authorization-code"
  (given [req (make-auth-request endpoint-auth-code "bazqux")
          uri (parse-uri (:uri req))]
    (it "constructs a uri for the authorization redirect"
      (and (= (:scheme uri) "http")
           (= (:host uri) "localhost")
           (= (:port uri) 18080)
           (= (:path uri) "/auth")
           (= (:query uri) {:response_type "code"
                            :client_id "foo"
                            :redirect_uri "http://my.host/cb"
                            :scope "foo bar"
                            :state "bazqux"})))
    (it "contains the passed in scope and state"
      (and (= (:scope req) ["foo" "bar"])
           (= (:state req) "bazqux"))))

  (testing get-access-token
    (it "returns an access token hash-map on success"
      (= (:access-token (get-access-token endpoint-auth-code
                                          {:code "abracadabra" :state "foo"}
                                          {:state "foo"}))
         "sesame"))
    (it "also works with application/x-www-form-urlencoded responses (as produced by Facebook)"
      (= (:access-token (get-access-token (assoc endpoint-auth-code :access-token-uri
                                                 (str (:access-token-uri endpoint-auth-code)
                                                      "?formurlenc"))
                                          {:code "abracadabra" :state "foo"}
                                          {:state "foo"}))
         "sesame"))
    (it "returns an access token when no state is given"
      (= (:access-token (get-access-token endpoint-auth-code {:code "abracadabra"}))
         "sesame"))
    (it "fails when state differs from expected state"
      (throws? OAuth2StateMismatchException
               (fn []
                 (get-access-token endpoint-auth-code
                                   {:code "abracadabra" :state "foo"}
                                   {:state "bar"}))))
    (it "fails when an error response is passed in"
      (throws? OAuth2Exception
               (fn []
                 (get-access-token endpoint-auth-code
                                   {:error "invalid_client"
                                    :error_description "something went wrong"}))
               (fn [e]
                 (expect (= ["something went wrong" "invalid_client"] @e)))))
    (it "raises on error response"
      (throws? OAuth2Exception
               (fn []
                 (get-access-token (assoc endpoint-auth-code
                                     :access-token-uri
                                     "http://localhost:18080/token-error")
                                   {:code "abracadabra" :state "foo"}
                                   {:state "foo"}))
               (fn [e]
                 (expect (= ["not good" "unauthorized_client"] @e)))))))

(describe "token usage"
  (it "should grant access to protected resources"
    (= "that's gold jerry!"
       (:body (request {:method :get
                        :oauth2 access-token
                        :url "http://localhost:18080/some-resource"}))))

  (it "should preserve the url's query string when adding the access-token"
    (= {:foo "123" (:query-param access-token) (:access-token access-token)}
       (form-url-decode (:body (request {:method :get
                                         :oauth2 access-token
                                         :query-params {:foo "123"}
                                         :url "http://localhost:18080/query-echo"})))))

  (it "should deny access to protected resource given an invalid access token"
    (= "nope"
       (:body (request {:method :get
                        :oauth2 (assoc access-token :access-token "nope")
                        :url "http://localhost:18080/some-resource"
                        :throw-exceptions false}))))

  (testing "pre-defined shortcut request functions"
    (given [req {:oauth2 access-token}]
      (it (= "get" (:body (get "http://localhost:18080/get" req))))
      (it (= "post" (:body (post "http://localhost:18080/post" req))))
      (it (= "put" (:body (put "http://localhost:18080/put" req))))
      (it (= "delete" (:body (delete "http://localhost:18080/delete" req))))
      (it (= 200 (:status (head "http://localhost:18080/head" req)))))))