(ns sugar.browser
  (:require [sugar.samples :as samples]))

(defn ^:dev/after-load init []
  (samples/visual-test-suite))

(init)
