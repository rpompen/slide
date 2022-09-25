(ns slide.rpc)

;; Todo: extra graduations for unit conversion
;; Navitimer: 60 MPH, 61 KM, 36 SEC, 38 STAT. MI, 33 NAUT. MI.

(def watches [{:model "E6-B"
               :diameter 152
               :idx-on-top true
               :upside-down true
               :graduations [{:ticks [60 150]
                              :lbls [60 105 5]}
                             {:ticks [150 300 2]
                              :lbls [100 250 10]}
                             {:ticks [300 600 5]
                              :lbls [250 600 50]}]}
              
              {:model "breitling-navitimer"
               :diameter 152 ;; 46
               :graduations [{:ticks [60 120]
                              :lbls [60 105 5]}
                             {:ticks [120 250 2]
                              :lbls [100 250 10]}
                             {:ticks [250 600 5]
                              :lbls [250 600 50]}]}
              
              {:model "seiko-flightmaster"
               :diameter 152 ;; 45.5
               :graduations [{:ticks [60 150]
                              :lbls [60 105 5]}
                             {:ticks [150 300 2]
                              :lbls [100 250 10]}
                             {:ticks [300 600 5]
                              :lbls [250 600 50]}]}])

(defn- rule
  "Joins the ranges of the tick or label scale."
  [scale watch]
  (->> watch
       :graduations
       (mapcat #(apply range (get % scale)))))

(defn- chop-lbl
  "Shorten label to the 2 most significant digits."
  [lbl]
  (when (some? lbl)
    (let [lbl (str lbl)]
      (if (= 2 (count lbl))
        lbl
        (apply str (take 2 lbl))))))

(defn- tick->rad
  "Converts the graduation at a specific number to the radial."
  [tick idx-on-top?]
  (let [_log (Math/log10 tick)
        offset (if idx-on-top? 0 (Math/log10 6))
        log (- _log offset)]
    (- (* log 2 Math/PI) (* 2.5 Math/PI))))

(defn bezel
  "A seq of vectors containing a graduation's radial and label if present."
  [watch]
  (let [idx-on-top (:idx-on-top watch)
        ticks (rule :ticks watch)
        lbls (set (rule :lbls watch))]
    (into (sorted-map)
          (map #(vector (tick->rad % idx-on-top) (chop-lbl (lbls %))) ticks))))

(def listed-watches (map :model watches))
