(ns sugar.samples
  (:require [clojure.string :as str]
            [sugar.core :refer [styled sgr*]]
            [sugar.data :refer [base-colors] :as palette]
            #?(:cljs [js-runtime-detection.core])))

(defn- pln
  "Prints args, routing through print-styled-for-browser-console in browser cljs for dev-console formatting."
  [& args]
  (let [s (str/join " " args)]
    #?(:clj  (println s)
       :cljs (if js-runtime-detection.core/js-runtime-is-server
               (println s)
               (sugar.core/print-styled-for-browser-console s)))))

(defn grid 
  [font-style
   font-weight
   strikethrough 
   use-background-color?
   use-neutral-background-color?
   supports-color]
  (doseq [prefix ["" "extra-light-" "light-" "soft-" "medium-" "dark-" "extra-dark-"]
          :let   [prefix-display (if (= prefix "") "resolved-" prefix)]]
    (pln (str/replace prefix-display #"-$" "")
         (str/join (repeat (- 12 (count prefix-display)) " "))
         (str/join
          " "
          (for [[color bgc] (map vector
                                 base-colors
                                 (filter #(not (contains? #{"white" "black" "gray"} %)) base-colors))
                :when       (not (contains? #{"white" "black" "gray"} color))]
            (styled (if (or use-background-color?
                            use-neutral-background-color?)
                      (str " " bgc " ") 
                      color)
                    {:color            (str prefix color)
                     :strikethrough    strikethrough
                     :font-weight      font-weight
                     :font-style       font-style
                     :background-color (cond use-background-color?
                                             bgc
                                             use-neutral-background-color?
                                             "gray")}
                    supports-color))))))

(defn row-label [s x]
  (pln (sgr* :italic-gray
                 (str "\n\n" s ": " x "\n")
                 2)))


(defn text-decoration-combos [x supports-color]
  (doseq [x x
          font-weight     sugar.data/font-weight-enum
          font-style      sugar.data/font-style-enum
          :let            [s (str/join "."
                                       (remove nil?
                                               (mapv #(some-> % name)
                                                     [x
                                                      font-style
                                                      font-weight])))]]
    (sugar.core/print-styled s s supports-color)))

(defn text-decoration-basics [supports-color]
  ;; Sorted, because set order differs on nbb.
  (doseq [x (concat (sort sugar.data/underline-style-for-tokenized-string-enum)
                    ["strikethrough"])]
    (sugar.core/print-styled x x supports-color)))

(def ^:private warning-examples
  "One invalid value per kind of option, to show what each warning looks like."
  [["invalid color name"                {:color "not-a-color"}]
   ["rgb component out of range"        {:color [256 0 0]}]
   ["8-bit id out of range"             {:color 256}]
   ["invalid background-color"          {:background-color :not-a-color}]
   ["invalid font-weight"               {:font-weight :heavy}]
   ["invalid font-style"                {:font-style :oblique}]
   ["invalid underline-style"           {:underline true :underline-style :zigzag}]
   ["invalid boolean"                   {:strikethrough "yes"}]
   ["several invalid values in one map" {:color "not-a-color" :font-weight :heavy}]])

(defn warnings
  "Calls styled with each invalid value and validation on, which prints its
   warning."
  [supports-color]
  (doseq [[label m] warning-examples]
    (row-label "warning" (str label ", " (pr-str m)))
    (styled "x" (assoc m :validate-options true) supports-color)))

(defn palette
  "Prints the palette grids for font style, font weight and background color,
   then the text decorations."
  [supports-color]
  (doseq [font-style ["italic" "normal"]]
    (row-label "font-style" font-style)
    (grid font-style nil nil nil nil supports-color))
  (doseq [font-weight ["bold" "dim"]]
    (row-label "font-weight" font-weight)
    (grid nil font-weight nil nil nil supports-color))
  (row-label "using background-color" nil)
  (grid nil nil nil true nil supports-color)
  (row-label "using neutral background-color" nil)
  (grid nil nil nil nil true supports-color)
  (row-label "text-decoration-basics" nil)
  (text-decoration-basics supports-color))
