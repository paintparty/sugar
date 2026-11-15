(ns tasks.visual
  "Runs the sugar.visual tasks on Babashka, or on nbb, Node, Babashka and the
   JVM, in that order."
  (:require [babashka.tasks :refer [clojure shell]]))

(defn run
  "Prints each named visual task (palette, samples, warnings, platforms) on
   Babashka."
  [& tasks]
  (apply (requiring-resolve 'sugar.visual/run) "bb" tasks))

(defn run-all-platforms
  "Prints each named visual task on every platform. Each platform starts once
   for all the tasks."
  [& tasks]
  (apply shell "npx" "--yes" "nbb" "-cp" "src:test" "-m" "sugar.visual" "nbb" tasks)
  ;; The build log would land between the nbb and Node output, so it prints
  ;; only when the build fails.
  (let [{:keys [exit out err]} (shell {:out :string :err :string :continue true}
                                      "npx" "shadow-cljs" "compile" "visual")]
    (when-not (zero? exit)
      (binding [*out* *err*] (println out err))
      (throw (ex-info "shadow-cljs compile visual failed" {:exit exit}))))
  (apply shell "node" "out/visual/visual.js" "node" tasks)
  (apply run tasks)
  (apply clojure "-M:test" "-m" "sugar.visual" "jvm" tasks))
