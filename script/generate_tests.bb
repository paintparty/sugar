#!/usr/bin/env bb

;; Generates test/sugar/styled_test.cljc with combinatorial styled tests, and
;; test/sugar/sample_cases.cljc with the same cases as data, for
;; `bb visual:samples`.
;; Run: bb script/generate_tests.bb

(require '[clojure.string :as str])

(def output-path "test/sugar/styled_test.cljc")
(def sample-output-path "test/sugar/sample_cases.cljc")

;; ---- Test dimension definitions ----

(def colors
  ["red" "blue" "green" "cyan" "purple" "orange" "magenta" "gray"
   "light-red" "dark-blue" "extra-light-green" "extra-dark-purple"
   "soft-cyan" "light-orange"
   "user-red" "user-bright-blue"
   "neutral" "max"
   "black" "white"])

(def bg-colors
  [nil "blue" "extra-dark-red" "white" "user-black"])

(def font-styles
  [nil :italic :normal])

(def font-weights
  [nil :bold :dim])

(def underline-opts
  [nil
   {:underline true}
   {:underline true :underline-style :wavy}
   {:underline true :underline-style :double}
   {:underline true :underline-style :dotted}
   {:underline true :underline-style :dashed}
   {:underline true :underline-style :straight}])

(def bool-modifiers
  [{}
   {:reverse true}
   {:strikethrough true}
   {:hide true}
   {:blink true}])

(def hex-colors
  [;; 6-digit
   "FF0000" "00FF00" "0000FF" "ff8800" "336699"
   ;; 3-digit (shorthand)
   "ABC" "fff" "000" "F0F"
   ;; 8-digit (RRGGBBAA)
   "FF000080" "00FF00FF" "336699CC"
   ;; 4-digit (RGBA shorthand)
   "F00F" "ABC8" "0008"
   ;; with # prefix, 6-digit
   "#FF0000" "#00ff00" "#BADA55"
   ;; with # prefix, 3-digit
   "#ABC" "#fff" "#F0F"
   ;; with # prefix, 8-digit
   "#FF000080" "#00FF00FF"
   ;; with # prefix, 4-digit
   "#F00F" "#ABC8"
   ;; edge cases
   "000000" "FFFFFF" "ffffff" "#000" "#FFF"])

(def rgb-colors
  [[255 0 0] [0 128 255] [50 205 50]])

(def eight-bit-colors
  [196 39 82 231 16])

;; ---- Combination generators ----

(defn take-spread
  "Take n items spread evenly across coll."
  [n coll]
  (let [v (vec coll)
        c (count v)]
    (if (<= c n)
      v
      (mapv #(nth v (int (* (/ % n) c))) (range n)))))

(defn pr-val [v]
  (cond
    (nil? v)     "nil"
    (keyword? v) (str v)
    (string? v)  (str "\"" v "\"")
    (vector? v)  (str v)
    (number? v)  (str v)
    (map? v)     (str v)
    :else        (pr-str v)))

(defn opts-map-str [m]
  (str "{"
       (str/join "\n                "
                 (for [[k v] (sort-by (comp str key) m)
                       :when (some? v)]
                   (str k " " (pr-val v))))
       "}"))

(defn test-name [prefix idx]
  (str prefix "-" idx))

;; ---- Build test cases ----

(def test-cases (atom []))
(def idx (atom 0))

(defn add-test! [label opts-map]
  (let [i (swap! idx inc)]
    (swap! test-cases conj {:name  (str (str/replace label #"[^a-zA-Z0-9]+" "-") "-" i)
                            :label label
                            :opts  opts-map})))

;; 1. Each color as foreground
(doseq [c colors]
  (add-test! (str "fg-" c)
             {:color (str "\"" c "\"")}))

;; 2. Color + background combinations (sampled)
(doseq [fg (take-spread 6 colors)
        bg bg-colors
        :when bg]
  (add-test! (str "fg-" fg "-bg-" bg)
             {:color            (str "\"" fg "\"")
              :background-color (str "\"" bg "\"")}))

;; 3. Font style × font weight
(doseq [fs font-styles
        fw font-weights
        :when (or fs fw)]
  (add-test! (str "style-" (name (or fs "none")) "-weight-" (name (or fw "none")))
             (cond-> {:color "\"red\""}
               fs (assoc :font-style fs)
               fw (assoc :font-weight fw))))

;; 4. Underline variants
(doseq [u underline-opts
        :when u]
  (let [style (:underline-style u)]
    (add-test! (str "underline" (when style (str "-" (name style))))
               (merge {:color "\"cyan\""} u))))

;; 5. Boolean modifiers
(doseq [bm bool-modifiers
        :when (seq bm)]
  (let [k (first (keys bm))]
    (add-test! (str "modifier-" (name k))
               (merge {:color "\"green\""} bm))))

;; 6. Boolean modifier combos
(add-test! "combo-bold-italic-underline"
           {:color        "\"purple\""
            :font-weight  :bold
            :font-style   :italic
            :underline    true})

(add-test! "combo-dim-strikethrough-reverse"
           {:color         "\"orange\""
            :font-weight   :dim
            :strikethrough true
            :reverse       true})

(add-test! "combo-all-modifiers"
           {:color            "\"blue\""
            :background-color "\"white\""
            :font-weight      :bold
            :font-style       :italic
            :underline        true
            :underline-style  :wavy
            :strikethrough    true
            :reverse          true})

;; 7. Hex colors
(doseq [h hex-colors]
  (add-test! (str "hex-" h)
             {:color (str "\"" h "\"")}))

;; 8. RGB vector colors
(doseq [rgb rgb-colors]
  (add-test! (str "rgb-" (str/join "-" rgb))
             {:color (str rgb)}))

;; 9. 8-bit integer colors
(doseq [n eight-bit-colors]
  (add-test! (str "8bit-" n)
             {:color (str n)}))

;; 10. Hex as background (various formats)
(add-test! "hex-bg-6digit"
           {:color            "\"white\""
            :background-color "\"FF4500\""})

(add-test! "hex-bg-3digit"
           {:color            "\"white\""
            :background-color "\"A3C\""})

(add-test! "hex-bg-hash-6digit"
           {:color            "\"white\""
            :background-color "\"#1A2B3C\""})

(add-test! "hex-bg-hash-3digit"
           {:color            "\"white\""
            :background-color "\"#F90\""})

(add-test! "hex-bg-8digit"
           {:color            "\"white\""
            :background-color "\"FF450080\""})

(add-test! "hex-fg-and-bg-mixed"
           {:color            "\"#ABC\""
            :background-color "\"336699\""})

;; 11. RGB as background
(add-test! "rgb-bg"
           {:color            "\"black\""
            :background-color "[70 130 180]"})

;; 12. Keyword forms (vs string forms already tested above)
(doseq [c [:red :blue :green :purple :cyan]]
  (add-test! (str "kw-" (name c))
             {:color c}))

;; 13. Keyword background
(add-test! "kw-bg"
           {:color            :white
            :background-color :extra-dark-blue})

;; 14. Empty/nil edge cases
(add-test! "nil-opts" nil)
(add-test! "empty-opts" {})

;; 15. Tokenized string syntax
(def string-tests
  [["str-color-only"                "red"]
   ["str-bold"                      "bold.red"]
   ["str-dim-italic"                "dim.italic.cyan"]
   ["str-bg"                        "white.bg-blue"]
   ["str-bold-italic-bg"            "bold.italic.green.bg-extra-dark-red"]
   ["str-strikethrough"             "strikethrough.orange"]
   ["str-reverse"                   "reverse.purple"]
   ["str-hide"                      "hide.blue"]
   ["str-blink"                     "blink.red"]
   ["str-underline"                 "underline.cyan"]
   ["str-wavy-underline"            "wavy-underline.red"]
   ["str-double-underline"          "double-underline.green"]
   ["str-dotted-underline"          "dotted-underline.blue"]
   ["str-dashed-underline"          "dashed-underline.purple"]
   ["str-straight-underline"        "straight-underline.orange"]
   ["str-all-modifiers"             "bold.italic.red.bg-white.strikethrough.wavy-underline.reverse"]
   ["str-user-color"                "user-bright-blue"]
   ["str-user-color-bg"             "user-red.bg-user-white"]
   ["str-variant-color"             "extra-dark-purple.bold"]
   ["str-dim-strikethrough-reverse" "dim.orange.strikethrough.reverse"]
   ["str-normal-weight"             "normal.red"]])

(doseq [[label s] string-tests]
  (let [i (swap! idx inc)]
    (swap! test-cases conj {:name     (str label "-" i)
                            :label    label
                            :opts     s
                            :string?  true})))

;; 16. Mocked env tests: exercise the 3-arity (styled x m supports-color).
;; sugar.snapshot-cases/context builds the context, with every other env var
;; set to nil and no tty, so the real shell and terminal can't leak in.
;; FORCE_COLOR sets the color level, and CLITHEME sets the polarity.
(def mock-envs
  [{"FORCE_COLOR" "0"}
   {"FORCE_COLOR" "1"}
   {"FORCE_COLOR" "2"}
   {"FORCE_COLOR" "3"}
   {"FORCE_COLOR" "3" "CLITHEME" "dark"}
   {"FORCE_COLOR" "3" "CLITHEME" "light"}])

(def mock-env-names
  ["level-0" "level-1" "level-2" "level-3" "level-3-polarity-dark" "level-3-polarity-light"])

;; Sample of opts to cross with mock-envs
(def mock-env-opts
  [["fg"               {:color "\"red\""}]
   ["fg-bg"            {:color "\"cyan\"" :background-color "\"blue\""}]
   ["bold-italic"      {:color "\"green\"" :font-weight :bold :font-style :italic}]
   ["hex"              {:color "\"#FF8800\""}]
   ["rgb"              {:color "[50 205 50]"}]
   ["8bit"             {:color "196"}]
   ["user-color"       {:color "\"user-red\""}]
   ["variant"          {:color "\"extra-dark-purple\""}]
   ["underline-wavy"   {:color "\"orange\"" :underline true :underline-style :wavy}]
   ["all-mods"         {:color "\"blue\"" :background-color "\"white\"" :font-weight :bold
                        :font-style :italic :underline true :strikethrough true :reverse true}]])

(doseq [[env-name mock-env] (map vector mock-env-names mock-envs)
        [opts-label opts]   mock-env-opts]
  (let [i (swap! idx inc)]
    (swap! test-cases conj {:name     (str env-name "-" opts-label "-" i)
                            :label    (str env-name "-" opts-label)
                            :opts     opts
                            :mock-env mock-env})))

;; String opts with mock-env
(doseq [[env-name mock-env] (map vector mock-env-names mock-envs)
        s                   ["red" "bold.italic.cyan" "dim.orange.bg-white"]]
  (let [label (str env-name "-str-" (str/replace s #"\." "-"))
        i     (swap! idx inc)]
    (swap! test-cases conj {:name     (str label "-" i)
                            :label    label
                            :opts     s
                            :string?  true
                            :mock-env mock-env})))

;; ---- Emit .cljc file ----

(defn emit-opts-literal [{:keys [opts string?]}]
  (cond
    string?
    (str "\"" opts "\"")

    (or (nil? opts) (empty? opts))
    (pr-str opts)

    :else
    (let [entries (for [[k v] (sort-by (comp str key) opts)
                        :when (some? v)]
                    (str (pr-str k) " " (if (keyword? v) (str v) (str v))))]
      (str "{" (str/join " " entries) "}"))))

(defn emit-env-literal
  "The env vars a case mocks, as a map literal."
  [m]
  (str "{"
       (str/join " " (for [[k v] (sort m)]
                       (str "\"" k "\" \"" v "\"")))
       "}"))

(defn emit-test [{:keys [name opts mock-env] :as test-case}]
  (let [opts-str (emit-opts-literal test-case)
        safe-name (-> name
                      (str/replace #"^-+" "")
                      (str/replace #"-+$" "")
                      (str/replace #"-+" "-")
                      (cond-> (Character/isDigit (first name)) (->> (str "n"))))]
    (if mock-env
      (let [env-str (emit-env-literal mock-env)]
        (str "(deftest " safe-name "\n"
             "  (let [opts " opts-str "\n"
             "        supports-color (cases/context " env-str ")\n"
             "        result (core/styled \"sample\" opts supports-color)]\n"
             "    (is (string? result))))\n"))
      (str "(deftest " safe-name "\n"
           "  (let [opts " opts-str "\n"
           "        result (core/styled \"sample\" opts)]\n"
           "    (is (string? result))))\n"))))

(defn emit-file [tests]
  (str
   ";; AUTO-GENERATED by script/generate_tests.bb. Do not edit by hand\n"
   "(ns sugar.styled-test\n"
   "  (:require #?(:clj  [clojure.test :refer [deftest is]]\n"
   "               :cljs [cljs.test :refer-macros [deftest is]])\n"
   "            [sugar.core :as core]\n"
   "            [sugar.snapshot-cases :as cases]))\n"
   "\n"
   ";; --------------------------------------------------------------------------\n"
   ";; Generated tests\n"
   ";; --------------------------------------------------------------------------\n"
   "\n"
   (str/join "\n" (map emit-test tests))))

(defn emit-sample-case
  "One case as a map literal, one entry per line, values aligned."
  [{:keys [label mock-env] :as test-case}]
  (let [entries (cond-> [[":label" (pr-str label)]
                         [":opts" (emit-opts-literal test-case)]]
                  mock-env (conj [":env" (emit-env-literal mock-env)]))
        width   (apply max (map (comp count first) entries))
        pad     #(str % (apply str (repeat (- width (count %)) " ")))]
    (str "   {"
         (str/join "\n    " (map (fn [[k v]] (str (pad k) " " v)) entries))
         "}")))

(defn emit-sample-file [tests]
  (str
   ";; AUTO-GENERATED by script/generate_tests.bb. Do not edit by hand\n"
   "(ns sugar.sample-cases\n"
   "  \"The cases of sugar.styled-test as data, in generation order. Printed by\n"
   "   `bb visual:samples`.\")\n"
   "\n"
   "(def cases\n"
   "  [" (subs (str/join "\n\n" (map emit-sample-case tests)) 3) "])\n"))

(spit output-path (emit-file @test-cases))
(spit sample-output-path (emit-sample-file @test-cases))
(println (str "Generated " (count @test-cases) " tests → " output-path
              " and " sample-output-path))
