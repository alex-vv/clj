(ns clj.core
  (:use [clj.utils]
        [clj.xmlrpc]
        [clojure.data.xml :only [sexp-as-element, indent-str]])
  (:require [clj-time.core :as joda]))

(def password)
(def username)

; wrapper to run xmlrpc with challenge using global username / password
(defn exec [method args]
  (println (str "exec " method args))
  (xmlrpc-challenge username password method args))

; LIVEJOURNAL API
(defn sync-items [last-sync]
  (exec "LJ.XMLRPC.syncitems"
    (if last-sync
        {:lastsync last-sync}
        nil)))

(defn get-events [args]
  (exec "LJ.XMLRPC.getevents" args))

(defn get-comments [ditemid itemid]
  (exec "LJ.XMLRPC.getcomments" {:journal username :ditemid ditemid :itemid itemid :extra true}))


; fetch all syncitems of 'L' type (journal entries), possibly recursively in several steps
(defn all-sync-items []
  (let [items (sync-items nil)
        total (:total items)]
    (loop [acc (:syncitems items)]
      (if-not (< (count acc) total)
        (filter #(.startsWith (:item %1) "L-") acc)
        (recur (conj acc (sync-items (:time (peek acc)))))))))

; fetch posts starting from a date (lastsync)
(defn get-posts [date]
  (let [events (:events (get-events {:selecttype "syncitems"
                                     :lastsync (from-date date)}))]
    (map #(update-vals %1 [:event :subject] decode-str) events)))

; fetch posts from the date of the first element in syncitems
(defn get-posts-for-syncitems [syncitems]
  (let [date (joda/minus (to-date (:time (first syncitems))) (joda/seconds 1))]
    (vec (get-posts date))))

; fetch all posts, applying a callback function (with a side effect) to each of them
(defn fetch-all-posts [callback]
  (loop [syncitems (all-sync-items)
         posts (get-posts-for-syncitems syncitems)
         fetched 0]
    (doall (map #(callback %1) posts))
    (if (< (+ fetched (count posts)) (count syncitems))
      ; we need to exclude downloaded posts from syncitems
      ; and run recursively for the syncitems left
      ; to filter, take max date from posts
      ; filter out all syncitems <= this date
      (let [maxdate (:eventtime (peek posts))
            filtered-syncitems (filter #(= 1 (compare (:time %1) maxdate)) syncitems)]
        (recur filtered-syncitems
               (get-posts-for-syncitems filtered-syncitems)
               (count posts))))))

; convert comments to xml fragment (recursively for threaded comments)
(defn comments-as-xml [comments]
  (->> comments
       (map (fn [comment]
        [:comment (-> (dissoc comment :body :children :poster_userpic_url :privileges :props)
                      (assoc :poster_ip (-> comment :props :poster_ip)))
          [:text
            [:-cdata (decode-str (:body comment))]]
        (if (:children comment)
          [:comments (comments-as-xml (:children comment))]
          nil)]))))

; convert post to xml
(defn post-as-xml [post]
  (sexp-as-element
    [:post (dissoc post :event :props :comments)
      [:text
        [:-cdata (:event post)]]
     (if (:comments post)
        [:comments (comments-as-xml (:comments post))]
        nil)]))

; generate a file name to save a post
(defn post-filename [post]
  (str (subs (:logtime post) 0 10) ".xml"))

; generate a dir name to save a post
(defn post-dir [post]
  (str (subs (:logtime post) 0 4)))

; fetch comments for a post if they are available
(defn fetch-post-comments [post]
  (if (= 0 (:reply_count post))
    post
    (assoc post :comments (:comments (get-comments (:ditemid post) (:itemid post))))))

; saves a post
(defn save-post [post]
  (let [dir (str username "/" (post-dir post))]
    (mkdir username)
    (mkdir dir)
    (spit (str dir "/" (post-filename post))
      (indent-str (post-as-xml post)))))

; fetch and save all posts
(defn run []
  (fetch-all-posts #(-> %1
                        fetch-post-comments
                        save-post)))

(defn -main
  [username password]
  (def username username)
  (def password password)
  (try
    (run)
    (catch Exception e
      (println (.getMessage e)))))
