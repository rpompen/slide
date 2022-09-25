(ns slide.core
  (:require [hoplon.core :refer [defelem div table for-tpl td tr th h1 text
                                 select option]]
            [hoplon.jquery]
            [hoplon.svg :as svg :refer [g svg circle line]]
            [slide.rpc :as rpc]
            [javelin.core :refer [defc defc= cell cell= dosync]]))

(defc selected-watch-name (first rpc/listed-watches))
(defc= selected-watch (first (filter #(= (:model %) selected-watch-name) rpc/watches)))
(defc= graduations (rpc/bezel selected-watch))

(defn rad->deg [x] (/ (* x 180) Math/PI))

(defn rotate-number
  [deg]
  (if-not (<= 0 deg 180)
    deg
    (- deg 180)))

(defelem radial
  [{:keys [cx cy rad radius len label label-distance upside-down outer-bezel] :as attr} _]
  (let [len (cell= (if (some? label) (* len 1.3) len))
        x2 (cell= (+ cx (* radius (Math/cos rad))))
        y2 (cell= (+ cy (* radius (Math/sin rad))))
        x1 (cell= (+ cx (* ((if outer-bezel + -) radius len) (Math/cos rad))))
        y1 (cell= (+ cy (* ((if outer-bezel + -) radius len) (Math/sin rad))))
        xl (cell= (+ cx (* ((if outer-bezel + -) radius len label-distance) (Math/cos rad))))
        yl (cell= (+ cy (* ((if outer-bezel + -) radius len label-distance) (Math/sin rad))))]
    (g (line :x1 x1 :y1 y1 :x2 x2 :y2 y2 attr)
       (svg/text :x xl :y yl :fill "white"
                 :text-anchor "middle" :dominant-baseline "middle"
                 :transform (cell= (str "rotate("
                                        (+ (if upside-down 
                                             (rad->deg rad)
                                             (rotate-number (rad->deg rad)))
                                           90) " " xl "," yl ")")) label))))

(defelem inner-bezel
  [{watch :watch} _]
  (let [diameter (cell= (:diameter watch))
        canvas (cell= (- diameter 8))
        unit  #(str % "mm")]
    (svg :viewBox "0 0 600 600"
         :width (cell= (unit canvas)) :height (cell= (unit canvas))
         (g (circle :cx 300 :cy 300 :r 248
                    :stroke "black" :stroke-width 1
                    :fill "navy")
            (for-tpl [[rad label] graduations]
                     [(radial :cx 300 :cy 300 :radius 248 :rad rad :len 12
                              :label label :label-distance 15
                              :upside-down (cell= (:upside-down watch))
                              :outer-bezel true
                              :style "stroke: white; stroke-width: 1")
                      (radial :cx 300 :cy 300 :radius 248 :rad rad :len 12
                              :label label :label-distance 15
                              :upside-down (cell= (:upside-down watch))
                              :outer-bezel false
                              :style "stroke: white; stroke-width: 1")])))))

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
