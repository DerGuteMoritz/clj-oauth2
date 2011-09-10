(ns clj-oauth2.OAuth2Exception
  (:gen-class
   :extends java.lang.Exception
   :implements [clojure.lang.IDeref]
   :init init
   :state state
   :constructors {[String String] [String]}))

(defn -init [message type]
  [[message] [message type]])

(defn -deref
  [this]
  (.state this))