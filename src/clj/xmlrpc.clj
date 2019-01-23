; XMLRPC utils
(ns clj.xmlrpc
  (:use [digest :only [md5]]
        [clojure.data.zip.xml :only [text]])
  (:require [necessary-evil.core :as xml-rpc]
            [necessary-evil.value :as xml-rpc-value]
            [necessary-evil.fault :as xml-rpc-fault])
  (:import org.apache.commons.codec.binary.Base64))

(defn safe-xml [s]
  (clojure.string/replace s #"[^\u0009\r\n\u0020-\uD7FF\uE000-\uFFFD\ud800\udc00-\udbff\udfff]" ""))

(defmethod xml-rpc-value/parse-value :base64 [v]
  (safe-xml (String. (Base64/decodeBase64 ^String (text v)) "UTF-8")))

(defn xmlrpc
  "Run XML-RPC method for LJ API, returns response as a map"
  [method-name args]
  (let [res (xml-rpc/call* "http://www.livejournal.com/interface/xmlrpc" method-name [args]
                           :request { :headers { "User-Agent" "CLJ v2.0.0 <avflance@gmail.com>"} :decompress-body false})]
    (if (xml-rpc-fault/fault? res)
      (throw (Exception. (:fault-string res)))
      res)))

(defn get-challenge []
  (:challenge (xmlrpc :LJ.XMLRPC.getchallenge [])))

(defn digest [challenge password]
  (md5 (str challenge (md5 password))))

(defn auth-params [username challenge digest]
  {:username username
   :auth_method "challenge"
   :auth_challenge challenge
   :auth_response digest
   :ver 1})

(defn xmlrpc-challenge
  "Run XML-RPC API with authentication challenge"
  [username password method args]
  (let [challenge (get-challenge)]
    (xmlrpc method
      (merge args
        (auth-params username challenge
          (digest challenge password))))))