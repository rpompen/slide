(ns slide.core
  (:require [hoplon.core :refer [defelem div table for-tpl td tr th]]
            [hoplon.jquery]
            [hoplon.svg :refer [g svg]]
            [slide.rpc :as rpc]
            [javelin.core :refer [defc cell cell= dosync]]))

(defc graduations nil)
(reset! graduations (rpc/bezel rpc/breitling-navitimer))

(defelem main [_ _]
  (div :id "app"
       (table
        (tr (th "Radial") (th "Label"))
        (for-tpl [[radial label] graduations]
                 (tr (td radial) (td label))))))

(.replaceChild js/document.body
               (main)
               (.getElementById js/document "app"))
