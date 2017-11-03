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

(deftest fetching
  (testing "get revtime from post"
    (is (= nil (get-revtime {:props {}})))
    (is (= "2008-09-19 13:42:13" (from-date (get-revtime {:props {:revtime 1221831733}})))))
  (testing "finding lastsync date for zero posts"
    (let [posts []]
      (is (= nil (last-sync-date posts))))))
  (testing "finding lastsync date for one post with logtime"
    (let [posts [{:logtime "2006-12-25 19:43:16", :props {}}]]
      (is (= "2006-12-25 19:43:16" (last-sync-date posts)))))
  (testing "finding lastsync date for one post with revtime"
    (let [posts [{:logtime "2006-12-25 19:43:16", :props {:revtime 1221831733}}]]
      (is (= "2008-09-19 13:42:13" (last-sync-date posts)))))
  (testing "finding lastsync date for several posts"
    (let [posts (fixture-edn "posts.edn")]
      (is (= "2008-09-19 13:42:13" (last-sync-date posts)))))

(deftest xml
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
