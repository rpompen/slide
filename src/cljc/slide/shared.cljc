(ns slide.shared)

(def secure? false)
(def server "rpompen-lt")
(def port 8000)

(def db-server server)
(def db-port (if secure? 6984 5984))
(def db-name "sample")
