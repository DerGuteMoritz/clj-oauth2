(ns clj-oauth2.test.uri
  (:use [clj-oauth2.uri]
        [lazytest.describe])
  (:import [java.net URI]))

(describe parse-uri
  (given [uri (parse-uri "http://example.com/somewhere")]
    (it "parses an URI string into a hash-map of its components"
      (and (= (:scheme uri) "http")
           (= (:host uri) "example.com")
           (= (:path uri) "/somewhere"))))
  (it "decodes query strings as application/x-www-form-urlencoded by default"
    (= {:foo "bar" :baz "qux"} (:query (parse-uri "?foo=bar&baz=qux"))))
  (it "accepts a second argument for toggling application/x-www-form-urlencoded decoding"
    (= "foo=bar&baz=qux" (:query (parse-uri "?foo=bar&baz=qux" false)))))


(describe make-uri
  (it "turns a hash-map into a URI"
    (= (URI. "ssh://hey@foo:99/bar?baz=qux&qux=quux")
       (make-uri {:scheme "ssh"
                  :user-info "hey"
                  :host "foo"
                  :port 99
                  :path "/bar"
                  :query "baz=qux&qux=quux"})))
  (it "application/x-www-form-urlencodes query parameters when they are given as a hash-map"
    (= (URI. "?foo=123&bar=baz")
       (make-uri {:query {:foo 123 :bar "baz"}})))
  (it "turns vector values into multiple params"
    (= (URI. "?foo=123&foo=456")
       (make-uri {:query {:foo [123 456]}}))))