(ns clj.core
  (:use [clj-time.format :only [formatter, parse, unparse]]
        [digest :only [md5]]
        [clojure.data.xml :only [sexp-as-element, indent-str]])
  (:require [clj-time.core :as joda]
            [necessary-evil.core :as xml-rpc]
            [necessary-evil.fault :as xml-rpc-fault]
            [clojure.edn]))

(def password)
(def username)

; Run XML-RPC method for LJ API, returns response as a map
(defn xmlrpc [method-name args]
  (let [res (xml-rpc/call* "http://www.livejournal.com/interface/xmlrpc" method-name [args]
                  :request { :headers { "User-Agent" "CLJ v0.0.1 <avflance@gmail.com>" }})]
    (if (xml-rpc-fault/fault? res)
      (throw (Exception. (:fault-string res)))
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

; Run XML-RPC API with authentication challenge
(defn exec [method args]
  (println (str "exec" method args))
  (let [challenge (get-challenge)]
    (xmlrpc method
      (merge args (auth-params challenge (digest challenge))))))

; Mock data
(defn sync-items-mock []
  (clojure.edn/read-string (slurp "sync-items.edn")))

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

(defn update-if-contains [map key f] 
  (if (contains? map key)
    (update-in map [key] f)
    map))

(defn update-vals [map vals f]
  (reduce #(update-if-contains %1 %2 f) map vals))

(defn get-posts [date]
  (let [events (:events (get-events {:selecttype "syncitems"
                                     :lastsync (from-date date)}))]
    (map #(update-vals %1 [:event :subject] decode-str) events)))

(defn get-posts-mock [date]
  (if (and date (= 2008 (joda/year date)))
    (clojure.edn/read-string (slurp "get-posts.edn"))
    (get-posts date)))

(defn get-posts-for-syncitems [syncitems]
  (let [date (joda/minus (to-date (:time (first syncitems))) (joda/seconds 1))]
    (vec (get-posts date))))

(defn fetch-all-posts [callback]
  (loop [syncitems (all-sync-items)
         posts (get-posts-for-syncitems syncitems)
         fetched 0]
    (doall (map #(callback %1) posts))
    (if (< (+ fetched (count posts)) (count syncitems))
      ; we need to exclude downloaded posts from syncitems
      ; and run recursively for the sincitems left
      ; to filter, take max date from acc
      ; filter out all syncitems <= this date
      (let [maxdate (:eventtime (peek posts))
            filtered-syncitems (filter #(= 1 (compare (:time %1) maxdate)) syncitems)]
        (recur filtered-syncitems
               (get-posts-for-syncitems filtered-syncitems)
               (count posts))))))

(defn post-as-xml [post]
  (sexp-as-element
    [:post (dissoc post :event :props)
      [:text [:-cdata (:event post)]]]))

(defn post-filename [post]
  (str (subs (:logtime post) 0 10) ".xml"))

(defn post-dir [post]
  (str (subs (:logtime post) 0 4)))

(defn mkdir [dir]
  (.mkdir (java.io.File. dir)))

(defn save-post [post]
  (let [dir (str username "/" (post-dir post))]
    (mkdir username)
    (mkdir dir)
    (spit (str dir "/" (post-filename post)) 
      (indent-str (post-as-xml post)))))

(defn run []
  (fetch-all-posts save-post))

(defn -main
  [username password]
  (def username username)
  (def password password)
  (try
    (run)
    (catch Exception e
      (println (.getMessage e)))))
