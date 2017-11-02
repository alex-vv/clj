(ns clj.core-test
  (:use [clojure.data.xml :only [indent-str emit-str]]
        [clj.utils])
  (:require [clojure.test :refer :all]
            [clj.core :refer :all]
            [clojure.edn]))

(defn fixture-string [name]
  (slurp (str "test/clj/fixtures/" name)))

(defn fixture-edn [name]
  (clojure.edn/read-string (fixture-string name)))

(deftest a-test
  (testing "comments as xml"
    (let [comments (fixture-edn "comments.edn")]
      (is (= 3 (count (comments-as-xml comments))))))
  (testing "post as xml"
    (let [post (fixture-edn "post.edn")
          post-xml (fixture-string "post.xml")]
      (is (= post-xml (indent-str (post-as-xml post))))))
  (testing "post as xml without comments"
    (let [post (fixture-edn "post2.edn")
          post-xml (fixture-string "post2.xml")]
      (is (= post-xml (indent-str (post-as-xml post)))))))
