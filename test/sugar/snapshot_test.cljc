(ns sugar.snapshot-test
  "Compares the output of every case in sugar.snapshot-cases with the stored
   output in sugar.snapshots. Regenerate the snapshots with `bb test:snapshot`."
  (:require #?(:clj  [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [clojure.string :as str]
            [sugar.snapshot-cases :as cases]
            [sugar.snapshots :refer [snapshots]]))

(defn- show
  "The snapshot entry as text, with ESC written as \\u001b so failures are
   readable in a terminal."
  [v]
  (str/replace (pr-str v) "\u001b" "\\u001b"))

(deftest case-ids-are-unique
  (let [ids (map :id cases/cases)]
    (is (= (count ids) (count (set ids))))))

(deftest matches-snapshots
  (doseq [{:keys [id] :as c} cases/cases]
    (testing id
      (if (contains? snapshots id)
        (is (= (show (get snapshots id))
               (show (cases/run c))))
        (is false (str "No snapshot for " (pr-str id) ". Run bb test:snapshot."))))))
