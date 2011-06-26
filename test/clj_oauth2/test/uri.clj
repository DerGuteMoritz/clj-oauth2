(ns clj-oauth2.test.uri
  (:use [lazytest.describe]
        [clj-oauth2.uri])
  (:import [java.net URI]))

(describe make-uri
  (it "returns a java.net.URI instance"
    (instance? URI (make-uri "")))
  (it "returns URI instances unchanged"
    (let [uri (URI. "/foo")]
      (= uri (make-uri uri))))
  (it "can construct a URI from a map"
    (= (URI. "http://foo@localhost:8080/bar")
       (make-uri {:host "localhost"
                  :port 8080
                  :scheme "http"
                  :path "/bar"
                  :user-info "foo"})))
  (it "can parse strings into URI instance"
    (= (URI. "https://example.com/")
       (make-uri "https://example.com/"))))


(describe update-uri
  (given [uri (make-uri "http://example.com/foo/")]
    (it "replaces or adds URI parts given as a map"
      (= (URI. "https://example.com:8090/bar")
         (update-uri uri {:scheme "https" :path "/bar" :port 8090})))
    (it "resolves another URI instance against the given one"
      (= (URI. "http://example.com/foo/bar")
         (update-uri uri (make-uri "bar"))))
    (it "does the same when a string is given"
      (= (URI. "http://example.com/bar")
         (update-uri uri "/bar")))))