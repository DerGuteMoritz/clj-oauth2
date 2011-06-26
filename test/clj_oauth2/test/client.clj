(ns clj-oauth2.test.client
  (:use [clj-oauth2.client])
  (:use [lazytest.describe]))

(def endpoint
  {:authorization-uri "http://example.com/auth"
   :access-token-uri "http://example.com/token"
   :client-id "foo"
   :client-secret "bar"
   :redirect-uri "http://my.host/cb"})

(describe "grant-type authorization-code"
  (given [req (make-request (assoc endpoint :grant-type 'authorization-code))
          uri (parse-uri (:uri req))]
    (it "constructs a uri for the authorization redirect"
      (= (get-in uri [:query-params :response_type]) "code"))))