(ns sugar.nbb-runner
  "nbb entrypoint for `bb test:nbb`. Runs the same test namespaces as
   `bb test`.

   sugar.property-test is not run here. test.check calls
   `bit-shift-right-zero-fill`, which nbb does not define.

   Under nbb, `run-tests` returns nil and the summary arrives through the
   `:end-run-tests` report method, so the exit code is set there."
  (:require [cljs.test :as t]
            [sugar.behavior-test]
            [sugar.regression-test]
            [sugar.snapshot-test]
            [sugar.styled-test]))

(defmethod t/report [:cljs.test/default :end-run-tests] [m]
  (js/process.exit (if (t/successful? m) 0 1)))

(t/run-tests 'sugar.styled-test
             'sugar.snapshot-test
             'sugar.regression-test
             'sugar.behavior-test)
