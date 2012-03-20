(defproject clj-oauth2 "0.3.0"
  :min-lein-version "2.0.0"
  :description "clj-http and ring middlewares for OAuth 2.0"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.json "0.1.1"]
                 [clj-http "0.3.2"]
                 [uri "1.1.0"]
                 [commons-codec/commons-codec "1.6"]]
  :profiles {:dev {:dependencies [[ring "0.3.11"]]}}
  :aot [clj-oauth2.OAuth2Exception
        clj-oauth2.OAuth2StateMismatchException])
