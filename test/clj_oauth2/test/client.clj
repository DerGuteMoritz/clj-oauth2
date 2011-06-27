(ns clj-oauth2.test.client
  (:use [clj-oauth2.client]
        [clj-oauth2.uri]
        [lazytest.describe]))

(def endpoint
  {:authorization-uri "http://example.com/auth"
   :access-token-uri "http://example.com/token"
   :client-id "foo"
   :client-secret "bar"
   :redirect-uri "http://my.host/cb"})

(describe "grant-type authorization-code"
  (given [req (make-request (assoc endpoint
                              :grant-type 'authorization-code
                              :scope ["foo" "bar"]
                              :state "bazqux"))
          uri (parse-uri (:uri req))]
    (it "constructs a uri for the authorization redirect"
      (and (= (:scheme uri) "http")
           (= (:host uri) "example.com")
           (= (:path uri) "/auth")
           (= (:query uri) {:response_type "code" :scope "foo bar" :state "bazqux"})))
    (it "contains the passed in scope and state"
      (and (= (:scope req) ["foo" "bar"])
           (= (:state req) "bazqux")))))