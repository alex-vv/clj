(ns clj.core)

(require '[necessary-evil.core :as xml-rpc])

(defn xmlrpc [method-name args]
  (xml-rpc/call* "http://www.livejournal.com/interface/xmlrpc" method-name args 
                  :request { :headers { "User-Agent" "CLJ v0.0.1 <avflance@gmail.com>" }}))
  
(defn auth []
  (xmlrpc :LJ.XMLRPC.getchallenge []))

(defn -main
  []
  (println (auth)))