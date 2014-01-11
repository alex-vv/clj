(ns clj.core)

(require '[necessary-evil.core :as xml-rpc])
(require 'digest)

(def password)
(def username)

(defn xmlrpc [method-name args]
  (xml-rpc/call* "http://www.livejournal.com/interface/xmlrpc" method-name [args]
                  :request { :headers { "User-Agent" "CLJ v0.0.1 <avflance@gmail.com>" }}))

(defn get-challenge []
  (:challenge (xmlrpc :LJ.XMLRPC.getchallenge [])))

(defn digest [challenge]
  (digest/md5 (str challenge (digest/md5 password))))

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
  (exec "LJ.XMLRPC.syncitems"
    (if last-sync
        {:lastsync last-sync}
        nil)))

(defn run []
  (sync-items "2010-01-01 00:00:00"))

(defn -main
  [username password]
  (def username username)
  (def password password)
  (println (run)))

(run)