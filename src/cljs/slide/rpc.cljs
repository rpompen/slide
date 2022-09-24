(ns slide.rpc)

;; Todo: extra graduations for unit conversion
;; Navitimer: 60 MPH, 61 KM, 36 SEC, 38 STAT. MI, 33 NAUT. MI.

(def breitling-navitimer [{:ticks [60 120]
                           :lbls [60 105 5]}
                          {:ticks [120 250 2]
                           :lbls [100 250 10]}
                          {:ticks [250 600 5]
                           :lbls [250 600 50]}])

;; Also the Casio Edifice 527D
(def seiko-flightmaster [{:ticks [60 150]
                          :lbls [60 105 5]}
                         {:ticks [150 300 2]
                          :lbls [100 250 10]}
                         {:ticks [300 600 5]
                          :lbls [250 600 50]}])

(defn- rule
  "Joins the ranges of the tick or label scale."
  [scale watch]
  (mapcat #(apply range (get % scale)) watch))

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
  [tick]
  (let [_log (Math/log10 tick)
        offset (Math/log10 6)
        log (- _log offset)]
    (- (* 2.5 Math/PI) (* log 2 Math/PI))))

(defn bezel
  "A seq of vectors containing a graduation's radial and label if present."
  [watch]
  (let [ticks (rule :ticks watch)
        lbls (set (rule :lbls watch))]
    (into (sorted-map)
          (map #(vector (tick->rad %) (chop-lbl (lbls %))) ticks))))
