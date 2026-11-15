(ns sugar.behavior-test
  "Hand-written tests for behavior that a snapshot of styled output can't
   express: the process-context path and its cache, the catch-all, printing,
   ?sgr, sgr* and the browser character counts. Exact output of styled itself
   lives in sugar.snapshots."
  (:require #?(:clj  [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [clojure.string :as str]
            [sugar.browser-dev-console :as browser]
            [sugar.core :as core]
            [sugar.snapshot-cases :as cases]))

(def ^:private level-3 (cases/context {"FORCE_COLOR" "3" "CLITHEME" "dark"}))

(defn- captured
  "Calls f, returns [return-value printed-text]."
  [f]
  (let [ret (atom nil)
        out (with-out-str (reset! ret (f)))]
    [@ret out]))

;; styled with no context argument
;; -----------------------------------------------------------------------------

#?(:clj
   (deftest styled-without-a-context-uses-the-process-context
     (let [full  (#'core/resolve-ctx level-3)
           cache (atom {})
           opts  {:color "red" :font-weight "bold"}]
       (with-redefs [core/profile-ctx full
                     core/process-ctx (delay full)
                     core/sgr-cache   cache]
         (testing "same output as the mocked context"
           (is (= "\u001b[1;38;2;255;59;85mhi\u001b[m" (core/styled "hi" opts)))
           (is (= (core/styled "hi" opts level-3) (core/styled "hi" opts))))
         (testing "styles are cached as passed, so a string or keyword is not parsed again"
           (core/styled "hi" "bold.red")
           (core/styled "hi" "bold.red")
           (is (= "\u001b[1;38;2;255;59;85mhi\u001b[m" (core/styled "hi" :bold.red)))
           (core/styled "hi" :bold.red)
           (is (= {opts       "\u001b[1;38;2;255;59;85"
                   "bold.red" "\u001b[1;38;2;255;59;85"
                   :bold.red  "\u001b[1;38;2;255;59;85"}
                  @cache)))
         (testing "nil and empty opts return the text"
           (is (= "hi" (core/styled "hi" nil)))
           (is (= "hi" (core/styled "hi" {})))))
       (testing "the cache is emptied when it is full"
         (let [cache (atom {})]
           (with-redefs [core/profile-ctx     full
                         core/process-ctx     (delay full)
                         core/sgr-cache       cache
                         core/sgr-cache-limit 3]
             (doseq [r [1 2 3]] (core/styled "hi" {:color [r 0 0]}))
             (is (= 3 (count @cache)))
             (is (= "\u001b[38;2;4;0;0mhi\u001b[m" (core/styled "hi" {:color [4 0 0]})))
             (is (= {{:color [4 0 0]} "\u001b[38;2;4;0;0"} @cache)))))
       (testing "level 0 returns the text"
         (with-redefs [core/profile-ctx (assoc full :level 0)]
           (is (= "hi" (core/styled "hi" opts))))))))

(deftest keyword-styles-match-string-styles
  (is (= (core/styled "hi" "bold.red" level-3)
         (core/styled "hi" :bold.red level-3)))
  (is (= (core/styled "hi" "white.bg-blue" level-3)
         (core/styled "hi" :white.bg-blue level-3))))

(deftest string-opts-match-map-opts
  (is (= (core/styled "hi" {:color "red" :font-weight "bold"} level-3)
         (core/styled "hi" "bold.red" level-3)))
  (is (= (core/styled "hi" {:color "white" :background-color "blue"} level-3)
         (core/styled "hi" "white.bg-blue" level-3))))

;; Catch-all
;; -----------------------------------------------------------------------------

(deftest styled-returns-the-text-when-opts-are-not-a-map
  (let [[ret printed] (captured #(core/styled "hi" 42 level-3))]
    (is (= "hi" ret))
    (is (str/includes? printed "Error(Caught)"))))

(deftest styled-returns-the-text-when-opts-are-an-infinite-seq
  (let [[ret printed] (captured #(core/styled "hi" (range) level-3))]
    (is (= "hi" ret))
    (is (str/includes? printed "Error(Caught)"))))

;; Supplied values in warnings
;; -----------------------------------------------------------------------------

(def ^:private supplied-value @#'core/supplied-value)

(deftest supplied-values-show-control-characters-as-escapes
  (is (= "\"\\u001b[2J\"" (supplied-value "\u001b[2J" 0)))
  (is (= "\"a\\u009bb\\u000ac\"" (supplied-value "a\u009bb\nc" 0)))
  (is (not (str/includes? (second (captured #(core/styled "hi" {:color "\u001b[2J"} level-3)))
                          "\u001b[2J"))))

(deftest supplied-values-are-bounded
  (is (= "(0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 ...)"
         (supplied-value (range) 0)))
  (is (= "[[[[#]]]]" (supplied-value [[[[[1]]]]] 0)))
  (let [s (supplied-value (apply str (repeat 300 "x")) 0)]
    (is (= 203 (count s)))
    (is (str/ends-with? s "xxxxx..."))))

;; print-styled
;; -----------------------------------------------------------------------------

#?(:cljs
   (deftest browser-console-prints-non-strings-as-text
     (let [calls (atom [])
           orig  (.-log js/console)]
       (set! (.-log js/console) (fn [& args] (swap! calls conj (vec args))))
       (try
         (doseq [x [42 :foo true nil 1.5 "hi"]]
           (core/print-styled-for-browser-console x))
         (finally
           (set! (.-log js/console) orig)))
       (is (= [["42"] [":foo"] ["true"] ["nil"] ["1.5"] ["hi"]] @calls)))))

(deftest print-styled-prints-the-styled-text-and-returns-nil
  (let [[ret printed] (captured #(core/print-styled "hi" {:color "red"} level-3))]
    (is (nil? ret))
    (is (= (str (core/styled "hi" {:color "red"} level-3) "\n") printed))
    (is (not= "hi\n" printed)))
  (is (= "hi\n" (second (captured #(core/print-styled "hi" nil level-3)))))
  (testing "2-arity uses the process context, so nil opts print the bare text"
    (is (= "hi\n" (second (captured #(core/print-styled "hi" nil)))))))

;; ?sgr
;; -----------------------------------------------------------------------------

(deftest ?sgr-returns-its-argument
  (let [s "\u001b[1;31mhi\u001b[m"]
    (is (= s (first (captured #(core/?sgr s)))))))

(deftest ?sgr-prints-tags-as-readable-text
  (let [[_ printed] (captured #(core/?sgr "\u001b[1;31mhi\u001b[m"))]
    (is (str/includes? printed "\\033[1;31m"))
    (is (str/includes? printed "\\033[m")))
  (is (= "plain\n" (second (captured #(core/?sgr "plain"))))))

;; sgr
;; -----------------------------------------------------------------------------

(deftest sgr-returns-the-opening-sequence
  (is (= "\u001b[1;38;2;255;59;85m" (core/sgr {:color "red" :font-weight "bold"} level-3)))
  (is (= "\u001b[1;38;2;255;59;85m" (core/sgr :bold.red level-3))))

(deftest sgr-returns-an-empty-string-when-there-are-no-codes
  (is (= "" (core/sgr :bold.red (cases/context {"FORCE_COLOR" "0"}))))
  (is (= "" (core/sgr {} level-3)))
  (is (= "" (core/sgr nil level-3))))

(deftest sgr-returns-an-empty-string-when-the-style-is-invalid
  (let [[ret printed] (captured #(core/sgr [1 2] level-3))]
    (is (= "" ret))
    (is (str/includes? printed "Error(Caught)"))))

(deftest styled-is-sgr-plus-text-plus-reset
  (doseq [{:keys [id opts env]} cases/cases
          :let [ctx     (cases/context env)
                [o _]   (captured #(core/sgr opts ctx))
                [s _]   (captured #(core/styled cases/text opts ctx))]]
    (testing id
      (is (= s (if (= "" o) cases/text (str o cases/text "\u001b[m")))))))

;; sgr*
;; -----------------------------------------------------------------------------

(deftest sgr*-wraps-only-above-level-0
  (is (= "x" (core/sgr* :green "x" 0)))
  (is (not= "x" (core/sgr* :green "x" 1))))

;; Browser character counts
;; -----------------------------------------------------------------------------

(deftest browser-counts-exclude-sgr-tags
  (let [s "\u001b[1mhi\u001b[m"]
    (is (= 7 (browser/sgr-count s)))
    (is (= 2 (browser/adjusted-char-count s))))
  (is (= 2 (browser/adjusted-char-count "hi")))
  (is (= 1 (browser/adjusted-char-count "\n"))))
