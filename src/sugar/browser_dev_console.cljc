(ns sugar.browser-dev-console
  "Converts ANSI SGR-styled strings to browser dev console format: a string with
   a %c format specifier in place of each SGR sequence, followed by one CSS
   string per %c. Used by sugar.core/print-styled in browsers."
  (:require [clojure.string :as string]
            [sugar.utils :as utils]))

(defn- hex-byte [n]
  (let [h #?(:clj (Integer/toHexString n) :cljs (.toString n 16))]
    (if (< n 16) (str "0" h) h)))

(defn- x256->css
  "The CSS hex color of an 8-bit color id, from sugar.utils/x256-rgb-table."
  [id]
  (let [[r g b] (nth utils/x256-rgb-table id)]
    (str "#" (hex-byte r) (hex-byte g) (hex-byte b))))

(def esc "\u001b\\[")

(def sgr-color-base
  ";(?:(5);([0-9]{1,3})|(2);([0-9]{1,3});([0-9]{1,3});([0-9]{1,3});?)")

(def sgr-fgc-re
  (re-pattern (str "38" sgr-color-base)))

(def sgr-bgc-re
  (re-pattern (str "48" sgr-color-base)))

(def sgr-x256-or-rgb-foreground-color-re
  "38;(?:5;[0-9]{1,3}|2;[0-9]{1,3};[0-9]{1,3};[0-9]{1,3});?m")

(def sgr-reset
  "(?:0;?m|m)")

(def sgr-font-style
  "3;?m")

(def sgr-font-weight
  "[01];?m")

(def sgr-text-decoration-base
  "(?:9|4(?::[1-5])?)")

(def sgr-text-decoration
  (str sgr-text-decoration-base ";?m"))

;; Each character has only one way to match, so a long run of digits with no
;; closing m fails in linear time. An overlapping form such as
;; (?:[0-9]|;|9|4(?::[1-5])?)* matches the same strings but backtracks
;; exponentially in JavaScript.
(def sgr-freeform
  "(?:[0-9;]|4:[1-5])*m")

(def sgr-re (re-pattern (str esc sgr-freeform)))

(def sgr-unstyled-spaces-re (re-pattern (str esc "m +" esc "0m")))

(defn sgr-count
  "Given a string containing ANSI SGR tags, returns the character count of the
   ANSI SGR tags. 0 when there are none, or s is nil."
  [s]
  (or (some->> s
               (re-seq sgr-re)
               (reduce (fn [n s] (+ n (count s))) 0))
      0))

(defn adjusted-char-count
  "Given a string containing ANSI SGR tags, returns the character count of the
   string minus the char count of all the ANSI SGR tags."
  [s]
  (- (count s) (or (when-not (= s "\n") (sgr-count s)) 0)))

;; -----------------------------------------------------------------------------

(defn- capture-group
  "Wraps s in a regex capture group."
  [s]
  (str "(" s ")"))

(def ^:private sgr-classify-re
  "Pre-compiled regex for classifying SGR escape sequences by type."
  (re-pattern
   (str esc
        "(?:"
        (capture-group sgr-reset)
        "|"
        (capture-group sgr-x256-or-rgb-foreground-color-re)
        "|"
        (capture-group sgr-font-style)
        "|"
        (capture-group sgr-font-weight)
        "|"
        (capture-group sgr-text-decoration)
        "|"
        (capture-group sgr-freeform)
        ")")))

(defn- find-sgr-pattern2
  "Classifies an SGR escape in s by type: reset, color, font-style, font-weight,
   text-decoration, or freeform."
  [s]
  (let [[_
         reset
         just-color
         just-italic
         just-weight
         just-decoration
         freeform]
        (re-find sgr-classify-re s)]
    (or (some->> reset (vector "reset"))
        (some->> just-color (vector "color"))
        (some->> just-italic (vector "font-style"))
        (some->> just-weight (vector "font-weight"))
        (some->> just-decoration (vector "text-decoration"))
        (some->> freeform (vector "freeform")))))


(defn- sgr-color->map
  "Extracts a foreground (:fgc) or background (:bgc) color from an SGR string as
   a CSS-value map."
  [s k]
  (let [fgc? (= k :fgc)
        [_ x256? x256 _ r g b :as color]
        (re-find (if fgc? sgr-fgc-re sgr-bgc-re) s)]
    (when color
      (hash-map
       (if fgc? "color" "background-color")
       (if x256?
         (x256->css #?(:cljs (js/parseInt x256)
                       :clj  (Integer/parseInt x256)))
         (str "rgb(" r ", " g ", " b ")"))))))


(def sgr-style-by-id
  {"1"   ["font-weight" "bold"]
   "2"   ["font-weight" "lighter"]
   "0"   ["font-weight" "normal"]
   "3"   ["font-style" "italic"]
   "9"   ["text-decoration" "line-through"]
   "4" ["text-decoration" "underline" "text-decoration-style" "straight"]
   "4:1" ["text-decoration" "underline" "text-decoration-style" "straight"]
   "4:2" ["text-decoration" "underline" "text-decoration-style" "double"]
   "4:3" ["text-decoration" "underline" "text-decoration-style" "wavy"]
   "4:4" ["text-decoration" "underline" "text-decoration-style" "dotted"]
   "4:5" ["text-decoration" "underline" "text-decoration-style" "dashed"]})


(def ^:private user-color-styles-by-id
  "CSS for the 16 user colors: 30-37 and 90-97 foreground, 40-47 and 100-107
   background. Uses the same 16 colors as 8-bit ids 0-15."
  (into {}
        (for [[base prop]     [[30 "color"] [40 "background-color"]]
              [offset index0] [[0 0] [60 8]]
              i               (range 8)]
          [(str (+ base offset i)) [prop (x256->css (+ index0 i))]])))


(defn- add-style
  "Adds a CSS property to acc. text-decoration values combine, so underline and
   line-through can both apply."
  [acc [k v]]
  (if (and (= k "text-decoration") (get acc k))
    (assoc acc k (str (get acc k) " " v))
    (assoc acc k v)))


(defn- sgr-text-styling-coll
  "Strips the trailing 'm' and splits an SGR parameter string into individual
  code tokens."
  [s]
  (-> s
      (string/replace #"m$" "")
      (string/split #";")))


(defn- sgr-text-styling
  "Removes color sub-sequences from s and returns the remaining SGR code tokens."
  [s fgc bgc]
  (let [s (if fgc (string/replace s sgr-fgc-re "") s)
        s (if bgc (string/replace s sgr-bgc-re "") s)]
    (sgr-text-styling-coll s)))


(defn- text-style-map
  "Builds a complete CSS property map from SGR codes and extracted color maps.
   Reverse (7) swaps color and background, falling back to the console's own
   colors (CanvasText, Canvas). Hide (8) makes the text transparent. Blink (5)
   has no CSS equivalent in dev consoles, so it is dropped."
  [s fgc bgc]
  (let [ids (sgr-text-styling s fgc bgc)
        m   (-> (reduce (fn [acc id]
                          (reduce add-style
                                  acc
                                  (partition 2 (or (get sgr-style-by-id id)
                                                   (get user-color-styles-by-id id)))))
                        {}
                        ids)
                (merge fgc bgc))
        m   (if (some #{"7"} ids)
              (assoc m
                     "color"            (get m "background-color" "Canvas")
                     "background-color" (get m "color" "CanvasText"))
              m)]
    (if (some #{"8"} ids)
      (assoc m "color" "transparent")
      m)))


(defn- ansi-sgr->style-map
  "Converts a single ANSI SGR escape sequence into a CSS property map."
  [s]
  (let [[tag v] (find-sgr-pattern2 s)]
    (assoc (cond (= "freeform" tag)
                 (let [fgc       (sgr-color->map v :fgc)
                       bgc       (sgr-color->map v :bgc)
                       style-map (text-style-map v fgc bgc)]
                   style-map)

                 (= "color" tag)
                 (sgr-color->map v :fgc)

                 (contains? #{"font-style" "text-decoration" "font-weight"} tag)
                 (text-style-map v nil nil)

                 :else
                 {})
           "line-height"
           ;; Make this dynamic
           1.5)))


(defn- style-map->css-style-str
  "Serializes a CSS property map into a semicolon-delimited inline style string.
   Properties are sorted, so the string is the same on every platform."
  [m]
  (string/join "; " (mapv (fn [[k v]] (str k ": " v)) (sort-by key m))))


(defn ansi-sgr-string->browser-dev-console-array
  "Intended to convert an ANSI SGR-tagged string to a format specifier-tagged
  string, with a corresponding vector of css style strings. The resulting string
  and styles can be supplied to a browser development console to format
  messages. See https://developer.chrome.com/docs/devtools/console/format-style
  for more background.
  
  Replaces all ANSI SGR tags in supplied string with CSS format specifier tag,
  \"%c\", after escaping every % in the text as %%. Analyzes all ANSI SGR
  tags, and produces a vector of corresponding css style strings. Returns a vector of the format-specifier-tagged strings,
  followed by the contents of the styles array. In ClojureScript, returns an
   array.
   
  Example usage in ClojureScript:

  ```Clojure
  (def console-array
    (->> :foo
         bling.hifi/hifi
         ansi-sgr-string->browser-dev-console-array))
  ;; =>
  ;; [\"%c:foo%c\",
  ;;  \"color: rgb(122, 62, 157)\",
  ;;  \"line-height: 1.4; color: default\"]

  ;; Print the value, with formatting, to dev console:
  (.apply js/console.log js/console console-array)
  ```
  "
  [s]
  (when (and (string? s) (not (string/blank? s)))
    (let [          ;; This removes redundant unstyled spaces
          ;; TODO - test perf with and without
          ;; s             
          ;; (string/replace s ansi-sgr-unstyled-spaces-re "")

          ;; Escapes % as %%, so text such as "100%c" can't consume a style
          ;; arg. Chrome, Firefox, Safari and Node print %% as %. Then
          ;; replaces all ANSI SGR tags with CSS format specifier tag.
          with-format-specifiers
          (-> s
              (string/replace "%" "%%")
              (string/replace sgr-re "%c"))

          ;; This analyzes all ansi-sgr escape sequences and produces vector of
          ;; css style strings
          vc
          (->> s
               (re-seq sgr-re)
               (reduce (fn [vc s]
                         (->> s
                              ansi-sgr->style-map
                              style-map->css-style-str
                              (conj vc)))
                       [with-format-specifiers]))]

      #?(:cljs (into-array vc) :clj vc))))
