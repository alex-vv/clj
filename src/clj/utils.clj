; Basic utils
(ns clj.utils
  (:use [clj-time.format :only [formatter, parse, unparse]]))

(def custom-formatter (formatter "yyyy-MM-dd HH:mm:ss"))

(defn to-date [date-str]
  (parse custom-formatter date-str))

(defn from-date [date]
  (unparse custom-formatter date))

(defn latest [date-str1 date-str2]
  (cond
    (nil? date-str1) date-str2
    (nil? date-str2) date-str1
    (= 1 (compare (to-date date-str1) (to-date date-str2))) date-str1
    :else date-str2))

(defn earliest [date-str1 date-str2]
  (cond
    (nil? date-str1) date-str2
    (nil? date-str2) date-str1
    (= 1 (compare (to-date date-str1) (to-date date-str2))) date-str2
    :else date-str1))

(defn mkdir [dir]
  (.mkdir (java.io.File. dir)))

; https://groups.google.com/d/msg/clojure/r77ydJ0tnLI/jG3dbzOUAwAJ
(defn read-password [prompt]
  (if (= "user" (str (.getName *ns*)))
    (do
      (print (format "%s [will be echoed to the screen]" prompt))
      (flush)
      (read-line))
    (let [console (System/console)
          chars   (.readPassword console "%s" (into-array [prompt]))]
      (apply str chars))))