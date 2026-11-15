(ns sugar.bench-criterium
  "Runs the sugar.bench scenarios on the JVM with criterium's quick-benchmark.
   Run with FORCE_COLOR and CLITHEME set, as for sugar.bench."
  (:require [criterium.core :as crit]
            [sugar.bench :as bench]))


(defn -main [& _]
  (println (str "\nJVM " (System/getProperty "java.version") ", level " (bench/level)
                ", criterium quick-benchmark mean and lower quantile (2.5%)"))
  (println (str (format "%-36s" "scenario") (format "%-12s" "mean") "lower q"))
  (doseq [[scenario-name f] bench/scenarios]
    (let [result (crit/quick-benchmark* f {})]
      (bench/row scenario-name
                 (* 1e9 (first (:mean result)))
                 (* 1e9 (first (:lower-q result))))))
  (shutdown-agents))
