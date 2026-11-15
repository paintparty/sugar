(ns sugar.sandbox
  (:require [sugar.core :refer [styled]]
            [supports-color.core]
            [sugar.visual :as visual]))

#_(supports-color.core/detect-profile {:debug? true})

;; (println (styled "foo" {:color :red :underline true :underline-style :wavy} #_{"FORCE_COLOR" 1}))

#_(println (str
          "\033[38;2;255;255;175m"
          "\033[48;2;215;175;0m"
          "foos"
          "\033[m"))

#_(println (styled "foo"
                   {:background-color :medium-yellow 
                    :color            :extra-light-yellow
                    :dev/print-profile true
                    }
                   ))
;; Shadow red
#_(doseq [c ["shadow-red" #_[233 255 66] #_"#f2af50"]]
  (println (styled "foo"
                   {:background-color c 
                    :color            :medium-red 
                    ;; :dev/print-profile true
                    } 
                   {:env {"FORCE_COLOR" 1}}))
  (println (styled "foo"
                   {:background-color c 
                    :color            :medium-red 
                    ;; :dev/print-profile true
                    } 
                   {:env {"FORCE_COLOR" 2}}))
  (println (styled "foo"

                   {:background-color c 
                    :color            :medium-red
                    ;; :dev/print-profile true
                    }
                   {:env {"FORCE_COLOR" 3}})))


(def mock-supports-color
  {:env {"FORCE_COLOR" 3
         "COLOR_THEME" "dark"}})



#_(visual/run "jvm" "palette" "warnings")


