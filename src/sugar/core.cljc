(ns sugar.core
  "Top-level api namespace for public functions which assist in styling text
   with ANSI SGR, for output to terminal environments.

   Supports output for system terminal emulator, as well development consoles in
   web browser dev tool environments."
  (:require [clojure.string :as str]
            [sugar.browser-dev-console]
            [sugar.utils :as utils]
            [sugar.data :as palette]
            [supports-color.core]))


;; Env / Config / Options ------------------------------------------------------


;; Disabled: sugar reads no env vars. Mood comes from supports-color.core/theme,
;; and contrast is fixed at "medium" for now. Kept for reference.
#_(def debug-mood? false)

#_(defn- user-mood
  "Resolves the dark/light/medium mood from COLOR_THEME env var or terminal query."
  [ctx debug?]
  (let [s (get-in @ctx [:env "COLOR_THEME"] "medium")
        _ (when debug?
            (println (str "sugar.core/set-mood! :: COLOR_THEME=" s)))
        s (if (contains? #{"dark" "light" "medium"} s)
            s
            (some-> s
                    (str/split #" ")
                    last
                    str/lower-case))]
    s))

#_(defn- set-mood!
  "Resolves the dark/light/medium mood from COLOR_THEME env var or terminal query."
  [ctx]
  (let [s      (user-mood ctx debug-mood?)
        mood   (or (when (contains? #{"dark" "light"} s)
                     s)
                   #?(:clj
                      ;; Currently sets to light, if light detected
                      (when (some-> (get @ctx :dark-background?) not)
                        (do (when debug-mood?
                              (println (str "sugar.core/set-mood! :: "
                                            "(supports-color.core/dark-background?) "
                                            "=> false "
                                            ";; setting to \"light\"")))
                            "light"))
                      :cljs
                      nil)
                   (do (when debug-mood? (println "Falling back to medium"))
                       "medium"))]
    (swap! ctx assoc :mood mood)))


#_(defn- set-contrast!
  "Reads COLOR_CONTRAST env var and stores it in ctx (defaults to \"medium\")."
  [ctx]
  (let [contrast (get-in @ctx [:env "COLOR_CONTRAST"] "medium")
        _        (when debug-mood?
                   (println (str "sugar.core/set-contrast! :: COLOR_CONTRAST="
                                 contrast)))]
    (swap! ctx assoc :contrast contrast)))


(defn- color-variant
  "Derives the palette color variant (e.g. \"light\", \"dark\", \"soft\") from
   mood and contrast."
  [mood contrast]
  (cond (= mood "dark")
        (case contrast
          "high" "extra-light"
          "low" "soft"
          "light")
        (= mood "light")
        (case contrast
          "high" "extra-dark"
          "low" "soft"
          "dark")
        :else
        ;; If mood defaults to medium, we can only offer a "soft"
        ;; variant, if the user has asked for low-contrast
        (case contrast
          "low" "soft"
          "medium")))


(defn- subtle-color-variant
  "The variant a subtle- color resolves to: shadow on a dark background,
   highlight on a light one. nil when the mood is not detected, and the color
   then resolves to neutral."
  [mood]
  (case mood
    "dark"  "shadow"
    "light" "highlight"
    nil))


(defn- detect-profile
  "The supported color level, 0 to 3, and the :host (:jvm, :js, or :browser).
   Reads env and TTY status, no terminal query. In a browser the level is 3.
   `supports-color` is a supports-color :context map, or nil."
  [supports-color]
  (let [p (supports-color.core/profile {:context supports-color})]
    {:level (:level p)
     :host  (get-in p [:runtime :host])}))


(defn- detect-theme
  "Mood and palette variants from the terminal's polarity. May query the
   terminal, unless CLITHEME decides the polarity. `supports-color` is a
   supports-color :context map, or nil. A mocked context never queries the real
   terminal, unless it supplies :runtime :tty itself."
  [supports-color]
  (let [theme    (supports-color.core/theme
                  ;; No :timeout-ms, so the query uses supports-color's
                  ;; default, which is long enough for slow SSH links.
                  {:context (if (some? supports-color)
                              (update-in supports-color
                                         [:runtime :tty]
                                         #(or % {:stdout false
                                                 :stderr false
                                                 :stdin  false}))
                              supports-color)})
        mood     (get #{"dark" "light"} (:polarity theme) "medium")
        contrast "medium"]
    {:theme                    theme
     :mood                     mood
     :contrast                 contrast
     :color-variant            (color-variant mood contrast)
     :subtle-color-variant     (subtle-color-variant mood)}))


(defn- resolve-ctx
  "Level, host, mood, and palette variants for a supports-color :context map."
  [supports-color]
  (merge (detect-profile supports-color)
         (detect-theme supports-color)))


(def ^:private profile-ctx
  "The color level and host of this process. Resolved at load, no terminal
   query."
  (detect-profile nil))


(def ^:private theme-ctx
  "Mood and palette variants of this process. Resolved on the first styled call
   at a level above 0, so requiring sugar never touches the terminal."
  (delay (detect-theme nil)))


(def ^:private process-ctx
  "theme-ctx and profile-ctx merged, once. Neither changes after it is
   resolved, so styled calls don't merge them again."
  (delay (merge @theme-ctx profile-ctx)))


(def ^:private ^:dynamic *ctx*
  "A context resolved for one mocked styled call. nil means use this process's
   own context."
  nil)


(defn- current-ctx []
  (or *ctx* @process-ctx))


(defn ^:no-doc print-profile!
  "Prints the resolved color level, polarity, and palette variants of ctx."
  [ctx]
  (println
   (str "\nResolved color level:  " (:level ctx) "\n"
        "Polarity:  " (get-in ctx [:theme :polarity]) "\n"
        "Polarity detection method:  " (get-in ctx [:theme :detection-method]) "\n"
        "Resolved color-variant:   " (:color-variant ctx) "\n"
        "Resolved subtle-color-variant:   " (:subtle-color-variant ctx))))


;; Utils -------------------------------------------------------------------

(defn- as-str
  "Coerces x to string, using name for keywords and symbols."
  [x]
  (str (if (or (keyword? x) (symbol? x)) (name x) x)))

(defn- when->>
  "If (= (pred x) true), returns x, otherwise nil.
   Useful in a `clojure.core/some->>` threading form."
  [pred x]
  (when (or (true? (pred x))
            (when (set? pred) (contains? pred x)))
    x))

;; Debugging, Errors and Warnings ----------------------------------------------

(def ^:private sgr-tags*
  "Used for constructing warning, error, and, info messages"
  {:wavy-underline        "\033[4:3m"
   :orange-wavy-underline "\033[38;5;208;4:3m"
   :italic                "\033[3m"
   :gray                  "\033[38;5;247m"
   :bold-gray             "\033[38;5;247;1m"
   :italic-gray           "\033[3;38;5;247m"
   :bold-italic-gray      "\033[3;38;5;247;1m"
   :bold-italic           "\033[3;1m"
   :bold-orange           "\033[1;38;5;208m"
   :orange                "\033[38;5;208m"
   :green                 "\033[38;5;40m"
   :red                   "\033[38;5;196m"
   :bold-red              "\033[1;38;5;196m"
   :blue                  "\033[38;5;39m"
   :bold                  "\033[1m"
   :dim                   "\033[2m"
   :sgr-tag-close         "\033[0;m"})


(defn ^:public ^:no-doc sgr*
  "Wraps v in the named SGR escape sequence from sgr-tags* if level > 0."
  [k v level]
  (if (pos? level)
    (str (get sgr-tags* k) v (:sgr-tag-close sgr-tags*))
    v))

(defn- wrapped-coll
  "Formats coll as a vector literal, with as many items per line as fit in 72
   columns. A set is sorted first."
  [coll]
  (let [items (map pr-str (if (set? coll) (sort coll) coll))
        lines (reduce (fn [lines s]
                        (let [line (peek lines)]
                          (if (and line (<= (+ (count line) 1 (count s)) 70))
                            (conj (pop lines) (str line " " s))
                            (conj lines s))))
                      []
                      items)]
    (str "[" (str/join "\n " lines) "]")))

(defn- pred-display
  "The predicate shown in a warning. A set prints sorted, so the output is the
   same on every platform."
  [x]
  (if (set? x)
    (str "#{" (str/join " " (map pr-str (sort x))) "}")
    (str x)))

(defn- satisfactions
  "Formats a spec's desc entries as labeled alternatives joined by '~ OR ~'."
  [desc level]
  (str/join (sgr* :italic "\n\n~ OR ~\n\n" level)
            (mapv #(str
                    (:label %)
                    (when (:pred %)
                      (str "\n"
                           (wrapped-coll (:pred %)))))
                  desc)))


(def ^:private supplied-value-max-chars 200)


(defn- control-char->escape
  "The \\uXXXX escape of the one-character string c."
  [c]
  (let [h #?(:clj  (Integer/toHexString (int (first c)))
             :cljs (.toString (.charCodeAt c 0) 16))]
    (str "\\u" (subs (str "0000" h) (count h)))))


(defn- supplied-value
  "Formats a user-supplied value for display in warning/error blocks. Control
   characters are shown as \\uXXXX escapes, so a value can't send escape codes
   to the terminal. Collections print at most 20 items and 4 levels deep, so an
   infinite seq can't hang, and the result is cut to 200 characters."
  [supplied level]
  (let [s (-> (cond
                (= supplied nil)
                "nil"
                (string? supplied)
                (str "\"" supplied "\"")
                :else
                (binding [*print-length* 20
                          *print-level*  4]
                  (pr-str supplied)))
              (str/replace #"[\x00-\x1f\x7f-\x9f]" control-char->escape))]
    (sgr* :bold
          (if (> (count s) supplied-value-max-chars)
            (str (subs s 0 supplied-value-max-chars) "...")
            s)
          level)))


(defn- indent-block
  "Indents every line of s with indent-str."
  [indent-str s]
  (->> (str/split s #"\n")
       (mapv #(str indent-str %))
       (str/join "\n")))


(defn- block-label
  "Returns an italicized label string followed by two newlines."
  [s level]
  (str (sgr* :italic s level) "\n\n"))


(defn- supplied-value-label
  "Builds the 'Supplied value for <prop>:' label for warning/error output."
  [prop level]
  (block-label (str (sgr* :italic
                          (str "Supplied value" (when prop " for "))
                          level)
                    (sgr* :bold-italic prop level)
                    ":")
               level))


(defn- supplied-must-satisfy-label
  "Builds the 'The value of <prop> must satisfy:' label for warning/error output."
  [prop level]
  (block-label (str (sgr* :italic
                          "The value of "
                          level)
                    (sgr* :bold-italic prop level)
                    (sgr* :italic " must satisfy:" level))
               level))


(defn- print-block
  "Prints a boxed warning or error message with spec violation details."
  [label type prop spec supplied level]
  (println
   (str (sgr* (case type :warning :bold-orange :error :bold-red :bold)
              (str "\n╭─── " label " ───── sugar.core/styled\n")
              level)
        "\n"
        (indent-block
         "  "
         ;; (sgr* :bold prop level)
         (let [desc (get-in spec [prop :desc])]
           (str
            (when (not (nil? supplied))
              (str (supplied-value-label prop level)
                   (indent-block "   " (supplied-value supplied level))
                   "\n\n\n"))
            (when (not (nil? prop))
              (str (supplied-must-satisfy-label prop level)
                   (indent-block "   "
                                 (satisfactions desc level))
                   "\n\n\n"
                   (block-label "Predicate:" level)
                   "    "
                   (sgr* :bold
                         (pred-display (get-in spec [prop :pred-display]))
                         level)
                   "\n\n\n"))
            "\n\n")))
        "\n\n"
        (sgr* :orange
              "╰────────────────────────────────────────"
              level))))

(defn ^:public ?sgr
  "Prints `s` with its SGR escape codes shown as text, for debugging.
   Returns `s`.
   
   Each code prints as readable text on a gray background, followed by
   the code itself, so the text keeps its styling.
   
   Show the codes that styled produced
   ```clojure
   (?sgr (styled \"hello\" :bold.red))
   ```"
  {:tldr     "Prints `s` with its SGR escape codes shown as text, for debugging.
              Returns `s`."
   :desc     "Each code prints as readable text on a gray background, followed by
              the code itself, so the text keeps its styling."
   :examples [{:desc  "Show the codes that styled produced"
               :forms '[[(?sgr (styled "hello" :bold.red))]]}]}
  [s]
  (println
   (str/replace s
                #"\u001b\[((?:[0-9;]|4:[1-5])*)m"
                (str "\033[38;5;231;48;5;247m" ; <- white on gray, actual ansi sgr tag for fake ansi tag
                     "\\\\033[$1m"             ; <- fake ansi sgr tag text
                     "\033[0m"                 ; <- reset, actual ansi sgr for resetting fake ansi tag
                     "\033[$1m"                ; <- original ansi sgr, to preserve coloring
                     )))
  s)


;; Public API, supporting fns --------------------------------------------------

(defn- named-color->user-color
  "If we need to compress level 3 (truecolor) or level 2 (x256) down to level 1
  (8 or 16 colors), we may need to convert some of the sugar pallete colors."
  [s]
  (cond
    (re-find #"^shadow-[a-z]+$" s)
    "black"

    (re-find #"^highlight-[a-z]+$" s)
    "white"

    (re-find #"gray" s)
    "white"

    :else
    (when-let [c (re-find sugar.data/base-colors-re s)]
      (-> (str/replace c
                       #"^olive$|^orange$|^purple$"
                       (fn [x]
                         (case x
                           "olive" "green"
                           "orange" "yellow"
                           "purple" "blue"
                           x)))))))

(defn- sgr-8-bit-user
  "Looks up the SGR code for a 3/4-bit user-palette color name and layer."
  [color layer]
  (some->> color
           as-str
           (#(if (= "foreground" layer)
               (get sugar.data/user-colors-foreground %)
               (get sugar.data/user-colors-background (str % "-bg"))))))


(defn- sgr-color-component
  "Builds the SGR parameter string (e.g. \"38;2;r;g;b\") for a color layer and mode."
  [fg-or-bg mode color]
  (str (if (= fg-or-bg "foreground") "38" "48")
       (if (= mode :truecolor) ";2;" ";5;")
       color))


(defn- sgr-rgb-color-sub-component
  "Resolves a named palette color to its SGR parameter string at the given level."
  [color layer level]
  (some->> (if (= level 3)
             (get sugar.data/colors-24-bit color)
             (some-> (get sugar.data/colors-8-bit color) vector))
           (str/join ";")
           (sgr-color-component layer
                                (if (= level 3)
                                  :truecolor
                                  :8-bit))))


(defn- sgr-color-str
  "Converts any valid color value to its full SGR escape parameter string."
  [color layer level]
  (when color
    (some->> (if (string? color)
               (or (sgr-8-bit-user color layer)
                   (sgr-rgb-color-sub-component color layer level))
               (if (vector? color)
                 (if (= level 3)
                   (sgr-color-component layer :truecolor (str/join ";" color))
                   (sgr-color-component layer :8-bit (apply utils/rgb->8-bit color)))
                 (sgr-color-component layer :8-bit color))))))

(defn- stringify-kw
  "Converts a keyword to its name string; passes other values through."
  [c]
  (if (keyword? c) (name c) c))


(defn- sgr-text-decoration-str
  "Returns the SGR underline parameter (e.g. \"4:3\" for wavy) or nil."
  [underline underline-style]
  (when (true? underline)
    (if-let [n (get sugar.data/underline-style-codes-by-style
                    (stringify-kw underline-style))]
      (str "4:" n)
      "4")))


(def ^:private invalid-constant "__SUGAR.CORE/INVALID_VALUE__")


(defn- eight-bit-color?
  "Returns true if x is an integer in the 0-255 range."
  [x]
  (and (int? x) (<= 0 x 255)))


(defn- string-or-keyword->valid-color
  "Converts a string or keyword into one of the following, depending on the
  value of x and the value of the color level:
  - A valid bling color (string), for subsequent lookup
  - An valid rgb tuple
  - A valid numerical id 0-255, for an 8-bit color"
  [x k level]
  (when (or (string? x) (keyword? x))
    (let [s (stringify-kw x)]
      (or (when-let [[_ hex-digits] (re-find utils/hex-color-re s)]
            (utils/hex->rgb-or-8-bit hex-digits level))
          ;; Valid bling pallette color
          (when (contains? sugar.data/all-colors s)
            (cond (contains? #{"black" "white" "neutral"} s)
                  s

                  ;; As a text color, the most contrast with the background.
                  ;; With no mood detected, the same as neutral: the
                  ;; terminal's own foreground, chosen to read well on its
                  ;; background. As a background color it is neutral, since
                  ;; white or black behind the terminal's own text color
                  ;; would be hard to read.
                  (= "max" s)
                  (if (= k :color)
                    (case (:mood (current-ctx))
                      "light" "black"
                      "dark"  "white"
                      "neutral")
                    "neutral")

                  (contains? sugar.data/base-colors-set s)
                  (str (:color-variant (current-ctx)) "-" s)

                  ;; Shadow or highlight, for the detected mood. With no mood
                  ;; detected, either could be the wrong one, so the color is
                  ;; left out.
                  (contains? sugar.data/subtle-colors s)
                  (if-let [variant (:subtle-color-variant (current-ctx))]
                    (str variant "-" (subs s (count "subtle-")))
                    "neutral")

                  :else
                  s))))))

(defn- nearest-user-color-variant
  "Maps a base color name to its nearest user-bright-* 3/4-bit variant string."
  [s]
  (let [fallback-to-user-bright-variant? true]
    (if (= s "black")
      "user-black"
      (str "user"
           (when fallback-to-user-bright-variant?
             "-bright")
           "-"
           s))))

(defn- valid-color
  "Returns a valid color value, or nil.

   Potential return values are:
   
   Sugar pallette colors and variants (8-bit and 24-bit versions available):
   \"red\", \"green\", \"extra-dark-olive\", \"highlight-yellow\"

   User colors (16 basic colors, 3/4 bit):
   \"user-cyan\", \"user-bright-cyan\"

   RGB vector (24-bit)
   [255 0 125]
   
   8-bit color (numerical index 0-255)
   97

   Colors may get downsampled here (or later in the sugar.core/styled pipeline),
   if the detected color level is lower than the compatibility of the supplied
   color."
  [x k spec print-warnings level]
  (if (nil? x)
    nil
    (let [ret*     (or (string-or-keyword->valid-color x k level)
                       (when (and (vector? x)
                                  (= 3 (count x))
                                  (let [[r g b] x]
                                    (and (eight-bit-color? r)
                                         (eight-bit-color? g)
                                         (eight-bit-color? b))))
                         x)
                       (when (eight-bit-color? x) x)
                       invalid-constant)
          invalid? (= ret* invalid-constant)
          ret      (when-not invalid?
                     (if (= level 1)
                       (cond
                         ;; Already using level 1 compatible name
                         (contains? sugar.data/all-user-colors ret*)
                         ret*

                         ;; It was supplied as an 8-bit
                         ;; It was supplied as a hex, and pre-converted to 8-bit
                         (eight-bit-color? ret*)
                         (-> ret*
                             utils/x256->nearest-user-color
                             nearest-user-color-variant)

                         ;; It was supplied as an [r g b] vector
                         (vector? ret*)
                         (some->> ret*
                                  (apply utils/rgb->8-bit)
                                  utils/x256->nearest-user-color
                                  nearest-user-color-variant)

                         ;; It was supplied as a pallete name variant
                         (string? ret*)
                         (some-> ret*
                                 named-color->user-color
                                 nearest-user-color-variant))

                       ret*))]
      (when invalid?
        (when print-warnings (print-block "WARNING" :warning k spec x level)))
      ret)))


(defn- valid-enum-option
  "Validates x against a spec's enum set; prints a warning and returns nil if
   invalid."
  [x k spec print-warnings level]
  (if (nil? x)
    nil
    (or (and (or (string? x)
                 (keyword? x))
             (let [s (stringify-kw x)]
               (some->> s
                        (when->> #(contains? (get-in spec [k :pred]) %)))))
        (when print-warnings
          (print-block "WARNING" :warning k spec x level)))))


(defn- valid-boolean-option
  "Validates x as a boolean; prints a warning and returns nil if invalid."
  [x k spec print-warnings level]
  (if (nil? x)
    nil
    (let [valid? (or (true? x) (false? x))]
      (if valid?
        x
        (when print-warnings
          (print-block "WARNING" :warning k spec x level))))))


(def ^:private boolean-spec
  {:pred         #(when (boolean? %) %)
   :pred-display 'boolean?
   :desc         [{:label "A boolean value - `true` or `false`"}]})


(def ^:private color-spec
  {:pred         valid-color
   :pred-display 'sugar.core/valid-color
   :desc         [{:label "An rgb vector such as [22 233 122]."}
                  {:label "A numerical id of an 8-bit color, from 0 to 255"}
                  {:label "A string or keyword that resolves to one of:"
                   :pred  sugar.data/all-colors}]})

(def ^:private underline-style-spec
  {:pred         sugar.data/underline-style-enum
   :pred-display sugar.data/underline-style-enum
   :desc         [{:label "A string or keyword that resolves to one of:"
                   :pred  sugar.data/underline-style-enum}]})

(def ^:private enum-desc
  [{:label "A string or keyword that resolves to one of:"}])

(def ^:private options-spec
  "Used internally for lightweight validation and warnings"
  {:font-weight       {:pred         sugar.data/font-weight-enum
                       :pred-display sugar.data/font-weight-enum
                       :desc         enum-desc}
   :font-style        {:pred         sugar.data/font-style-enum
                       :pred-display sugar.data/font-style-enum
                       :desc         enum-desc}
   :underline-style   underline-style-spec
   :color             color-spec
   :background-color  color-spec
   :reverse           boolean-spec
   :strikethrough     boolean-spec
   :blink             boolean-spec
   :underline         boolean-spec
   :hide              boolean-spec
   :print-warnings    boolean-spec
   :validate-options  boolean-spec})


(def ^:private style-token-prop
  "The name a warning uses for an unknown token in a dot-delimited style string
   or keyword."
  "a style token")


(def ^:private style-token-spec
  {style-token-prop
   {:pred-display 'sugar.core/parse-style-str
    :desc         [{:label "A font weight or font style:"
                    :pred  (into sugar.data/font-weight-enum sugar.data/font-style-enum)}
                   {:label "A boolean option:"
                    :pred  #{"underline" "strikethrough" "reverse" "hide" "blink"}}
                   {:label "An underline style followed by -underline, such as wavy-underline. The style is one of:"
                    :pred  sugar.data/underline-style-enum}
                   {:label "A color, or bg- followed by a color, such as bg-red. The color is one of:"
                    :pred  sugar.data/all-colors}]}})


;; SGR prefixes for the process context, keyed on the style exactly as the
;; caller passed it: a map, or a string or keyword before it is parsed. The process
;; context never changes after it is resolved, so the key needs nothing else.
;; Calls with a mocked context don't use the cache.
(def ^:private sgr-cache (atom {}))

;; The cache is emptied when it reaches this many entries, so a program that
;; styles with many distinct styles (an rgb color per value, say) can't grow
;; it without limit.
(def ^:private sgr-cache-limit 1024)

(defn- cache-sgr!
  "Adds style -> sgr to sgr-cache, emptying it first when it is full."
  [style sgr]
  (swap! sgr-cache
         (fn [cache]
           (assoc (if (< (count cache) sgr-cache-limit) cache {})
                  style
                  sgr))))


;; -----------------------------------------------------------------------------
;; NOTE: This lib is designed and implemented in the spirit of a reference
;; implementation. The build-sgr fn uses some calling conventions that would be
;; easier to port to other languages vs conventional clojure idioms.
;; -----------------------------------------------------------------------------

(defn- build-sgr
  "Compute the SGR escape prefix for style map m at the given color level.
   Reads (current-ctx) for color-variant resolution. Returns the prefix string or nil."
  [m level]
  (let [{:keys [font-weight
                font-style
                reverse
                hide
                blink
                strikethrough
                underline
                underline-style
                color
                background-color]}
        m


;; Validation is on unless :validate-options is present and not true.
;; Invalid values are dropped either way, because resolving a color also
;; checks it. Validation only decides whether they are reported. A
;; non-boolean :validate-options is reported, and turns validation off.
        validate-options
        (if (contains? m :validate-options)
          (true? (valid-boolean-option (:validate-options m)
                                       :validate-options
                                       options-spec
                                       true
                                       level))
          true)

        ;; With validation on, warnings print unless :print-warnings is
        ;; present and not true. A non-boolean :print-warnings is reported,
        ;; and turns warnings off.
        print-warnings
        (and validate-options
             (if (contains? m :print-warnings)
               (true? (valid-boolean-option (:print-warnings m)
                                            :print-warnings
                                            options-spec
                                            true
                                            level))
               true))

        ;; Always reported. String and keyword styles have no way to turn
        ;; validation off, and the parse has already found these tokens.
        _
        (doseq [token (::unknown-tokens m)]
          (print-block "WARNING" :warning style-token-prop style-token-spec token level))

        font-style
        (when-let [font-style
                   (valid-enum-option font-style
                                      :font-style
                                      options-spec
                                      print-warnings
                                      level)]
          (when (= font-style "italic") 3))

        font-weight
        (when-let [font-weight
                   (valid-enum-option font-weight
                                      :font-weight
                                      options-spec
                                      print-warnings
                                      level)]
          (case font-weight
            "bold" 1
            "dim" 2
            nil))

        color
        (when-let [color (valid-color color
                                      :color
                                      options-spec
                                      print-warnings
                                      level)]
          (sgr-color-str color "foreground" level))

        background-color
        (when-let [color (valid-color background-color
                                      :background-color
                                      options-spec
                                      print-warnings
                                      level)]
          (sgr-color-str color "background" level))

        underline
        (valid-boolean-option underline
                              :underline
                              options-spec
                              print-warnings
                              level)

        underline-style
        (valid-enum-option underline-style
                           :underline-style
                           options-spec
                           print-warnings
                           level)

        text-decoration
        (sgr-text-decoration-str underline underline-style)

        reverse
        (when (valid-boolean-option reverse
                                    :reverse
                                    options-spec
                                    print-warnings
                                    level)
          7)

        strikethrough
        (when (valid-boolean-option strikethrough
                                    :strikethrough
                                    options-spec
                                    print-warnings
                                    level)
          9)

        hide
        (when (valid-boolean-option hide
                                    :hide
                                    options-spec
                                    print-warnings
                                    level)
          8)

        blink
        (when (valid-boolean-option blink
                                    :blink
                                    options-spec
                                    print-warnings
                                    level)
          5)

        params
        (remove nil?
                [font-style
                 font-weight
                 text-decoration
                 blink
                 reverse
                 hide
                 strikethrough
                 color
                 background-color])]
    (when (seq params)
      (str "\033[" (str/join ";" params)))))


(def ^:private bool-tokens
  #{"strikethrough" "reverse" "hide" "blink" "underline"})


(defn- parse-style-str
  "Parses a dot-delimited style string (e.g. \"bold.red.bg-blue\") into an options map.
   Unknown tokens go in ::unknown-tokens, and build-sgr always warns about
   them, whatever :validate-options is. Empty tokens are skipped."
  [s]
  (reduce
   (fn [acc token]
     (cond
       (str/starts-with? token "bg-")
       (assoc acc :background-color (subs token 3))

       (contains? sugar.data/font-weight-enum token)
       (assoc acc :font-weight token)

       (contains? sugar.data/font-style-enum token)
       (assoc acc :font-style token)

       (str/ends-with? token "-underline")
       (let [style (subs token 0 (- (count token) 10))]
         (if (contains? sugar.data/underline-style-enum style)
           (assoc acc :underline true :underline-style style)
           (update acc ::unknown-tokens (fnil conj []) token)))

       (= token "underline")
       (assoc acc :underline true)

       (contains? bool-tokens token)
       (assoc acc (keyword token) true)

       (contains? sugar.data/all-colors token)
       (assoc acc :color token)

       (str/blank? token)
       acc

       :else
       (update acc ::unknown-tokens (fnil conj []) token)))
   {}
   (str/split s #"\.")))


(defn- style->map
  "The options map for a styled style. A string or keyword is parsed as
   dot-delimited tokens. A namespaced keyword keeps its namespace, so
   :foo/bold.red reports foo/bold as an unknown token."
  [style]
  (cond
    (string? style)  (parse-style-str style)
    (keyword? style) (parse-style-str (subs (str style) 1))
    :else            style))


(defn- opening-sgr
  "The opening SGR escape sequence for style, ending in \"m\", or nil when
   the level is 0 or style produces no codes. supports-color is as in
   styled. Throws when style is not a map, string, keyword or nil."
  [style supports-color]
  ;; Checked before style is hashed for the cache or passed to dissoc,
  ;; which never return for an infinite seq.
  (when-not (or (nil? style) (map? style) (string? style) (keyword? style))
    (throw (ex-info "style must be a map, string, keyword or nil" {})))
  (some->
   (if (seq supports-color)
     ;; Resolve a context for this call only. The process's own context
     ;; is untouched.
     (let [m     (style->map style)
           ctx   (resolve-ctx supports-color)
           level (:level ctx)]
       (when (:dev/print-profile m) (print-profile! ctx))
       (when-not (and (number? level) (zero? level))
         (binding [*ctx* ctx]
           (build-sgr (dissoc m :supports-color) level))))
     ;; Normal path, cached. A string or keyword is parsed only on a cache
     ;; miss.
     (let [level (:level profile-ctx)]
       (when (:dev/print-profile style) (print-profile! (current-ctx)))
       (when (and style
                  (not (and (number? level) (zero? level))))
         (if-let [e (find @sgr-cache style)]
           (val e)
           (let [s (build-sgr (style->map style) level)]
             (cache-sgr! style s)
             s)))))
   (str "m")))


(defn- report-caught!
  "Prints an error caught by styled or sgr, and the style that caused it."
  [e style]
  ;; Printing the error must not throw, or the caller would throw.
  (try
    (binding [*print-length* 20
              *print-level*  4]
      (println e))
    (print-block "Error(Caught)" :error nil nil style 0)
    (catch #?(:cljs :default :clj Throwable) _ nil)))


;; TODO - Add :highlight option
;; This would automatically apply bg-color which is the same as foreground color.
;; which would automatically use the shadow or highlight variant.
;; But only if user is in level 2 or 3, AND their theme is confirmed.
;;     TODO - what does this mean: "their theme is confirmed"?

(defn styled
  "Returns `x` wrapped in ANSI SGR escape codes for `style`.
   
   `style` is an options map, or a dot-delimited string or keyword
   such as `\"bold.red.bg-blue\"` or `:bold.red.bg-blue`. In a string
   or keyword, each token is a font weight or style, the name of a
   boolean option, an underline style followed by `-underline`
   (`wavy-underline`), a color, or `bg-` followed by a color.
   
   Colors are downsampled to the color level the terminal supports.
   At level 0, or when `style` produces no codes, `x` is returned
   unchanged.
   
   `x` is not sanitized. Escape codes in it reach the terminal as they
   are, so strip them first when `x` comes from untrusted input.
   
   The optional 3rd arg, `supports-color`, is a supports-color
   :context map (`{:env {...} :runtime {...}}`), for mocking in tests.
   Its entries override the live process reads, and anything it
   leaves out still comes from the real process. An env var missing
   from :env is read from the real environment, so a test that needs
   a fixed context sets every other key in
   `supports-color.core/env-var-keys` to nil. The color level and the
   mood are resolved from it for this call only. A mocked context
   never queries the real terminal, unless it supplies :runtime :tty
   itself.
   
   In browsers the level is always 3, and the mood follows
   prefers-color-scheme.
   
   An options map
   ```clojure
   (styled \"hello\" {:color :red, :underline true})
   ```
   
   A dot-delimited string or keyword
   ```clojure
   (styled \"hello\" \"bold.red.bg-blue\")
   
   (styled \"hello\" :bold.red.bg-blue)
   ```
   
   Hex, rgb and 8-bit colors
   ```clojure
   (styled \"hello\" {:color \"#ff5f00\"})
   
   (styled \"hello\" {:color [255 95 0]})
   
   (styled \"hello\" {:color 202})
   ```
   
   A mocked context, for tests: 256 colors on a dark background
   ```clojure
   (styled \"hello\" :red {:env {\"FORCE_COLOR\" \"2\", \"CLITHEME\" \"dark\"}})
   ```
   
   Options, when `style` is a map. A boolean option is on only when it is `true`:
   
   * **`:color`**
       - `[:or :string :keyword [:tuple :int :int :int] :int]`
       - Optional.
       - A palette name such as `:red` or `\"light-blue\"`, a hex string such as `\"#ff5f00\"`, an rgb vector such as `[255 95 0]`, or an 8-bit color id from 0 to 255. A plain name such as `:red` resolves to a variant that reads well on the terminal's dark or light background. The `user-` names, such as `:user-red`, use the terminal's own 16 colors. A `subtle-` name, such as `:subtle-blue`, is a low-contrast version of the color: the shadow variant on a dark background and the highlight variant on a light one. It is left out when the background is not detected. `:max` is white on a dark background and black on a light one. `:neutral` is the terminal's own text color.
   
   * **`:background-color`**
       - `[:or :string :keyword [:tuple :int :int :int] :int]`
       - Optional.
       - A color, in any form that `:color` accepts, resolved the same way. Use a `subtle-` name, such as `:subtle-blue`, for a low-contrast background behind the terminal's own text color. `:max` and `:neutral` leave the background unchanged.
   
   * **`:font-weight`**
       - `#{:bold :dim :normal}`
       - Optional.
   
   * **`:font-style`**
       - `#{:normal :italic}`
       - Optional.
   
   * **`:underline`**
       - `boolean?`
       - Optional.
   
   * **`:underline-style`**
       - `#{:double :wavy :straight :dashed :dotted}`
       - Optional.
       - Defaults to `:straight`.
       - Applies only when `:underline` is `true`.
   
   * **`:strikethrough`**
       - `boolean?`
       - Optional.
   
   * **`:reverse`**
       - `boolean?`
       - Optional.
       - Swaps the text and background colors.
   
   * **`:hide`**
       - `boolean?`
       - Optional.
       - Hides the text. It still takes up its space.
   
   * **`:blink`**
       - `boolean?`
       - Optional.
   
   * **`:validate-options`**
       - `boolean?`
       - Optional.
       - Defaults to `true`.
       - Prints a warning for each invalid value. Invalid values are dropped either way.
   
   * **`:print-warnings`**
       - `boolean?`
       - Optional.
       - Defaults to `true`.
       - Set to `false` to silence the warnings while validation is on. An unknown token in a string or keyword style always warns.
   
   * **`:dev/print-profile`**
       - `boolean?`
       - Optional.
       - Prints the resolved color level, polarity and palette variants."
  {:tldr     "Returns `x` wrapped in ANSI SGR escape codes for `style`."
   :desc     "`style` is an options map, or a dot-delimited string or keyword
              such as `\"bold.red.bg-blue\"` or `:bold.red.bg-blue`. In a string
              or keyword, each token is a font weight or style, the name of a
              boolean option, an underline style followed by `-underline`
              (`wavy-underline`), a color, or `bg-` followed by a color.

              Colors are downsampled to the color level the terminal supports.
              At level 0, or when `style` produces no codes, `x` is returned
              unchanged.

              `x` is not sanitized. Escape codes in it reach the terminal as they
              are, so strip them first when `x` comes from untrusted input.

              The optional 3rd arg, `supports-color`, is a supports-color
              :context map (`{:env {...} :runtime {...}}`), for mocking in tests.
              Its entries override the live process reads, and anything it
              leaves out still comes from the real process. An env var missing
              from :env is read from the real environment, so a test that needs
              a fixed context sets every other key in
              `supports-color.core/env-var-keys` to nil. The color level and the
              mood are resolved from it for this call only. A mocked context
              never queries the real terminal, unless it supplies :runtime :tty
              itself.

              In browsers the level is always 3, and the mood follows
              prefers-color-scheme."
   :examples [{:desc  "An options map"
               :forms '[[(styled "hello" {:color :red :underline true})]]}
              {:desc  "A dot-delimited string or keyword"
               :forms '[[(styled "hello" "bold.red.bg-blue")]
                        [(styled "hello" :bold.red.bg-blue)]]}
              {:desc  "Hex, rgb and 8-bit colors"
               :forms '[[(styled "hello" {:color "#ff5f00"})]
                        [(styled "hello" {:color [255 95 0]})]
                        [(styled "hello" {:color 202})]]}
              {:desc  "A mocked context, for tests: 256 colors on a dark background"
               :forms '[[(styled "hello" :red {:env {"FORCE_COLOR" "2"
                                                     "CLITHEME"    "dark"}})]]}]
   :options  [:map
              {:desc "Options, when `style` is a map. A boolean option is on only when it is `true`"}
              [:color
               {:desc     "A palette name such as `:red` or `\"light-blue\"`, a hex string such as `\"#ff5f00\"`, an rgb vector such as `[255 95 0]`, or an 8-bit color id from 0 to 255. A plain name such as `:red` resolves to a variant that reads well on the terminal's dark or light background. The `user-` names, such as `:user-red`, use the terminal's own 16 colors. A `subtle-` name, such as `:subtle-blue`, is a low-contrast version of the color: the shadow variant on a dark background and the highlight variant on a light one. It is left out when the background is not detected. `:max` is white on a dark background and black on a light one. `:neutral` is the terminal's own text color."
                :optional true}
               [:or :string :keyword [:tuple :int :int :int] :int]]
              [:background-color
               {:desc     "A color, in any form that `:color` accepts, resolved the same way. Use a `subtle-` name, such as `:subtle-blue`, for a low-contrast background behind the terminal's own text color. `:max` and `:neutral` leave the background unchanged."
                :optional true}
               [:or :string :keyword [:tuple :int :int :int] :int]]
              [:font-weight
               {:optional true}
               [:enum :bold :dim :normal]]
              [:font-style
               {:optional true}
               [:enum :italic :normal]]
              [:underline
               {:optional true}
               :boolean]
              [:underline-style
               {:desc     "Applies only when `:underline` is `true`."
                :optional true
                :default  :straight}
               [:enum :straight :double :wavy :dotted :dashed]]
              [:strikethrough
               {:optional true}
               :boolean]
              [:reverse
               {:desc     "Swaps the text and background colors."
                :optional true}
               :boolean]
              [:hide
               {:desc     "Hides the text. It still takes up its space."
                :optional true}
               :boolean]
              [:blink
               {:optional true}
               :boolean]
              [:validate-options
               {:desc     "Prints a warning for each invalid value. Invalid values are dropped either way."
                :optional true
                :default  true}
               :boolean]
              [:print-warnings
               {:desc     "Set to `false` to silence the warnings while validation is on. An unknown token in a string or keyword style always warns."
                :optional true
                :default  true}
               :boolean]
              [:dev/print-profile
               {:desc     "Prints the resolved color level, polarity and palette variants."
                :optional true}
               :boolean]]}
  ([x style]
   (styled x style nil))
  ([x style supports-color]
   (try
     (if-let [opening (opening-sgr style supports-color)]
       (str opening x "\033[m")
       x)
     (catch #?(:cljs :default :clj Throwable)
            e
       (report-caught! e style)
       x))))


(defn sgr
  "Returns the opening ANSI SGR escape sequence for `style`, without
   any text or reset.
   
   `style` and the optional 2nd arg, `supports-color`, are the same as
   the 2nd and 3rd args of `styled`. Use it to style text that is
   built in pieces, and end it with the SGR reset sequence, `ESC[m`.
   
   At level 0, or when `style` produces no codes, returns an empty
   string.
   
   An options map
   ```clojure
   (sgr {:color :red, :font-weight :bold})
   ```
   
   A dot-delimited string or keyword
   ```clojure
   (sgr :bold.red.bg-blue)
   ```"
  {:tldr     "Returns the opening ANSI SGR escape sequence for `style`, without
              any text or reset."
   :desc     "`style` and the optional 2nd arg, `supports-color`, are the same as
              the 2nd and 3rd args of `styled`. Use it to style text that is
              built in pieces, and end it with the SGR reset sequence, `ESC[m`.

              At level 0, or when `style` produces no codes, returns an empty
              string."
   :examples [{:desc  "An options map"
               :forms '[[(sgr {:color :red :font-weight :bold})]]}
              {:desc  "A dot-delimited string or keyword"
               :forms '[[(sgr :bold.red.bg-blue)]]}]}
  ([style]
   (sgr style nil))
  ([style supports-color]
   (try
     (or (opening-sgr style supports-color) "")
     (catch #?(:cljs :default :clj Throwable)
            e
       (report-caught! e style)
       ""))))


#?(:cljs
   (defn ^:public print-styled-for-browser-console
     "Prints `s` to the browser dev console, with its SGR escape codes
   converted to CSS.
   
   ClojureScript only. `s` is usually the result of `styled`. A value
   that is not a string prints as `print-str` shows it, as `println`
   does in a terminal.
   
   Print styled text in a browser
   ```clojure
   (print-styled-for-browser-console (styled \"hello\" :bold.red))
   ```"
     {:tldr     "Prints `s` to the browser dev console, with its SGR escape codes
                 converted to CSS."
      :desc     "ClojureScript only. `s` is usually the result of `styled`. A value
                 that is not a string prints as `print-str` shows it, as `println`
                 does in a terminal."
      :examples [{:desc  "Print styled text in a browser"
                  :forms '[[(print-styled-for-browser-console (styled "hello" :bold.red))]]}]}
     [s]
     (->> (if (string? s) s (print-str s))
          sugar.browser-dev-console/ansi-sgr-string->browser-dev-console-array
          (.apply js/console.log js/console))))

(defn print-styled
  "Prints `x` styled per `style`, followed by a newline. Returns nil.
   
   Takes the same args as `styled`. In a terminal, it is
   `(println (styled x style))`. In a browser, it prints to the dev
   console with the escape codes converted to CSS, through
   `print-styled-for-browser-console`.
   
   Print bold red text
   ```clojure
   (print-styled \"hello\" :bold.red)
   ```"
  {:tldr     "Prints `x` styled per `style`, followed by a newline. Returns nil."
   :desc     "Takes the same args as `styled`. In a terminal, it is
              `(println (styled x style))`. In a browser, it prints to the dev
              console with the escape codes converted to CSS, through
              `print-styled-for-browser-console`."
   :examples [{:desc  "Print bold red text"
               :forms '[[(print-styled "hello" :bold.red)]]}]}
  ([x style]
   (print-styled x style nil))
  ([x style supports-color]
   #?(:clj (println (styled x style supports-color))
      :cljs (if (= :browser (:host profile-ctx))
              (-> (styled x style supports-color) print-styled-for-browser-console)
              (println (styled x style supports-color))))))

;; Disabled for 0.1.0. Meant to return what a color resolves to in the current
;; context, as data, for programs that need the color value itself rather than
;; escape codes. The return shape is not settled yet.
#_(defn color
  "Returns a map representing the color value, given the detected context, and 
   additional info about the context.

   (color :red)
   =>
   {:rgb         [288 555 333]
    :hex         \"#00ff00\"
    :ansi256     61
    :color-level 3
    :theme       :dark}"
  ([color]
   (styled " " {:color color} nil))
  ([color supports-color]
   (styled " " {:color color} supports-color)))
