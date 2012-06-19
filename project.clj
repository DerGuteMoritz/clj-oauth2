(defproject org.clojars.the-kenny/clj-oauth2 "0.3.1"
  :min-lein-version "2.0.0"
  :description "clj-http and ring middlewares for OAuth 2.0"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.json "0.1.1"]
                 [clj-http "0.2.6"]
                 [uri "1.1.0"]
                 [commons-codec/commons-codec "1.6"]]
  :exclusions   [org.clojure/clojure-contrib]
  :profiles {:dev {:dependencies [[ring "0.3.11"]]}}
  :repositories {"stuartsierra-releases" "http://stuartsierra.com/maven2"}
  :aot [clj-oauth2.OAuth2Exception
        clj-oauth2.OAuth2StateMismatchException])
