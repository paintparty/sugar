(ns sugar.regression-test
  "Exact-output tests for fixed bugs. Each test is named after the bug it
   covers."
  (:require #?(:clj  [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [clojure.string :as str]
            [sugar.browser-dev-console :as browser]
            [sugar.core :as core]
            [sugar.utils :as utils]
            [sugar.snapshot-cases :as cases]))

(defn- styled-at
  "Output of styled for opts at color level n, with a mocked context."
  [n opts]
  (:result (cases/run {:opts opts :env {"FORCE_COLOR" (str n)}})))

(defn- printed-at
  "What styled printed for opts at color level n."
  [n opts]
  (:printed (cases/run {:opts opts :env {"FORCE_COLOR" (str n)}})))

(defn- validated
  "opts with validation on explicitly. It is on by default too."
  [opts]
  (assoc opts :validate-options true))

(deftest rgb-and-hex-are-truecolor-at-level-3
  (is (= "\u001b[38;2;186;218;85mhi\u001b[m" (styled-at 3 {:color "#BADA55"})))
  (is (= "\u001b[38;2;0;128;255mhi\u001b[m" (styled-at 3 {:color [0 128 255]})))
  (is (= "\u001b[48;2;186;218;85mhi\u001b[m" (styled-at 3 {:background-color "#BADA55"})))
  (testing "level 2 still downsamples to 8-bit"
    (is (= "\u001b[38;5;149mhi\u001b[m" (styled-at 2 {:color "#BADA55"})))))

(deftest palette-colors-use-16-color-codes-at-level-1
  (is (= "\u001b[91mhi\u001b[m" (styled-at 1 {:color "red"})))
  (is (= "\u001b[101mhi\u001b[m" (styled-at 1 {:background-color "red"})))
  (is (= "\u001b[30mhi\u001b[m" (styled-at 1 {:color "shadow-red"})))
  (is (= "\u001b[97mhi\u001b[m" (styled-at 1 {:color "highlight-red"}))))

(deftest black-is-not-gray-at-level-1
  (is (= "\u001b[30mhi\u001b[m" (styled-at 1 {:color 0})))
  (is (= "\u001b[30mhi\u001b[m" (styled-at 1 {:color "#000000"})))
  (is (= "\u001b[30mhi\u001b[m" (styled-at 1 {:color "black"}))))

(deftest user-colors-work-as-backgrounds
  (is (= "\u001b[41mhi\u001b[m" (styled-at 3 {:background-color "user-red"})))
  (is (= "\u001b[107mhi\u001b[m" (styled-at 3 {:background-color "user-bright-white"})))
  (is (= "\u001b[31;47mhi\u001b[m" (styled-at 3 "user-red.bg-user-white"))))

(deftest eight-bit-ids-are-0-to-255
  (is (= "\u001b[38;5;255mhi\u001b[m" (styled-at 3 {:color 255})))
  (testing "256 is rejected with a warning, and does not throw at level 1"
    (is (= "hi" (styled-at 3 {:color 256})))
    (is (string? (printed-at 3 (validated {:color 256}))))
    (is (= "hi" (styled-at 1 {:color 256})))))

(deftest rgb-vectors-have-3-components-in-range
  (is (= "hi" (styled-at 3 {:color [256 0 0]})))
  (is (= "hi" (styled-at 3 {:color [1 2]})))
  (is (= "hi" (styled-at 3 {:color [1 2 3 4]}))))

(deftest no-styles-returns-the-text-unchanged
  (is (= "hi" (styled-at 3 {})))
  (is (= "hi" (styled-at 3 nil)))
  (is (= "hi" (styled-at 3 "")))
  (is (= "hi" (styled-at 3 {:color "neutral"})))
  (is (= "hi" (styled-at 3 {:color "not-a-color"}))))

(deftest warning-tags-have-the-right-colors
  (is (= "\u001b[38;5;40mx\u001b[0;m" (core/sgr* :green "x" 3)))
  (is (= "\u001b[1;38;5;196mx\u001b[0;m" (core/sgr* :bold-red "x" 3))))

(deftest strikethrough-and-blink-warnings-say-what-is-valid
  (doseq [k [:strikethrough :blink]]
    (testing k
      (is (str/includes? (printed-at 3 (validated {k "yes"}))
                         "A boolean value - `true` or `false`")))))

(deftest validation-is-on-by-default
  (is (= "hi" (styled-at 3 {:color "not-a-color"})))
  (is (str/includes? (printed-at 3 {:color "not-a-color"}) ":color"))
  (testing ":validate-options false turns warnings off"
    (is (= "hi" (styled-at 3 {:color "not-a-color" :validate-options false})))
    (is (nil? (printed-at 3 {:color "not-a-color" :validate-options false}))))
  (testing "a non-boolean value is reported and turns validation off"
    (let [printed (printed-at 3 {:color "not-a-color" :validate-options "yes"})]
      (is (str/includes? printed ":validate-options"))
      (is (not (str/includes? printed ":color"))))))

(deftest print-warnings-false-suppresses-warnings
  (is (= "hi" (styled-at 3 (validated {:color "not-a-color" :print-warnings false}))))
  (is (nil? (printed-at 3 (validated {:color "not-a-color" :print-warnings false}))))
  (is (string? (printed-at 3 (validated {:color "not-a-color"}))))
  (testing "only true keeps warnings on. nil is off"
    (is (nil? (printed-at 3 (validated {:color "not-a-color" :print-warnings nil})))))
  (testing "a non-boolean value is reported and turns other warnings off"
    (let [printed (printed-at 3 (validated {:color "not-a-color" :print-warnings "no"}))]
      (is (str/includes? printed ":print-warnings"))
      (is (not (str/includes? printed ":color"))))))

(deftest unknown-style-string-tokens-always-warn
  (let [printed (printed-at 3 "zigzag-underline.red.bogus")]
    (is (= 2 (count (re-seq #"WARNING" printed))))
    (is (str/includes? printed "\"zigzag-underline\""))
    (is (str/includes? printed "\"bogus\"")))
  (is (= "\u001b[38;2;255;0;0mhi\u001b[m" (styled-at 3 "zigzag-underline.red.bogus")))
  (testing "empty tokens are skipped"
    (is (nil? (printed-at 3 "red..bold")))))

(deftest underline-style-warning-lists-underline-styles
  (let [printed (printed-at 3 (validated {:underline true :underline-style :zigzag}))]
    (is (str/includes? printed "wavy"))
    (is (not (str/includes? printed "rgb vector")))))

(deftest hex-alpha-is-0-to-1-on-every-platform
  (is (= [255 0 0 0.5] (utils/hex->rgba "FF000080")))
  (is (= [255 0 0 1.0] (utils/hex->rgba "FF0000FF")))
  (is (= [255 0 0 1] (utils/hex->rgba "FF0000"))))

(deftest rgb->8-bit-clamps-components-to-0-255
  (is (= 46 (utils/rgb->8-bit 0 300 0)))
  (is (= (utils/rgb->8-bit 0 255 0) (utils/rgb->8-bit 0 300 0)))
  (is (= 198 (utils/rgb->8-bit 1000 -1000 128))))

(deftest hex-conversions-accept-a-leading-hash
  (is (= [186 218 85] (utils/hex->rgb "#BADA55")))
  (is (= [170 187 204] (utils/hex->rgb "#ABC")))
  (is (= [255 0 0 0.5] (utils/hex->rgba "#FF000080")))
  (is (= 149 (utils/hex->8-bit "#BADA55"))))

(deftest sgr-count-is-0-without-tags
  (is (= 0 (browser/sgr-count "hi")))
  (is (= 0 (browser/sgr-count "")))
  (is (= 0 (browser/sgr-count nil))))

;; Jolt has no java.util.Locale, so this JVM locale check runs on the JVM and bb only.
#?(:jolt nil
   :clj
   (deftest hex-alpha-does-not-depend-on-locale
     (let [default (java.util.Locale/getDefault)]
       (try
         (java.util.Locale/setDefault java.util.Locale/GERMANY)
         (is (= [255 0 0 0.5] (utils/hex->rgba "FF000080")))
         (is (= "\u001b[38;2;255;0;0mhi\u001b[m" (styled-at 3 {:color "#FF000080"})))
         (finally
           (java.util.Locale/setDefault default))))))

(defn- css
  "The CSS string for the first %c of the browser conversion of s."
  [s]
  (second (vec (browser/ansi-sgr-string->browser-dev-console-array s))))

(deftest browser-console-converts-16-color-codes
  (is (= "color: #800000; line-height: 1.5" (css "\u001b[31mhi\u001b[m")))
  (is (= "color: #ff0000; line-height: 1.5" (css "\u001b[91mhi\u001b[m")))
  (is (= "background-color: #c0c0c0; line-height: 1.5" (css "\u001b[47mhi\u001b[m")))
  (is (= "background-color: #ffffff; line-height: 1.5" (css "\u001b[107mhi\u001b[m"))))

(deftest browser-console-converts-dim-reverse-hide
  (is (= "font-weight: lighter; line-height: 1.5" (css "\u001b[2mhi\u001b[m")))
  (is (= "color: transparent; line-height: 1.5" (css "\u001b[8mhi\u001b[m")))
  (testing "reverse swaps colors, with the console's own colors as fallbacks"
    (is (= "background-color: CanvasText; color: Canvas; line-height: 1.5"
           (css "\u001b[7mhi\u001b[m")))
    (is (= "background-color: #800000; color: #c0c0c0; line-height: 1.5"
           (css "\u001b[7;31;47mhi\u001b[m")))))

(deftest browser-console-combines-underline-and-strikethrough
  (is (= "line-height: 1.5; text-decoration: underline line-through; text-decoration-style: straight"
         (css "\u001b[4;9mhi\u001b[m"))))

(deftest browser-console-escapes-percent-signs
  (is (= ["%c100%%c%%s%c" "font-weight: bold; line-height: 1.5" "line-height: 1.5"]
         (vec (browser/ansi-sgr-string->browser-dev-console-array "\u001b[1m100%c%s\u001b[m"))))
  (is (= ["50%%"] (vec (browser/ansi-sgr-string->browser-dev-console-array "50%")))))

(defn- elapsed-ms [f]
  (let [t #?(:clj (System/nanoTime) :cljs (js/Date.now))]
    (f)
    #?(:clj  (/ (- (System/nanoTime) t) 1e6)
       :cljs (- (js/Date.now) t))))

(deftest sgr-regexes-fail-fast-on-an-unterminated-tag
  ;; The old pattern let 9 and 4 match two branches. On Node it took 1.1 s for
  ;; 26 4s, and 4 times longer for every 2 more.
  (let [s (str "\u001b[" (apply str (repeat 28 "4")) "x")]
    (is (< (elapsed-ms #(browser/sgr-count s)) 50))
    (is (< (elapsed-ms #(browser/ansi-sgr-string->browser-dev-console-array s)) 50))
    (is (< (elapsed-ms #(with-out-str (core/?sgr s))) 50)))
  (testing "underline styles still match inside a tag"
    (is (= 8 (browser/sgr-count "\u001b[4:3;1m")))))

(deftest mood-follows-clitheme-on-every-platform
  (is (= "\u001b[38;2;209;0;31mhi\u001b[m"
         (:result (cases/run {:opts {:color "red"}
                              :env  {"FORCE_COLOR" "3" "CLITHEME" "light"}})))))
