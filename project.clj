(defproject clj "2.0.0-SNAPSHOT"
  :description "LiveJournal archive tool"
  :url "https://github.com/alex-vv/clj"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [necessary-evil "2.0.2"]
                 [digest "1.4.6"]
                 [clj-time "0.14.0"],
                 [org.clojure/data.xml "0.2.0-alpha5"]
                 [org.clojure/tools.cli "0.3.7"]
                 [org.jsoup/jsoup "1.11.3"]]
  :main clj.core
  :aot [clj.core])
