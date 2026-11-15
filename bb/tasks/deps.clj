(ns tasks.deps
  "Switches supports-color between the local checkout and the published
   version, in deps.edn, bb.edn, nbb.edn and shadow-cljs.edn. Each file holds
   both, and #_ discards the one not in use. The task only moves the #_, so the
   path and version are whatever the files say."
  (:require [clojure.string :as str]))

(def ^:private deps-files ["deps.edn" "bb.edn" "nbb.edn"])

;; In deps.edn, bb.edn and nbb.edn: the key, the local coordinate, then the
;; published one.
(def ^:private entry-re
  #"(io\.github\.paintparty/supports-color\s+)(?:#_)?(\{:local/root[^}]*\})(\s+)(?:#_)?(\{:mvn/version[^}]*\})")

;; In shadow-cljs.edn: the checkout's src in :source-paths, and the published
;; version in :dependencies.
(def ^:private shadow-file "shadow-cljs.edn")
(def ^:private shadow-source-path-re #"(?:#_)?(\"[^\"]*supports-color/src\")")
(def ^:private shadow-dep-re #"(?:#_)?(\[io\.github\.paintparty/supports-color \"[^\"]*\"\])")

(defn- discard-if [discard? form]
  (str (when discard? "#_") form))

(defn- replace-once
  "s with the single match of re replaced by (f match). Throws if re matches
   anything other than once."
  [file s re f]
  (let [n (count (re-seq re s))]
    (when-not (= 1 n)
      (throw (ex-info (str file ": expected 1 supports-color entry matching " re ", found " n) {})))
    (str/replace s re f)))

(defn- set-entry! [file target]
  (let [local? (= target :local)]
    (spit file
          (replace-once file (slurp file) entry-re
                        (fn [[_ before local-coord ws published-coord]]
                          (str before
                               (discard-if (not local?) local-coord)
                               ws
                               (discard-if local? published-coord)))))))

(defn- set-shadow! [target]
  (let [local? (= target :local)]
    (spit shadow-file
          (-> (slurp shadow-file)
              (as-> s (replace-once shadow-file s shadow-source-path-re
                                    (fn [[_ form]] (discard-if (not local?) form))))
              (as-> s (replace-once shadow-file s shadow-dep-re
                                    (fn [[_ form]] (discard-if local? form))))))))

(defn- active-coord
  "The supports-color coordinate deps.edn uses, as a string."
  []
  (let [s                                 (slurp "deps.edn")
        [_ _ local-coord _ published-coord] (re-find entry-re s)]
    (if (str/includes? s (str "#_" local-coord)) published-coord local-coord)))

(def ^:private files (conj deps-files shadow-file))

(defn switch
  "Points all 4 files at target, :local or :published. Files already there are
   left as they are."
  [target]
  (let [before (mapv slurp files)]
    (doseq [f deps-files]
      (set-entry! f target))
    (set-shadow! target)
    (println (if (= before (mapv slurp files))
               (str "supports-color is already " (active-coord) ". Nothing changed.")
               (str "supports-color is now " (active-coord) ", in " (str/join ", " files) ".")))))
