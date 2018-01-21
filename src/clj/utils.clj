; Basic utils
(ns clj.utils
  (:use [clj-time.format :only [formatter, parse, unparse]]))

(def custom-formatter (formatter "yyyy-MM-dd HH:mm:ss"))

(defn to-date [date-str]
  (parse custom-formatter date-str))

(defn from-date [date]
  (unparse custom-formatter date))

(defn mkdir [dir]
  (.mkdir (java.io.File. dir)))
