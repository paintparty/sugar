(ns sugar.data
  "Color palette definitions, style enums, and SGR code tables."
  (:require [clojure.string :as str]))

(def ^:public ^:no-doc font-style-enum
  #{"italic"
    "normal"})

(def ^:public ^:no-doc font-weight-enum
  #{"bold"
    "dim"
    "normal"})

(def ^:public ^:no-doc underline-style-enum
  #{"straight"
    "double"
    "wavy"
    "dotted"
    "dashed"})

(def ^:public ^:no-doc underline-style-for-tokenized-string-enum
  #{"straight-underline"
    "double-underline"
    "wavy-underline"
    "dotted-underline"
    "dashed-underline"})

(def base-colors
  "Bling pallette"
  ["red"
   "orange"
   "yellow"
   "olive"
   "green"
   "cyan"
   "blue"
   "purple"
   "magenta"
   "gray"
   "black"
   "white"])

(def base-user-colors
  "OG 16-color user pallette"
  ["user-red"
   "user-yellow"
   "user-green"
   "user-cyan"
   "user-blue"
   "user-magenta"
   "user-black"
   "user-white"
   "user-bright-red"
   "user-bright-yellow"
   "user-bright-green"
   "user-bright-cyan"
   "user-bright-blue"
   "user-bright-magenta"
   "user-bright-black"
   "user-bright-white"])

(def colors-24-bit
  "Bling pallette - 24-bit color variants optimized for unity and contrast"
  {"soft-red"            [215 105 116]
   "red"                 [255 0 0]
   "medium-red"          [255 0 0]
   "light-red"           [255 59 85]
   "dark-red"            [209 0 31]
   "extra-light-red"     [255 175 175]
   "highlight-red"       [255 236 235]
   "extra-dark-red"      [95 0 0]
   "shadow-red"          [64 0 0]

   "soft-orange"         [214 144 69]
   "orange"              [240 116 0]
   "medium-orange"       [215 135 0]
   "light-orange"        [255 175 0]
   "dark-orange"         [175 95 0]
   "extra-light-orange"  [255 175 135]
   "highlight-orange"    [255 244 212]
   "extra-dark-orange"   [107 70 0]
   "shadow-orange"       [59 44 0]

   "soft-yellow"         [175 175 0]
   "yellow"              [215 173 21]
   "medium-yellow"       [215 175 0]
   "light-yellow"        [215 215 0]
   "dark-yellow"         [150 133 0]
   "extra-light-yellow"  [255 255 175]
   "highlight-yellow"    [253 253 169]
   "extra-dark-yellow"   [95 95 0]
   "shadow-yellow"       [46 46 0]

   "soft-olive"          [175 175 135]
   "olive"               [135 175 0]
   "medium-olive"        [135 175 0]
   "light-olive"         [215 215 175]
   "dark-olive"          [114 122 0]
   "extra-light-olive"   [215 255 175]
   "highlight-olive"     [238 246 186]
   "extra-dark-olive"    [0 95 0]
   "shadow-olive"        [27 46 16]

   "soft-green"          [135 175 135]
   "green"               [29 215 9]
   "medium-green"        [0 215 0]
   "light-green"         [95 255 0]
   "dark-green"          [0 135 0]
   "extra-light-green"   [175 255 175]
   "highlight-green"     [223 255 220]
   "extra-dark-green"    [0 95 0]
   "shadow-green"        [0 46 0]

   "soft-cyan"           [79 176 163]
   "cyan"                [18 199 163]
   "medium-cyan"         [18 199 163]
   "light-cyan"          [0 232 190]
   "dark-cyan"           [0 126 130]
   "extra-light-cyan"    [175 255 255]
   "highlight-cyan"      [215 255 255]
   "extra-dark-cyan"     [0 95 95]
   "shadow-cyan"         [0 43 43]

   "soft-blue"           [135 175 215]
   "blue"                [20 138 255]
   "medium-blue"         [0 175 255]
   "light-blue"          [95 215 255]
   "dark-blue"           [0 95 215]
   "extra-light-blue"    [175 215 255]
   "highlight-blue"      [230 243 255]
   "extra-dark-blue"     [0 0 135]
   "shadow-blue"         [0 0 101]

   "soft-purple"         [158 124 217]
   "purple"              [192 105 255]
   "medium-purple"       [175 135 255]
   "light-purple"        [215 175 255]
   "dark-purple"         [159 0 199]
   "extra-light-purple"  [215 175 255]
   "highlight-purple"    [242 235 255]
   "extra-dark-purple"   [95 0 135]
   "shadow-purple"       [42 0 89]

   "soft-magenta"        [215 135 175]
   "magenta"             [255 71 240]
   "medium-magenta"      [255 0 255]
   "light-magenta"       [255 135 255]
   "dark-magenta"        [204 0 184]
   "extra-light-magenta" [255 175 255]
   "highlight-magenta"   [255 222 247]
   "extra-dark-magenta"  [95 0 95]
   "shadow-magenta"      [52 0 52]

   "gray"                [148 148 148]
   "medium-gray"         [158 158 158]
   "light-gray"          [198 198 198]
   "dark-gray"           [108 108 108]
   "extra-light-gray"    [245 245 245]
   "highlight-gray"      [242 242 242]
   "extra-dark-gray"     [68 68 68]
   "shadow-gray"         [46 46 46]

   "black"               [0 0 0]

   "white"               [255 255 255]})

(def colors-8-bit
  "Bling pallette - 8-bit color variants optimized for unity and contrast"
  {"soft-red"            173
   "red"                 196
   "medium-red"          196
   "light-red"           198
   "dark-red"            160
   "extra-light-red"     224
   "highlight-red"       224
   "extra-dark-red"      52
   "shadow-red"          52

   "soft-orange"         136
   "orange"              202
   "medium-orange"       172
   "light-orange"        214
   "dark-orange"         166
   "extra-light-orange"  223
   "highlight-orange"    223
   "extra-dark-orange"   94
   "shadow-orange"       94

   "soft-yellow"         142
   "yellow"              143
   "medium-yellow"       178
   "light-yellow"        184
   "dark-yellow"         136
   "extra-light-yellow"  229
   "highlight-yellow"    227
   "extra-dark-yellow"   58
   "shadow-yellow"       58

   "soft-olive"          144
   "olive"               106
   "medium-olive"        106
   "light-olive"         150
   "dark-olive"          100
   "extra-light-olive"   192
   "highlight-olive"     192
   "extra-dark-olive"    22
   "shadow-olive"        22

   "soft-green"          108
   "green"               40
   "medium-green"        40
   "light-green"         83
   "dark-green"          28
   "extra-light-green"   157
   "highlight-green"     194
   "extra-dark-green"    22
   "shadow-green"        22

   "soft-cyan"           73
   "cyan"                43
   "medium-cyan"         43
   "light-cyan"          86
   "dark-cyan"           28
   "extra-light-cyan"    159
   "highlight-cyan"      159
   "extra-dark-cyan"     23
   "shadow-cyan"         23

   "soft-blue"           110
   "blue"                39
   "medium-blue"         39
   "light-blue"          81
   "dark-blue"           26
   "extra-light-blue"    153
   "highlight-blue"      195
   "extra-dark-blue"     18
   "shadow-blue"         18

   "soft-purple"         146
   "purple"              141
   "medium-purple"       141
   "light-purple"        183
   "dark-purple"         92
   "extra-light-purple"  189
   "highlight-purple"    189
   "extra-dark-purple"   54
   "shadow-purple"       54

   "soft-magenta"        175
   "magenta"             168
   "medium-magenta"      201
   "light-magenta"       213
   "dark-magenta"        127
   "extra-light-magenta" 225
   "highlight-magenta"   225
   "extra-dark-magenta"  53
   "shadow-magenta"      52

   "gray"                246
   "medium-gray"         247
   "light-gray"          251
   "dark-gray"           242
   "extra-light-gray"    253
   "highlight-gray"      255
   "extra-dark-gray"     238
   "shadow-gray"         234

   "black"               16

   "white"               231})

(def underline-style-codes-by-style
  {"straight" 1
   "double"   2
   "wavy"     3
   "dotted"   4
   "dashed"   5})

(def user-colors-background
  {"user-black-bg"          40
   "user-red-bg"            41
   "user-green-bg"          42
   "user-yellow-bg"         43
   "user-blue-bg"           44
   "user-magenta-bg"        45
   "user-cyan-bg"           46
   "user-white-bg"          47
   "user-bright-black-bg"   100
   "user-bright-red-bg"     101
   "user-bright-green-bg"   102
   "user-bright-yellow-bg"  103
   "user-bright-blue-bg"    104
   "user-bright-magenta-bg" 105
   "user-bright-cyan-bg"    106
   "user-bright-white-bg"   107})

(def user-colors-foreground
  {"user-black"            30
   "user-red"              31
   "user-green"            32
   "user-yellow"           33
   "user-blue"             34
   "user-magenta"          35
   "user-cyan"             36
   "user-white"            37
   "user-bright-black"     90
   "user-bright-red"       91
   "user-bright-green"     92
   "user-bright-yellow"    93
   "user-bright-blue"      94
   "user-bright-magenta"   95
   "user-bright-cyan"      96
   "user-bright-white"     97})

(def base-colors-re (re-pattern (str/join "|" base-colors)))

(def base-colors-set (into #{} base-colors))

(def all-user-colors (into #{} base-user-colors))

(def subtle-colors
  "A low-contrast version of each base color: shadow- on a dark background,
   highlight- on a light one. black and white have none."
  (into #{}
        (map #(str "subtle-" %))
        (remove #{"black" "white"} base-colors)))

(def all-colors
  (into #{}
        (concat base-colors
                base-user-colors
                subtle-colors
                (keys colors-24-bit) ; <- 24 and 8 bit names are same
                ["max"               ; <- black or white, if "mood" present
                 "neutral"]          ; <- resolves to user foreground
                )))

