; Basic utils
(ns clj.utils
  (:use [clj-time.format :only [formatter, parse, unparse]]))

(def custom-formatter (formatter "yyyy-MM-dd HH:mm:ss"))

(defn to-date [date-str]
  (parse custom-formatter date-str))

(defn from-date [date]
  (unparse custom-formatter date))

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

(defn mkdir [dir]
  (.mkdir (java.io.File. dir)))
