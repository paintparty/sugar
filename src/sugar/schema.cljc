(ns sugar.schema
  (:require [supports-color.common]
            [sugar.utils]
            [sugar.data]))

(def ^:private Color
  "Malli schema for a color value accepted by styled."
  [:or
   [:enum "neutral" "max"]
   [:and :string [:re sugar.utils/hex-color-re]]
   [:and :string [:fn #(contains? sugar.data/all-colors %)]]
   [:and :keyword [:fn #(contains? sugar.data/all-colors (name %))]]
   [:tuple [:int {:min 0 :max 255}]
    [:int {:min 0 :max 255}]
    [:int {:min 0 :max 255}]]
   [:int {:min 0 :max 255}]])

(def ^:private StyledOptions
  "Valid Malli schema for the options map accepted by styled."
  [:map {:closed false}
   [:color            {:optional true} Color]
   [:background-color {:optional true} Color]
   [:font-style       {:optional true} [:enum "italic" "normal" :italic :normal]]
   [:font-weight      {:optional true} [:enum "bold" "dim" "normal" :bold :dim :normal]]
   [:underline        {:optional true} :boolean]
   [:underline-style  {:optional true} [:enum
                                        "straight"
                                        "double"
                                        "wavy"
                                        "dotted"
                                        "dashed"
                                        :straight
                                        :double
                                        :wavy
                                        :dotted
                                        :dashed]]
   [:strikethrough    {:optional true} :boolean]
   [:reverse          {:optional true} :boolean]
   [:hide             {:optional true} :boolean]
   [:blink            {:optional true} :boolean]])

(def ^:private SupportsColorContext
  "A supports-color :context map, used in place of the live process reads."
  [:map {:closed false}
   [:env     {:optional true} supports-color.common/MockEnv]
   [:runtime {:optional true} :map]])

(def StyledSchema
  "Valid Malli schema for the args vec of sugar.core/styled."
  [:cat
   :any
   [:or [:nilable StyledOptions] :string]
   [:? [:nilable SupportsColorContext]]])
