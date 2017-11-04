(ns clj.core
  (:use [clj.utils]
        [clj.xmlrpc]
        [clojure.data.xml :only [sexp-as-element, indent-str]])
  (:require [clj-time.core :as joda]
            [clj-time.coerce :as joda-coerce]))

(def password)
(def username)
(def journal nil)

; wrapper to run xmlrpc with challenge using global username / password
(defn exec [method args]
  (println (str "exec " method args))
  (xmlrpc-challenge username password method args))

; LIVEJOURNAL API
(defn get-events [args]
  (exec "LJ.XMLRPC.getevents" args))

(defn get-comments [ditemid itemid]
  (exec "LJ.XMLRPC.getcomments" {:journal journal :ditemid ditemid :itemid itemid :extra true}))


; get revtime (modification time) from a post if available
(defn get-revtime [post]
  (let [revtime (-> post :props :revtime)]
    (if (nil? revtime)
      nil
      ; revtime is in seconds from epoch
      (joda-coerce/from-epoch revtime))))

; get last sync date from a collection of posts, that is a maximum date from a list of latest from logtime and revtime
(defn last-sync-date [posts]
  (if (empty? posts)
    nil
    (from-date
      (->> posts
         (map #(joda/latest (to-date(:logtime %1)) (get-revtime %1)))
         (reduce #(joda/latest %1 %2))))))

; fetch posts starting from a date (lastsync)
(defn get-posts [date]
  (let [events (:events (get-events {:journal journal
                                     :selecttype "syncitems"
                                     :lastsync (if (nil? date) (from-date (joda/epoch)) date)
                                     :get_video_ids true}))]
    (map #(update-vals %1 [:event :subject] decode-str) events)))

; fetch all posts, applying a callback function (with a side effect) to each of them
(defn fetch-all-posts [callback]
  (loop [posts (get-posts nil)]
    (doall (map #(callback %1) posts))
    (if-not (empty? posts)
      (recur (get-posts (last-sync-date posts))))))

; convert comments to xml fragment (recursively for threaded comments)
(defn comments-as-xml [comments]
  (->> comments
       (map (fn [comment]
        [:comment (-> (dissoc comment :body :children :poster_userpic_url :privileges :props)
                      (update-if-contains :subject decode-str)
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
      [:props (update-if-contains (:props post) :taglist decode-str)]
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
  (let [dir (str journal "/" (post-dir post))]
    (mkdir journal)
    (mkdir dir)
    (spit (str dir "/" (post-filename post))
      (indent-str (post-as-xml post)))))

; fetch and save all posts
(defn run []
  (fetch-all-posts #(-> %1
                        fetch-post-comments
                        save-post)))

(defn -main
  ([username password]
   (-main username password username))
  ([username password journal]
    (def username username)
    (def password password)
    (def journal journal)
    (try
      (run)
      (catch Exception e
        (println (.getMessage e))
        (.printStackTrace e)))))
