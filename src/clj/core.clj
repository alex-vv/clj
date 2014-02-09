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

(defn exec [method args]
  (println (str "exec" method args))
  (let [challenge (get-challenge)]
    (xmlrpc method
      (merge args (auth-params challenge (digest challenge))))))

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
    (println (str "all-sync-items items " items))
    (loop [acc (:syncitems items)]
      (if-not (< (count acc) total)
        (filter #(.startsWith (:item %1) "L-") acc)
        (recur (conj acc (sync-items (:time (peek acc)))))))))

(def custom-formatter (formatter "yyyy-MM-dd HH:mm:ss"))

(defn to-date [date-str]
  (println (str "to-date " date-str ";"))
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

; TODO get all posts not the first 100
(defn get-posts [date]
  (let [events (:events (get-events {:selecttype "syncitems"
                                     :lastsync (from-date date)}))]
    (map #(update-vals %1 [:event :subject] decode-str) events)))

(defn get-posts-mock [date]
  (if (and date (= 2008 (joda/year date)))
    (clojure.edn/read-string (slurp "get-posts.edn"))
    (get-posts date)))

(defn get-posts-for-syncitems [syncitems]
  (println "get-posts-for-syncitems" syncitems)
  (let [date (joda/minus (to-date (:time (first syncitems))) (joda/secs 14))]
    (vec (get-posts date))))

(defn get-all-posts []
  ; change to all-sync-items
  (loop [syncitems (all-sync-items)
         acc (get-posts-for-syncitems syncitems)]
    (println (str "get-all-posts syncitems " syncitems))
    (if-not (< (count acc) (count syncitems))
      acc
      ; else we need to exclude downloaded posts from syncitems
      ; and run recursively for the sincitems left
      ; to filter, take max date from acc
      ; filter out all syncitems <= this date
      (let [maxdate (:eventtime (peek acc))
            filtered-syncitems (filter #(= 1 (compare (:time %1) maxdate)) syncitems)]
        (recur filtered-syncitems
               (concat acc (get-posts-for-syncitems filtered-syncitems)))))))

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
  (println (str "Saving post: " post))
  (let [dir (str username "/" (post-dir post))]
    (mkdir username)
    (mkdir dir)
    (spit (str dir "/" (post-filename post)) 
      (indent-str (post-as-xml post)))))

(defn save-posts [posts]
  (println (str "Saving posts: " (count posts)))
  (doall (map #(save-post %1) posts)))

(defn run []
  (save-posts (get-all-posts)))

(defn -main
  [username password]
  (def username username)
  (def password password)
  (try
    (run)
    (catch Exception e
      (println (.getMessage e)))))
