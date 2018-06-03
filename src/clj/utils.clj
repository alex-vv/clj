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
