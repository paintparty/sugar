(ns tasks.deps
  "Switches supports-color between the local checkout and the published
   version, in deps.edn, bb.edn, nbb.edn and shadow-cljs.edn."
  (:require [clojure.string :as str]))

(def ^:private local-root "../../cljc/supports-color")
(def ^:private version "0.1.0-SNAPSHOT")

(def ^:private local-coord (str "{:local/root \"" local-root "\"}"))
(def ^:private published-coord (str "{:mvn/version \"" version "\"}"))

(def ^:private deps-files ["deps.edn" "bb.edn" "nbb.edn"])

;; In deps.edn, bb.edn and nbb.edn: the key and its coordinate map.
(def ^:private coord-re
  #"(io\.github\.paintparty/supports-color\s+)\{[^}]*\}")

;; In shadow-cljs.edn both entries are present, and #_ discards one.
(def ^:private shadow-file "shadow-cljs.edn")
(def ^:private shadow-source-path (str "\"" local-root "/src\""))
(def ^:private shadow-dep (str "[io.github.paintparty/supports-color \"" version "\"]"))

(defn- set-coord! [file coord]
  (let [s  (slurp file)
        s' (str/replace s coord-re (fn [[_ before]] (str before coord)))]
    (when (= s s')
      (when-not (str/includes? s coord)
        (throw (ex-info (str file ": no supports-color entry found") {}))))
    (spit file s')))

(defn- discard
  "s with #_ before the first occurrence of form, if it has none."
  [s form]
  (if (str/includes? s (str "#_" form)) s (str/replace-first s form (str "#_" form))))

(defn- keep-form
  "s without #_ before form."
  [s form]
  (str/replace s (str "#_" form) form))

(defn- set-shadow! [target]
  (let [s  (slurp shadow-file)
        _  (doseq [form [shadow-source-path shadow-dep]]
             (when-not (str/includes? s form)
               (throw (ex-info (str shadow-file ": " form " not found") {}))))
        s' (if (= target :local)
             (-> s (keep-form shadow-source-path) (discard shadow-dep))
             (-> s (discard shadow-source-path) (keep-form shadow-dep)))]
    (spit shadow-file s')))

(def ^:private files (conj deps-files shadow-file))

(defn switch
  "Points all 4 files at target, :local or :published. Files already there are
   left as they are."
  [target]
  (let [before (mapv slurp files)]
    (doseq [f deps-files]
      (set-coord! f (if (= target :local) local-coord published-coord)))
    (set-shadow! target)
    (let [where (if (= target :local)
                  (str "the local checkout, " local-root)
                  (str "the published version, " version))]
      (println (if (= before (mapv slurp files))
                 (str "supports-color is already " where ". Nothing changed.")
                 (str "supports-color is now " where ", in "
                      (str/join ", " files) "."))))))
