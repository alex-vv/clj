; XMLRPC utils
(ns clj.xmlrpc
  (:use [digest :only [md5]])
  (:require [necessary-evil.core :as xml-rpc]
            [necessary-evil.fault :as xml-rpc-fault]))

; Run XML-RPC method for LJ API, returns response as a map
(defn xmlrpc [method-name args]
  (let [res (xml-rpc/call* "http://www.livejournal.com/interface/xmlrpc" method-name [args]
                           :request { :headers { "User-Agent" "CLJ v0.1.0 <avflance@gmail.com>"} :decompress-body false})]
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

; Run XML-RPC API with authentication challenge
(defn xmlrpc-challenge [username password method args]
  (let [challenge (get-challenge)]
    (xmlrpc method
      (merge args
        (auth-params username challenge
          (digest challenge password))))))