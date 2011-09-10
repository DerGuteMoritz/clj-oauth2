(ns clj-oauth2.OAuth2StateMismatchException
  (:gen-class
   :extends java.lang.Exception
   :implements [clojure.lang.IDeref]
   :init init
   :state state
   :constructors {[String String String] [String]}))

(defn -init [message given expected]
  [[message] {:message message :given given :expected expected}])

(defn -deref
  [this]
  (.state this))