(ns sugar.bench
  "Benchmark scenarios for sugar.core/styled and the browser dev console
   converter, and a timed-loop runner for Babashka and Node.

   The color level comes from the process, so run this with FORCE_COLOR set
   (1 or 3) and CLITHEME=dark, which skips the terminal query. The bb tasks
   bench:bb, bench:node and bench:jvm do this at both levels.

   A -miss scenario empties the sgr cache before every call, so each call
   builds its SGR prefix from scratch. The emptying is part of the measured
   time."
  (:require [sugar.browser-dev-console :as browser]
            [sugar.core :as sugar]
            [supports-color.core :as sc]))


(defn- context
  "A supports-color :context that masks the real environment and terminal."
  [env]
  {:env     (merge (zipmap sc/env-var-keys (repeat nil)) env)
   :runtime {:tty  {:stdout false :stderr false :stdin false}
             :argv []
             :os   {:name "Linux" :version "6.0" :platform :unix}}})


(def ^:private level-3-dark
  (context {"FORCE_COLOR" "3" "CLITHEME" "dark"}))


;; Looked up once. In cljs, #' builds a new Var object on every evaluation.
(def ^:private sgr-cache @#'sugar.core/sgr-cache)


(defn- clear-cache! []
  (reset! sgr-cache {}))


(def ^:private text "hello")


(def ^:private map-opts
  {:color       "red"
   :font-weight "bold"})


(def ^:private long-map-opts
  "The map form of \"bold.italic.red.bg-blue.wavy-underline\"."
  {:color            "red"
   :background-color "blue"
   :font-weight      "bold"
   :font-style       "italic"
   :underline        true
   :underline-style  "wavy"})


(def ^:private validate-opts
  {:color            "red"
   :font-weight      "bold"
   :validate-options true})


(def ^:private span-opts
  "Styles for the spans of the long browser string. Covers palette colors,
   8-bit, rgb, background colors and text decorations."
  [{:color "red" :font-weight "bold"}
   {:color "blue" :background-color "yellow"}
   {:color 214 :font-style "italic"}
   {:color [12 200 99] :underline true}
   {:color "#3a7bd5" :underline-style "wavy"}
   {:color "green" :strikethrough true}
   {:background-color [200 30 30] :reverse true}
   {:color "magenta" :font-weight "dim"}])


(def ^:private long-sgr-string
  "64 styled spans separated by plain text, rendered at level 3."
  (apply str
         (for [i (range 64)]
           (str (sugar/styled (str "span-" i) (nth span-opts (mod i 8)) level-3-dark)
                " plain "))))


(def ^:private short-sgr-string
  (sugar/styled text map-opts level-3-dark))


(def scenarios
  "Ordered [name f] pairs. Each f is one operation."
  [["map opts, cache hit"
    #(sugar/styled text map-opts)]

   ["6-key map opts, cache hit"
    #(sugar/styled text long-map-opts)]

   ["string opts, cache hit"
    #(sugar/styled text "bold.red")]

   ["5-token string opts, cache hit"
    #(sugar/styled text "bold.italic.red.bg-blue.wavy-underline")]

   [":validate-options true, cache hit"
    #(sugar/styled text validate-opts)]

   ["map opts, miss"
    #(do (clear-cache!) (sugar/styled text map-opts))]

   ["6-key map opts, miss"
    #(do (clear-cache!) (sugar/styled text long-map-opts))]

   ["string opts, miss"
    #(do (clear-cache!) (sugar/styled text "bold.red"))]

   ["rgb fg and bg, miss"
    #(do (clear-cache!) (sugar/styled text {:color [12 200 99] :background-color [200 30 30]}))]

   ["hex fg, miss"
    #(do (clear-cache!) (sugar/styled text {:color "#3a7bd5"}))]

   ["8-bit fg, miss"
    #(do (clear-cache!) (sugar/styled text {:color 214}))]

   [":validate-options true, miss"
    #(do (clear-cache!) (sugar/styled text validate-opts))]

   ["empty cache, no-op"
    #(clear-cache!)]

   ["mocked context (3rd arg)"
    #(sugar/styled text map-opts level-3-dark)]

   ["browser convert, 1 span"
    #(browser/ansi-sgr-string->browser-dev-console-array short-sgr-string)]

   ["browser convert, 64 spans"
    #(browser/ansi-sgr-string->browser-dev-console-array long-sgr-string)]])


;; Timed loop ------------------------------------------------------------------

(defn- now-ns []
  #?(:cljs (* 1e6 (js/performance.now))
     :clj  (System/nanoTime)))


(defn- run-for
  "Calls f in a loop for about budget-ns. Returns the mean ns per call."
  [f budget-ns]
  (let [start (now-ns)]
    (loop [n 0]
      (let [elapsed (- (now-ns) start)]
        (if (and (>= elapsed budget-ns) (pos? n))
          (/ elapsed n)
          (do (dotimes [_ 64] (f))
              (recur (+ n 64))))))))


(defn- median [xs]
  (nth (sort xs) (quot (count xs) 2)))


(defn measure
  "Warms f up, then runs 7 batches of about 200 ms each. Returns the median and
   the minimum of the per-batch mean ns per call."
  [f]
  (run-for f 500e6)
  (let [batches (vec (repeatedly 7 #(run-for f 200e6)))]
    {:median-ns (median batches)
     :min-ns    (apply min batches)}))


(defn- fmt-ns [ns]
  (let [ns (double ns)]
    (cond
      (>= ns 1e6) (str (/ (Math/round (/ ns 1e4)) 100.0) " ms")
      (>= ns 1e3) (str (/ (Math/round (/ ns 10)) 100.0) " us")
      :else       (str (Math/round ns) " ns"))))


(defn- pad [s n]
  (let [s (str s)]
    (apply str s (repeat (- n (count s)) " "))))


(defn level
  "The color level sugar resolved for this process."
  []
  (:level @#'sugar.core/profile-ctx))


(defn header [runtime]
  (println (str "\n" runtime ", level " (level) ", median and min of 7 batches of 200 ms"))
  (println (str (pad "scenario" 36) (pad "median" 12) "min")))


(defn row [scenario-name median min]
  (println (str (pad scenario-name 36) (pad (fmt-ns median) 12) (fmt-ns min))))


(defn run-all
  "Measures every scenario with the timed loop and prints a table."
  [runtime]
  (header runtime)
  (doseq [[scenario-name f] scenarios]
    (let [{:keys [median-ns min-ns]} (measure f)]
      (row scenario-name median-ns min-ns))))


(defn -main [& _]
  (run-all #?(:clj  (if (System/getProperty "babashka.version")
                      (str "Babashka " (System/getProperty "babashka.version"))
                      (str "JVM " (System/getProperty "java.version") ", timed loop"))
              :cljs "")))


#?(:cljs
   (defn main [& _]
     (run-all (str "Node " js/process.version))))
