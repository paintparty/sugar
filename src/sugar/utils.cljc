(ns sugar.utils
  "Color conversion utilities: hex/RGB/8-bit conversions and x256 color mapping.")

;; Regex
(def hex-color-re #"^#?([A-Fa-f0-9]{8}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{4}|[A-Fa-f0-9]{3})$")

;; helper fns

(defn hex->rgb*
  "Converts hex digits to [[r g b] alpha], with or without a leading #.
   Accepts 3 (RGB), 4 (RGBA), 6 (RRGGBB), or 8 (RRGGBBAA) hex chars."
  [s]
  (let [s (if (= \# (first s)) (subs s 1) s)
        s (case (count s)
            3 (apply str (mapcat #(vector % %) s))
            4 (apply str (mapcat #(vector % %) s))
            s)
        [r1 r2 g1 g2 b1 b2 a1 a2] s
        f #?(:cljs #(js/parseInt (str "0x" %1 %2))
             :clj #(Integer/valueOf (str %1 %2) 16))
        r (f r1 r2)
        g (f g1 g2)
        b (f b1 b2)
        ;; Alpha from 0 to 1, rounded to 2 decimals. Arithmetic, not
        ;; format, so a locale with comma decimals can't break it.
        a (when (and a1 a2)
            (/ (Math/round (* 100.0 (/ (f a1 a2) 255))) 100.0))]
    [[r g b] a]))


(defn rgb->8-bit
  "Uses a Manhattan distance algorithm to convert RGB to 8-bit color.
   Components outside 0 to 255 are clamped to that range."
  [r g b]
  (letfn [(clamp [v] (max 0 (min 255 v)))
          (trunc [v]
            (if (neg? v)
              #?(:cljs
                 (js/Math.ceil v)
                 :clj
                 (Math/ceil v))
              #?(:cljs
                 (js/Math.floor v)
                 :clj
                 (Math/floor v))))
          (dist-sq [x, y, z, r, g, b]
            (+ (* (- x r) (- x r))
               (* (- y g) (- y g))
               (* (- z b) (- z b))))
          (ci [c]
            (cond
              (< c 48)  0
              (< c 115) 1
              :else     (trunc (/ (- c 35) 40))))]
    (let [r        (clamp r)
          g        (clamp g)
          b        (clamp b)
          ri       (ci r)
          gi       (ci g)
          bi       (ci b)
          clr-i    (+ (* 36 ri) (* 6 gi) bi)
          avg      (trunc (/ (+ r g b)  3))
          gry-i    (if (> avg 238) 23 (trunc (/ (- avg 3) 10)))
          hexes    [0, 0x5F, 0x87, 0xAF, 0xD7, 0xFF]
          rc       (nth hexes ri)
          gc       (nth hexes gi)
          bc       (nth hexes bi)
          gry      (+ 8 (* 10 gry-i))
          clr-dist (dist-sq rc, gc, bc, r, g, b)
          gry-dist (dist-sq gry, gry, gry, r, g, b)
          ret      (if (<= clr-dist gry-dist)
                     (+ 16 clr-i)
                     (+ 232 gry-i))
          ret      #?(:cljs
                      (js/Math.round ret)
                      :clj
                      (cond (double? ret)
                            (Math/round (double ret))
                            :else
                            ret))]
      ret)))

(def x256-rgb-table
  "The [r g b] value of each 8-bit color id, 0 to 255."
  (vec
   (for [code (range 256)]
     (cond
       ;; Standard 16 colors
       (< code 16)
       (nth [[0 0 0]
             [128 0 0]
             [0 128 0]
             [128 128 0]
             [0 0 128]
             [128 0 128]
             [0 128 128]
             [192 192 192]
             [128 128 128]
             [255 0 0]
             [0 255 0]
             [255 255 0]
             [0 0 255]
             [255 0 255]
             [0 255 255]
             [255 255 255]]
            code)

       ;; 6x6x6 RGB color cube
       (< code 232)
       (let [c (- code 16)
             cube-step #(if (zero? %) 0 (+ 55 (* 40 %))) ; Standard xterm color intervals
             r (cube-step (quot c 36))
             g (cube-step (quot (mod c 36) 6))
             b (cube-step (mod c 6))]
         [r g b])

       ;; 24-step grayscale
       :else
       (let [v (+ 8 (* 10 (- code 232)))]
         [v v v])))))

(defn hex->rgb
  "Converts a hex(a) color (string) to an [r g b] vector representation."
  [s]
  (-> s hex->rgb* (nth 0 nil)))

(defn hex->rgba
  "Converts a hex(a) color (string) to an [r g b a] vector representation."
  [s]
  (let [ret* (hex->rgb* s)
        rgb  (nth ret* 0 nil)
        a    (nth ret* 1 nil)]
    (conj rgb (if a a 1))))

(defn x256->nearest-user-color
  "Converts an 8-bit x256 color code (0-255) to a basic color string.
   Options map allows overriding :white-threshold and :dark-threshold."
  [code & [{:keys [white-threshold dark-threshold]
            :or {white-threshold 0.9
                 dark-threshold 0.1}}]]
  (let [[r g b] (nth x256-rgb-table code)
        r-norm (/ r 255.0)
        g-norm (/ g 255.0)
        b-norm (/ b 255.0)
        cmax (max r-norm g-norm b-norm)
        cmin (min r-norm g-norm b-norm)
        delta (- cmax cmin)
        l (/ (+ cmax cmin) 2.0)]

    (cond
      ;; 1. Check strict Lightness thresholds first
      (<= l dark-threshold) "black"
      (>= l white-threshold) "white"

      ;; 2. Handle pure grays falling inside the thresholds
      ;; Since "gray" isn't an allowed output, we push it to black or white based on the 50% mark.
      (zero? delta) (if (< l 0.5) "black" "white")

      ;; 3. Calculate Hue and map to the 6 primary/secondary buckets
      :else
      (let [h' (cond
                 (== cmax r-norm) (mod (/ (- g-norm b-norm) delta) 6.0)
                 (== cmax g-norm) (+ (/ (- b-norm r-norm) delta) 2.0)
                 :else            (+ (/ (- r-norm g-norm) delta) 4.0))
            h (* 60.0 h')]
        (cond
          (or (>= h 330) (< h 30)) "red"
          (< h 90) "yellow"
          (< h 150) "green"
          (< h 210) "cyan"
          (< h 270) "blue"
          :else "magenta")))))

(defn hex->8-bit
  "Converts hex color digits to an 8-bit color index (0-255)."
  [s]
  (->> s hex->rgb (apply rgb->8-bit)))

(defn hex->rgb-or-8-bit
  "Converts hex to an RGB vector (level 3) or 8-bit index (level 1-2)."
  [s level]
  (if (contains? #{1 2} level) (hex->8-bit s) (hex->rgb s)))

