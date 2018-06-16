(ns clj.core
  (:gen-class)
  (:use [clj.utils]
        [clj.metadata]
        [clj.xmlrpc]
        [clojure.data.xml :only [sexp-as-element, indent-str]])
  (:require [clj-time.core :as joda]
            [clj-time.coerce :as joda-coerce]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.tools.cli :refer [parse-opts]]))

(def password)
(def username)

(defn exec
  "Wrapper to run xmlrpc with challenge using global username / password.
  In case password is nil method is called directly without a challenge"
  [method args]
  (println (str "exec " method args))
  (if (nil? password)
    (xmlrpc method (merge {:ver 1} args))
    (xmlrpc-challenge username password method args)))

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

(def usage
  (->> ["CLJ - LiveJournal downloader, version: 2.0.0-SNAPSHOT"
        ""
        "Usage: java -jar clj-2.0.0-SNAPSHOT-standalone.jar username [journal] [-p]"
        ""
        "  username  LiveJournal user name"
        "  journal   Journal to download. Optional, if not set will fetch user's own journal."
        "  -p        Ask for a password, without a password only public entries will be downloaded"]
       (string/join \newline)))

(defn exit []
  (println usage)
  (System/exit 1))

(def cli-options
  [["-p" nil "Ask for a password"
    :id :password]])

(defn -main [& args]
  "Application entry point"
  (let [opts (parse-opts args cli-options)
        arguments (:arguments opts)]
    (if (empty? arguments)
      (exit)
      (let [username (first arguments)
            journal (if (= 1 (count arguments)) username (last arguments))]
        (def username username)
        (def password
          (if (-> opts :options :password)
            (read-password "LJ password")
            (System/getenv "CLJ_PASSWORD")))
        (try
          (run journal)
          (catch Exception e
            (println (.getMessage e))
            (.printStackTrace e)))))))
