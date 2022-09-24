(ns slide.api
  (:require [clojure.edn :as edn]))

(defmacro defrpc [f args & body]
  `(let [e# nil]
     (defn ~f ~args (try ~@body (catch Exception e# {:castraexpt (ex-message e#)})))))

(defrpc get-file []
  (edn/read-string (slurp "data/items.edn")))
