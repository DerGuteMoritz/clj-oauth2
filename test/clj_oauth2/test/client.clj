(ns clj-oauth2.test.client
  (:use [clj-oauth2.client]
        [clj-oauth2.uri]
        [lazytest.describe]))

(def endpoint
  {:authorization-uri "http://example.com/auth"
   :access-token-uri "http://example.com/token"
   :client-id "foo"
   :client-secret "bar"
   :redirect-uri "http://my.host/cb"
   :scope ["foo" "bar"]})

(def endpoint-auth-code
  (assoc endpoint
    :grant-type 'authorization-code))

(describe "grant-type authorization-code"
  (given [req (make-request endpoint-auth-code "bazqux")
          uri (parse-uri (:uri req))]
    (it "constructs a uri for the authorization redirect"
      (and (= (:scheme uri) "http")
           (= (:host uri) "example.com")
           (= (:path uri) "/auth")
           (= (:query uri) {:response_type "code"
                            :client_id "foo"
                            :client_secret "bar"
                            :redirect_uri "http://my.host/cb"
                            :scope "foo bar"
                            :state "bazqux"})))
    (it "contains the passed in scope and state"
      (and (= (:scope req) ["foo" "bar"])
           (= (:state req) "bazqux")))))