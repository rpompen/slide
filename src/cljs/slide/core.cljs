(ns slide.core
  (:require [hoplon.core :refer [defelem div table for-tpl td tr th h1 text
                                 select option]]
            [hoplon.jquery]
            [hoplon.svg :refer [g svg circle line]]
            [slide.rpc :as rpc]
            [javelin.core :refer [defc defc= cell cell= dosync]]))

(defc selected-watch-name (first rpc/listed-watches))
(defc= selected-watch (first (filter #(= (:model %) selected-watch-name) rpc/watches)))
(defc= graduations (rpc/bezel selected-watch))

(defelem bezel-table
  [_ _]
  (table
   (tr (th "Radial") (th "Label"))
   (for-tpl [[radial label] graduations]
            (tr (td radial) (td label)))))

(defelem radial
  [{:keys [cx cy rad radius len] :as attr} _]
  (let [x2 (cell= (+ cx (* radius (Math/cos rad))))
        y2 (cell= (+ cy (* radius (Math/sin rad))))
        x1 (cell= (+ cx (* (- radius len) (Math/cos rad))))
        y1 (cell= (+ cy (* (- radius len) (Math/sin rad))))]
    (line :x1 x1 :y1 y1 :x2 x2 :y2 y2 attr)))

(defelem inner-bezel
  [{watch :watch} _]
  (let [diameter (cell= (:diameter watch))
        canvas (cell= (- diameter 8))
        unit  #(str % "mm")]
    (svg :viewBox "0 0 500 500"
         :width (cell= (unit canvas)) :height (cell= (unit canvas))
         (g (circle :cx 250 :cy 250 :r 250
                    :stroke "green" :stroke-width 2
                    :fill "yellow")
            (for-tpl [[rad] graduations]
                     (radial :cx 250 :cy 250 :radius 250 :rad rad :len 12
                             :style "stroke: red; stroke-width: 2"))))))

(defelem main [_ _]
  (div :id "app"
       (select :value selected-watch-name
               :change #(reset! selected-watch-name @%)
               (for-tpl [w (cell rpc/listed-watches)]
                        (option :value w w)))
       (inner-bezel :watch selected-watch)))

(.replaceChild js/document.body
               (main)
               (.getElementById js/document "app"))
