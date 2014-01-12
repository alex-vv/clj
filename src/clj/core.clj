(ns clj.core
  (:use [clj-time.format :only [formatter, parse, unparse]]
        [digest :only [md5]])
  (:require [clj-time.core :as joda]
            [necessary-evil.core :as xml-rpc]
            [necessary-evil.fault :as xml-rpc-fault]))

(def password)
(def username)

(defn xmlrpc [method-name args]
  (let [res (xml-rpc/call* "http://www.livejournal.com/interface/xmlrpc" method-name [args]
                  :request { :headers { "User-Agent" "CLJ v0.0.1 <avflance@gmail.com>" }})]
    (if (xml-rpc-fault/fault? res)
      (println (:fault-string res))
      res)))


(defn get-challenge []
  (:challenge (xmlrpc :LJ.XMLRPC.getchallenge [])))

(defn digest [challenge]
  (md5 (str challenge (md5 password))))

(defn auth-params [challenge digest]
  {:username username
   :auth_method "challenge"
   :auth_challenge challenge
   :auth_response digest
   :ver 1})

(defn exec [method args]
  (let [challenge (get-challenge)]
    (xmlrpc method
      (merge args (auth-params challenge (digest challenge))))))

(defn sync-items [last-sync]
  (println "sync-items" last-sync)
  (exec "LJ.XMLRPC.syncitems"
    (if last-sync
        {:lastsync last-sync}
        nil)))

(defn get-events [args]
  (println "get-events" args)
  (exec "LJ.XMLRPC.getevents" args))

(defn all-sync-items []
  (let [items (sync-items nil)
        total (:total items)]
    (loop [acc (:syncitems items)]
      (if-not (< (count acc) total)
        (filter #(.startsWith (:item %1) "L-") acc)
        (recur (conj acc (sync-items (:time (peek acc)))))))))

(def custom-formatter (formatter "yyyy-MM-dd HH:mm:ss"))

(defn to-date [date-str]
  (parse custom-formatter date-str))

(defn from-date [date-str]
  (unparse custom-formatter date-str))

(defn decode-str [s]
  (if (string? s)
    s
    (String. s "UTF-8")))

(defn map-keys [f coll key]
  #(assoc %1 key (f (key %1))) coll)

(defn get-posts []
  (let [date (joda/minus (to-date (:time (first (all-sync-items)))) (joda/secs 4))
        events (get-events {:selecttype "syncitems"
                 :lastsync (from-date date)})]
    (map-keys decode-str (:events events) :event)))

(defn run []
  (get-posts))

(defn -main
  [username password]
  (def username username)
  (def password password)
  (println (run)))

;(run)
;(map-keys decode-str {:a "123" :b "234"} :a)

