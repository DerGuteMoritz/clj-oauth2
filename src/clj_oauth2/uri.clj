(ns clj-oauth2.uri
  (:use [clojure.contrib.java-utils]
        [clojure.pprint :only (write)])
  (:require [clojure.string :as str])
  (:import [java.net URI URLEncoder URLDecoder]
           [java.lang IllegalArgumentException]))

(defn throw-argument-error [msg arg]
  (throw (IllegalArgumentException.
          (format msg (write arg :stream nil)))))

(defmacro let-uri-parts [arg bindings & body]
  (let [args (arg 0)
        uri (arg 1)]
    `(let ~(vec (mapcat
                 (fn [[name getter]]
                   [name `(or (~(keyword name) ~args)
                              (. ~uri ~getter))])
                 (partition 2 bindings)))
       ~@body)))

(defn update-uri [uri arg]
  (cond (instance? URI arg)
        (. uri resolve arg)
        (string? arg)
        (update-uri uri (URI. arg))
        (map? arg)
        (let-uri-parts [arg uri]
          [scheme getScheme
           user-info getUserInfo
           host getHost
           port getPort
           path getPath
           query getQuery
           fragment getFragment]
          (URI. scheme user-info host port path query fragment))
        :else
        (throw-argument-error "can't update URI with %s" arg)))

(defn make-uri [arg]
  (cond (map? arg)
        (update-uri (URI. "") arg)
        (string? arg)
        (URI. arg)
        (instance? URI arg)
        arg
        :else
        (throw-argument-error "can't construct URI from %s" arg)))