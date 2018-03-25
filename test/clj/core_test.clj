(ns clj.core-test
  (:use [clojure.data.xml :only [indent-str emit-str]]
        [clj.utils])
  (:require [clojure.test :refer :all]
            [clj.core :refer :all]
            [clojure.edn]
            [clojure.java.io :as io]
            [clj-time.core :as joda]))

(defn fixture-string [name]
  (slurp (str "test/clj/fixtures/" name)))

(defn fixture-edn [name]
  (clojure.edn/read-string (fixture-string name)))


(deftest livejournal
  (with-redefs [exec (fn [method args] {:method method :args args})]
    (testing "get-events"
      (is (= "LJ.XMLRPC.getevents" (:method (get-events {:journal "test"}))))
      (is (= {:journal "test"} (:args (get-events {:journal "test"})))))
    (testing "get-comments"
      (is (= "LJ.XMLRPC.getcomments" (:method (get-comments "test" 123 456))))
      (is (= {:journal "test" :ditemid 123 :itemid 456 :extra true} (:args (get-comments "test" 123 456)))))))

(deftest fetching
  (testing "get revtime from post, fallback to logtime"
    (is (= "2008-09-19 13:42:13" (from-date (get-revtime {:props {:revtime 1221831733} :logtime "2008-01-19 10:26:46"}))))
    (is (= "2008-01-19 10:26:46" (from-date (get-revtime {:logtime "2008-01-19 10:26:46"})))))
  (testing "initial date no cache"
    (is (= "1970-01-01 00:00:00" (initial-date "test-journal"))))
  (testing "initial data with cache"
    (mkdir "test-journal")
    (spit "test-journal/.lastsync" "2009-05-19 11:02:46")
    (is (= "2009-05-19 11:02:46" (initial-date "test-journal")))
    (io/delete-file "test-journal/.lastsync")
    (io/delete-file "test-journal"))
  (testing "get-posts"
    (with-redefs [get-events (fn [args] {:events [{:props {:revtime 2}}, {:props {:revtime 1}}]})]
      (is (= [{:props {:revtime 1}}, {:props {:revtime 2}}] (get-posts "test" nil)))))
  (testing "fetch-post-comments"
    (with-redefs [get-comments (fn [journal ditemid itemid] {:comments "comments"})]
      (is (= {:reply_count 0} (fetch-post-comments "test" {:reply_count 0})))
      (is (= {:reply_count 1 :comments "comments"} (fetch-post-comments "test" {:reply_count 1}))))))

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

(deftest file
  (testing "save-post"
    (let [post (fixture-edn "post2.edn")
          post-xml (fixture-string "post2.xml")]
      (save-post "test-journal" post)
      (is (= post-xml (slurp "test-journal/2008/2008-01-21_1.xml")))
      (is (= "2009-05-19 11:02:46" (slurp "test-journal/.lastsync")))
      (io/delete-file "test-journal/.lastsync")
      (io/delete-file "test-journal/2008/2008-01-21_1.xml")
      (io/delete-file "test-journal/2008")
      (io/delete-file "test-journal"))))

