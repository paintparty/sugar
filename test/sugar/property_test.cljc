(ns sugar.property-test
  "Property tests (test.check) for sugar.core/styled, the color conversions in
   sugar.utils, and the browser console converter. Generated options include
   invalid values on purpose, because styled must handle them without throwing."
  (:require #?(:clj  [clojure.test :refer [deftest is]]
               :cljs [cljs.test :refer-macros [deftest is]])
            [clojure.string :as str]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [sugar.browser-dev-console :as browser]
            [sugar.core :refer [styled]]
            [sugar.data :as data]
            [sugar.snapshot-cases :as cases]
            [sugar.utils :as utils]))

;; Generators
;; -----------------------------------------------------------------------------

(def ^:private gen-level (gen/choose 0 3))

(def ^:private hex-chars (vec "0123456789abcdefABCDEF"))

(def ^:private gen-hex-digits
  "Hex digit strings of every valid length (3, 4, 6, 8) and some invalid."
  (gen/bind (gen/elements [2 3 4 5 6 8])
            (fn [n] (gen/fmap str/join (gen/vector (gen/elements hex-chars) n)))))

(def ^:private gen-color
  "Valid and invalid color values."
  (gen/one-of
   [(gen/elements (vec data/all-colors))
    (gen/fmap keyword (gen/elements (vec data/all-colors)))
    (gen/fmap #(str "#" %) gen-hex-digits)
    gen-hex-digits
    (gen/vector (gen/choose 0 255) 3)
    (gen/choose 0 255)
    ;; invalid
    (gen/choose -300 600)
    (gen/vector (gen/choose -10 300) 0 5)
    gen/string-alphanumeric
    gen/keyword
    gen/boolean
    (gen/return nil)]))

(def ^:private gen-bool (gen/one-of [gen/boolean gen/string-alphanumeric]))

(def ^:private gen-enum
  (gen/one-of [(gen/elements ["bold" "dim" "normal" "italic" "wavy" "double"
                              "straight" "dotted" "dashed" "heavy"])
               (gen/fmap keyword (gen/elements ["bold" "dim" "italic" "wavy"]))
               gen/string-alphanumeric
               (gen/choose -5 5)]))

(def ^:private gen-opts-map
  (gen/fmap (fn [kvs] (into {} kvs))
            (gen/vector
             (gen/one-of
              [(gen/tuple (gen/elements [:color :background-color]) gen-color)
               (gen/tuple (gen/elements [:font-weight :font-style :underline-style]) gen-enum)
               (gen/tuple (gen/elements [:reverse :strikethrough :blink
                                         :underline :hide :print-warnings
                                         :validate-options])
                          gen-bool)
               (gen/tuple gen/keyword gen/any-printable)])
             0 8)))

(def ^:private gen-opts-str
  (gen/fmap #(str/join "." %)
            (gen/vector
             (gen/one-of [(gen/elements ["bold" "dim" "italic" "underline" "reverse"
                                         "strikethrough" "blink" "hide" "red"
                                         "bg-blue" "bg-user-green" "#BADA55"
                                         "color-196" "bold-red"])
                          gen/string-alphanumeric])
             0 5)))

(def ^:private gen-opts
  (gen/one-of [gen-opts-map gen-opts-str (gen/fmap keyword gen-opts-str) (gen/return nil)]))

(def ^:private text-chars
  (vec (concat (map char (range 32 127)) "éü漢字")))

(def ^:private gen-text
  "Printable text without ESC. May contain `%`."
  (gen/fmap str/join (gen/vector (gen/elements text-chars) 0 20)))

(def ^:private gen-text-no-percent
  (gen/fmap #(str/replace % "%" "") gen-text))

;; Helpers
;; -----------------------------------------------------------------------------

(defn- run-styled
  "Calls styled at color level n with a mocked context. Returns the result and
   anything it printed."
  [text opts n]
  (let [result (atom nil)
        printed (with-out-str
                  (reset! result
                          (styled text opts (cases/context {"FORCE_COLOR" (str n)}))))]
    {:result  @result
     :printed printed}))

(def ^:private sgr-re (re-pattern "\u001b\\[([0-9;:]*)m"))

(defn- strip-sgr [s] (str/replace s sgr-re ""))

(defn- sgr-params
  "Every parameter string from every SGR sequence in s. A bare reset, ESC[m,
   has none."
  [s]
  (mapcat (fn [[_ ps]] (when (seq ps) (str/split ps #";"))) (re-seq sgr-re s)))

(defn- in-range? [s hi]
  (and (re-matches #"[0-9]+" s)
       (<= 0 #?(:clj (Long/parseLong s) :cljs (js/parseInt s 10)) hi)))

(defn- params-valid?
  "True when every parameter is well formed. 38 and 48 take either `5;n` or
   `2;r;g;b`, and every n, r, g and b is 0 to 255. Other parameters are plain
   codes up to 107, or an underline style such as 4:3."
  [params]
  (loop [[p & more :as ps] params]
    (cond
      (empty? ps) true

      (#{"38" "48"} p)
      (case (first more)
        "5" (and (some? (second more))
                 (in-range? (second more) 255)
                 (recur (drop 2 more)))
        "2" (and (every? #(and % (in-range? % 255)) (take 3 (rest more)))
                 (= 3 (count (take 3 (rest more))))
                 (recur (drop 4 more)))
        false)

      (re-matches #"4:[1-5]" p) (recur more)
      :else (and (in-range? p 107) (recur more)))))

(defn- check
  "Runs a test.check property and reports the shrunk failure, if any."
  [n property]
  (let [r (tc/quick-check n property)]
    (is (:pass? r) (pr-str (select-keys r [:fail :shrunk :seed])))))

;; Properties of styled
;; -----------------------------------------------------------------------------

(deftest styled-never-throws-or-prints-a-caught-exception
  (check 300
         (prop/for-all [text gen-text opts gen-opts n gen-level]
           (let [{:keys [result printed]} (run-styled text opts n)]
             (and (string? result)
                  (not (str/includes? printed "Error(Caught)")))))))

(deftest at-level-0-output-equals-input
  (check 300
         (prop/for-all [text gen-text opts gen-opts]
           (= text (:result (run-styled text opts 0))))))

(deftest stripping-sgr-returns-the-input-text
  (check 300
         (prop/for-all [text gen-text opts gen-opts n gen-level]
           (= text (strip-sgr (:result (run-styled text opts n)))))))

(deftest every-emitted-sgr-parameter-is-in-range
  (check 300
         (prop/for-all [text gen-text opts gen-opts n gen-level]
           (params-valid? (sgr-params (:result (run-styled text opts n)))))))

;; Properties of sugar.utils
;; -----------------------------------------------------------------------------

(deftest rgb->8-bit-returns-16-to-255
  (check 500
         (prop/for-all [r (gen/choose -1000 1000) g (gen/choose -1000 1000) b (gen/choose -1000 1000)]
           (<= 16 (utils/rgb->8-bit r g b) 255))))

(defn- parse-hex [s]
  #?(:clj (Long/parseLong s 16) :cljs (js/parseInt s 16)))

(defn- manual-rgb
  "Parses 3 or 6 hex digits by hand."
  [s]
  (let [s (if (= 3 (count s)) (str/join (mapcat #(vector % %) s)) s)]
    (mapv #(parse-hex (subs s % (+ % 2))) [0 2 4])))

(deftest hex->rgb-agrees-with-manual-parsing
  (check 300
         (prop/for-all [s (gen/bind (gen/elements [3 6])
                                    (fn [n] (gen/fmap str/join
                                                      (gen/vector (gen/elements hex-chars) n))))]
           (= (manual-rgb s) (utils/hex->rgb s)))))

(deftest a-leading-hash-does-not-change-hex-conversions
  (check 300
         (prop/for-all [s (gen/bind (gen/elements [3 4 6 8])
                                    (fn [n] (gen/fmap str/join
                                                      (gen/vector (gen/elements hex-chars) n))))]
           (and (= (utils/hex->rgba s) (utils/hex->rgba (str "#" s)))
                (= (utils/hex->8-bit s) (utils/hex->8-bit (str "#" s)))))))

;; Properties of the browser converter
;; -----------------------------------------------------------------------------

(deftest browser-converter-returns-one-style-per-format-specifier
  (check 300
         (prop/for-all [text gen-text-no-percent opts gen-opts-map n (gen/choose 1 3)]
           (let [s (:result (run-styled text opts n))
                 v (some-> (browser/ansi-sgr-string->browser-dev-console-array s) vec)]
             (or (nil? v)
                 (= (count (re-seq #"%c" (first v)))
                    (count (rest v))))))))
