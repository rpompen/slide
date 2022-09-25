(ns slide.core
  (:require [slide.handler :as handler]
            [ring.adapter.jetty :refer [run-jetty]]
            [slide.shared :refer [port]])
  (:gen-class))

(def server (atom nil))

(defn app [port]
  (run-jetty handler/app {:join? false :port port}))

(defn start-server
  "Start web-server."
  [port]
  (swap! server #(or % (app port))))

(defn -main [] (start-server port))
