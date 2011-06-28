(defproject clj-oauth2 "0.0.1-SNAPSHOT"
  :description "clj-http and ring middlewares for OAuth 2.0"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [clj-http "0.1.2"]
                 [com.sun.jersey/jersey-server "1.8"]
                 [javax.ws.rs/jsr311-api "1.1.1"]]

  :dev-dependencies [[org.clojure/clojure "1.2.1"]
                     [org.clojure/clojure-contrib "1.2.0"]
                     [clj-http "0.1.2"]
                     [com.sun.jersey/jersey-server "1.8"]
                     [javax.ws.rs/jsr311-api "1.1.1"]
                     [ring "0.3.9"]                     
                     [swank-clojure "1.4.0-SNAPSHOT"] ;; meh
                     [com.stuartsierra/lazytest "1.1.2"]
                     [lein-autotest "1.2.0"]]

  :repositories {"stuartsierra-releases" "http://stuartsierra.com/maven2"
                 "java" "http://download.java.net/maven/2"})