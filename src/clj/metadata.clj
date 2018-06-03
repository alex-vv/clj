(ns clj.metadata
  (:use [clj.utils]
        [clojure.data.xml :only [sexp-as-element, indent-str]])
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clj-time.core :as joda]))

(defn metadata-file
  "Metadata filename"
  [journal]
  (str journal "/metadata.xml"))

(defn map-users [m]
  (into [] (for [[k v] m] {:name k :comments (count v)})))

(defn users-comments-stats
  [comments]
  (map-users (group-by :postername comments)))

(defn flatten-comments
  "Flatten comments tree"
  [comments]
  (map #(dissoc %1 :children)
    (rest (tree-seq
      (fn [item] (some? (:children item)))
      (fn [item] (:children item))
      {:children comments}))))

(defn post-metadata
  "Generate metadata from post"
  [journal post]
  (let [flat-comments (flatten-comments (:comments post))]
    {:journal journal
     :entries {
       :first (:eventtime post)
       :last (:eventtime post)
       :total 1}
     :comments {
       :total (count flat-comments)
       :users (users-comments-stats flat-comments)}}))

(defn map-comments [m]
  (into [] (for [[k v] m] {:name k :comments (apply + (map :comments v))})))

(defn merge-comment-users
  [users1 users2]
  (map-comments (group-by :name (concat users1 users2))))

(defn merge-metadata
  "Merge two metadata maps in one"
  [m1 m2]
  {:journal (:journal m1)
   :entries {
     :first (earliest (-> m1 :entries :first) (-> m2 :entries :first))
     :last (latest (-> m1 :entries :last) (-> m2 :entries :last))
     :total (+ (-> m1 :entries :total) (-> m2 :entries :total))}
   :comments {
     :total (+ (-> m1 :comments :total) (-> m2 :comments :total))
     :users (sort-by :comments #(compare %2 %1)
              (merge-comment-users (-> m1 :comments :users) (-> m2 :comments :users)))}})

(defn last-updated
  "Generate last-updated timestamp"
  []
  (from-date (joda/now)))

(defn metadata-as-xml
  "Convert metadata to xml"
  [meta]
  (sexp-as-element
    [:metadata (assoc (dissoc meta :entries :comments) :last-updated (last-updated))
     [:entries (:entries meta)]
     [:comments {:total (-> meta :comments :total)}
        (apply list (map (fn [user] [:user user]) (-> meta :comments :users)))]]))

(defn save-metadata
  "Update post metadata"
  [journal meta]
  (spit (metadata-file journal)
    (indent-str meta)))

(defn metadata-from-xml
  "Read metadata from xml"
  [xml]
  (let [entries-attrs (-> xml :content first :attrs)
        comments (-> xml :content last)]
    {:journal (-> xml :attrs :journal)
    :entries {
      :first (:first entries-attrs)
      :last (:last entries-attrs)
      :total (Integer/parseInt (:total entries-attrs))
    }
    :comments {
      :total (Integer/parseInt (-> comments :attrs :total))
      :users
        (vec (map
          (fn [user-node]
            (update-in (:attrs user-node) [:comments] #(Integer/parseInt %)))
          (:content comments)))
    }}))

(defn xml-from-file
  "Read metadata XML from filename"
  [filename]
  (xml/parse (io/as-file filename)))

(defn metadata-from-file
  "Read metadata from journal's metadata file"
  [journal]
  (metadata-from-xml (xml-from-file (metadata-file journal))))

(defn initial-metadata
  "Get initial empty metadata for a journal.
  Tries to read it from metadata.xml or generate an empty one if not available"
  [journal]
  (if (.exists (io/as-file (metadata-file journal)))
    (metadata-from-file journal)
    {:journal journal
      :entries { :total 0 }
      :comments { :total 0 :users []}}))