(ns sugar.snapshot-cases
  "Inputs for the snapshot tests. Each case is a map:

     :id   unique string, the key in sugar.snapshots
     :opts the style passed to styled: a map, or a dot-delimited string or
           keyword
     :env  env vars for the mocked supports-color context. FORCE_COLOR sets
           the level. CLITHEME sets the mood.

   Add a case, run `bb test:snapshot`, and review the diff of
   test/sugar/snapshots.cljc."
  (:require [clojure.string :as str]
            [sugar.browser-dev-console :as browser]
            [sugar.core :refer [styled]]
            [sugar.data :as data]
            [supports-color.core :as sc]))

(def text "hi")

(defn- env-label [{:strs [FORCE_COLOR CLITHEME]}]
  (str "L" FORCE_COLOR (when CLITHEME (str " " CLITHEME))))

(defn- test-case [label opts env]
  {:id   (str label " " (env-label env))
   :opts opts
   :env  env})

(defn- level [n] {"FORCE_COLOR" (str n)})

(def ^:private palette (sort data/all-colors))

(def ^:private hex-colors
  ["#FF0000" "#00ff00" "#BADA55" "ff8800" "336699"
   "#ABC" "fff" "#F0F"
   "#FF000080" "336699CC"
   "#F00F" "ABC8"
   "#000000" "#FFFFFF"
   ;; invalid
   "#GGGGGG" "#12345"])

(def ^:private rgb-colors
  [[255 0 0] [0 128 255] [50 205 50] [0 0 0] [255 255 255]
   ;; invalid
   [256 0 0] [-1 0 0] [1 2]])

(def ^:private eight-bit-colors
  [0 7 8 15 16 196 231 232 255
   ;; invalid
   256 -1])

(def ^:private option-maps
  [["font-weight bold"          {:font-weight :bold}]
   ["font-weight dim"           {:font-weight :dim}]
   ["font-weight normal"        {:font-weight :normal}]
   ["font-weight invalid"       {:font-weight :heavy}]
   ["font-style italic"         {:font-style :italic}]
   ["font-style normal"         {:font-style :normal}]
   ["font-style invalid"        {:font-style :oblique}]
   ["underline"                 {:underline true}]
   ["underline straight"        {:underline true :underline-style :straight}]
   ["underline double"          {:underline true :underline-style :double}]
   ["underline wavy"            {:underline true :underline-style :wavy}]
   ["underline dotted"          {:underline true :underline-style :dotted}]
   ["underline dashed"          {:underline true :underline-style :dashed}]
   ["underline style invalid"   {:underline true :underline-style :zigzag}]
   ["underline style only"      {:underline-style :wavy}]
   ["strikethrough"             {:strikethrough true}]
   ["blink"                     {:blink true}]
   ["reverse"                   {:reverse true}]
   ["hide"                      {:hide true}]
   ["boolean invalid"           {:reverse "yes"}]
   ["strikethrough invalid"     {:strikethrough "yes"}]
   ["blink invalid"             {:blink "yes"}]
   ["color keyword"             {:color :red}]
   ["color invalid name"        {:color "not-a-color"}]
   ["color invalid type"        {:color 1.5}]
   ["color and background"      {:color "red" :background-color "blue"}]
   ["all options"               {:color            "blue"
                                 :background-color "white"
                                 :font-weight      :bold
                                 :font-style       :italic
                                 :underline        true
                                 :underline-style  :wavy
                                 :strikethrough    true
                                 :blink            true
                                 :reverse          true
                                 :hide             true}]
   ["print-warnings false"      {:color "not-a-color" :print-warnings false}]
   ["print-warnings true"       {:color "not-a-color" :print-warnings true}]
   ["print-warnings invalid"    {:color "not-a-color" :print-warnings "no"}]
   ["print-warnings nil"        {:color "not-a-color" :print-warnings nil}]
   ["validate-options false"    {:color "not-a-color" :validate-options false}]
   ["validate-options yes"      {:color "not-a-color" :validate-options "yes"}]
   ["unknown key"               {:colour "red"}]
   ["nil opts"                  nil]
   ["empty opts"                {}]])

(def ^:private option-strings
  ["red"
   "bold.red"
   "dim.italic.cyan"
   "white.bg-blue"
   "bold.italic.green.bg-extra-dark-red"
   "strikethrough.orange"
   "reverse.purple"
   "hide.blue"
   "blink.red"
   "underline.cyan"
   "wavy-underline.red"
   "double-underline.green"
   "dotted-underline.blue"
   "dashed-underline.purple"
   "straight-underline.orange"
   "zigzag-underline.red"
   "bold.italic.red.bg-white.strikethrough.wavy-underline.reverse"
   "user-bright-blue"
   "user-red.bg-user-white"
   "extra-dark-purple.bold"
   "normal.red"
   "red.unknown-token"
   ""])

(def cases
  (vec
   (concat
    ;; Every palette color, as foreground and as background
    (for [n [1 2 3] c palette]
      (test-case (str "color " c) {:color c} (level n)))
    (for [n [1 3] c palette]
      (test-case (str "background " c) {:background-color c} (level n)))

    ;; Moods: base and subtle colors resolve to a palette variant
    (for [mood ["dark" "light"]
          c    (concat data/base-colors (sort data/subtle-colors) ["max" "neutral"])
          k    [:color :background-color]]
      (test-case (str (name k) " " c) {k c} {"FORCE_COLOR" "3" "CLITHEME" mood}))

    ;; Hex, rgb, and 8-bit colors
    (for [n [1 2 3] c hex-colors]
      (test-case (str "hex " c) {:color c} (level n)))
    (for [n [1 2 3] c rgb-colors]
      (test-case (str "rgb " c) {:color c} (level n)))
    (for [n [1 2 3] c eight-bit-colors]
      (test-case (str "8-bit " c) {:color c} (level n)))
    (for [n [1 3] c ["#BADA55" [0 128 255] 196]]
      (test-case (str "background " (pr-str c)) {:background-color c} (level n)))

    ;; Every option
    (for [n [1 3] [label m] option-maps]
      (test-case label m (level n)))

    ;; Validation is on by default. These cases turn it on explicitly, and
    ;; report each invalid value above the same way.
    (for [n [1 3]
          [label m] (concat
                     (for [c ["#GGGGGG" "#12345" [256 0 0] [-1 0 0] [1 2] 256 -1]]
                       [(str "color " (pr-str c)) {:color c}])
                     (filter #(or (str/includes? (first %) "invalid")
                                  (str/starts-with? (first %) "print-warnings"))
                             option-maps))]
      (test-case (str "validated " label) (assoc m :validate-options true) (level n)))

    ;; Dot-delimited string opts
    (for [s option-strings]
      (test-case (str "string " (pr-str s)) s (level 3)))

    ;; Dot-delimited keyword styles, parsed like strings
    (for [k [:red
             :bold.red
             :white.bg-blue
             :bold.italic.green.bg-extra-dark-red
             :wavy-underline.red
             :bold.bogus
             :foo/bold.red]]
      (test-case (str "keyword " (pr-str k)) k (level 3)))

    ;; Level 0 returns the text unstyled
    (for [[label m] [["level 0 color"    {:color "red"}]
                     ["level 0 all"      (second (first (filter #(= "all options" (first %)) option-maps)))]
                     ["level 0 string"   "bold.red"]
                     ["level 0 invalid"  {:color "not-a-color"}]]]
      (test-case label m (level 0))))))

(defn context
  "A supports-color :context that masks the real environment and terminal, so
   output depends only on the case. No tty means no terminal query."
  [env]
  {:env     (merge (zipmap sc/env-var-keys (repeat nil)) env)
   :runtime {:tty  {:stdout false :stderr false :stdin false}
             :argv []
             :os   {:name "Linux" :version "6.0" :platform :unix}}})

(defn- browser-array
  "The browser dev console conversion of s, as a vector: the format string,
   then one CSS string per %c. nil when s has no SGR."
  [s]
  (when (not= s text)
    (some-> (browser/ansi-sgr-string->browser-dev-console-array s) vec)))

(defn run
  "Runs a case. Returns {:result s}, plus :browser with the browser dev console
   conversion of the result, and :printed with anything styled printed
   (warnings). A caught exception prints a platform-specific error object, so
   :printed is then :caught-exception."
  [{:keys [opts env]}]
  (let [result  (atom nil)
        printed (with-out-str (reset! result (styled text opts (context env))))
        browser (browser-array @result)]
    (cond-> {:result @result}
      (some? browser)
      (assoc :browser browser)

      (str/includes? printed "Error(Caught)")
      (assoc :printed :caught-exception)

      (and (not (str/includes? printed "Error(Caught)"))
           (seq printed))
      (assoc :printed printed))))
