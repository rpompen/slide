(ns slide.core
  (:require [slide.handler :as handler]
            [org.httpkit.server :refer [run-server]]
            [slide.shared :refer [port]])
  (:gen-class))

(def server (atom nil))

(defn app [port]
  (run-server handler/app {:port port}))

(defn start-server
  "Start web-server."
  [port]
  (swap! server #(or % (app port))))

(defn -main [] (start-server port))
