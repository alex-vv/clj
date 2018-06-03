(ns clj.core
  (:use [clj.utils]
        [clj.metadata]
        [clj.xmlrpc]
        [clojure.data.xml :only [sexp-as-element, indent-str]])
  (:require [clj-time.core :as joda]
            [clj-time.coerce :as joda-coerce]
            [clojure.java.io :as io]))

(def password)
(def username)

(defn exec
  "Wrapper to run xmlrpc with challenge using global username / password"
  [method args]
  (println (str "exec " method args))
  (xmlrpc-challenge username password method args))

; LIVEJOURNAL API
(defn get-events [args]
  (exec "LJ.XMLRPC.getevents" args))

(defn get-comments [journal ditemid itemid]
  (try
    (exec "LJ.XMLRPC.getcomments" {:journal journal :ditemid ditemid :itemid itemid :extra true})
    (catch Exception e
      (if (.contains (.getMessage e) "Don't have access to requested journal")
        (do
          (println "ERROR: No access to comments, skipping")
          {})
        (throw e)))))

(defn get-revtime
  "Get revtime (modification time) from a post if available, else logtime"
  [post]
  (let [revtime (-> post :props :revtime)]
    (if (nil? revtime)
      (to-date(:logtime post))
      ; revtime is in seconds from epoch
      (joda-coerce/from-epoch revtime))))

(defn cache-filename
  "Cache file name"
  [journal]
  (str journal "/.lastsync"))

(defn initial-date
  "Get initial date, either from cache or start of epoch"
  [journal]
  (if (.exists (io/file (cache-filename journal)))
    (slurp (cache-filename journal))
    (from-date (joda/epoch))))

(defn get-posts
  "Fetch posts starting from a date (lastsync)"
  [journal date]
  (let [events (:events (get-events {:journal journal
                                     :selecttype "syncitems"
                                     :lastsync (if (nil? date) (initial-date journal) date)
                                     :get_video_ids true}))]
    (sort-by get-revtime events)))

(defn fetch-all-posts
  "Fetch all posts, applying a callback function (with a side effect) to each of them"
  [journal callback]
  (loop [posts (get-posts journal nil)
         metadata (initial-metadata journal)]
    (let [reduced-meta (reduce callback metadata posts)]
      (if-not (empty? posts)
        (recur (get-posts journal (-> posts last get-revtime from-date))
               reduced-meta)))))

(defn comments-as-xml
  "Convert comments to xml fragment (recursively for threaded comments)"
  [comments]
  (->> comments
       (map (fn [comment]
        [:comment (-> (dissoc comment :body :children :poster_userpic_url :privileges :props)
                      (assoc :poster_ip (-> comment :props :poster_ip)))
          [:text
            [:-cdata (str (:body comment))]]
        (if (:children comment)
          [:comments (comments-as-xml (:children comment))]
          nil)]))))

(defn post-as-xml
  "Convert post to xml"
  [post]
  (sexp-as-element
    [:post (dissoc post :event :props :comments)
      [:props (:props post)]
      [:text
        [:-cdata (str (:event post))]]
     (if (:comments post)
        [:comments (comments-as-xml (:comments post))]
        nil)]))

(defn post-filename
  "Generate a file name to save a post"
  [post]
  (str (subs (:eventtime post) 0 10) "_" (:itemid post) ".xml"))

(defn post-dir
  "Generate a dir name to save a post"
  [post]
  (str (subs (:eventtime post) 0 4)))

(defn fetch-post-comments
  "Fetch comments for a post if they are available"
  [journal post]
  (if (= 0 (:reply_count post))
    post
    (assoc post :comments (:comments (get-comments journal (:ditemid post) (:itemid post))))))

(defn update-cache
  "Updates lastsync date cache"
  [journal post]
  (spit (cache-filename journal) (from-date (get-revtime post))))

(defn save-post
  "Saves a post"
  ([journal post]
  (let [dir (str journal "/" (post-dir post))]
    (mkdir journal)
    (mkdir dir)
    (spit (str dir "/" (post-filename post))
      (indent-str (post-as-xml post)))
    (update-cache journal post))))

(defn run
  "Fetch and save all posts"
  [journal]
  (fetch-all-posts journal
    (fn [metadata post]
      (let [post (fetch-post-comments journal post)
            meta (merge-metadata metadata (post-metadata journal post))]
        (save-post journal post)
        (save-metadata journal (metadata-as-xml meta))
        meta))))

(defn -main
  ([username password]
   (-main username password username))
  ([username password journal]
    (def username username)
    (def password password)
    (try
      (run journal)
      (catch Exception e
        (println (.getMessage e))
        (.printStackTrace e)))))
