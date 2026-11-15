(ns tasks.clojars
  "Prints what Clojars knows about a library: the latest release, then the full
   artifact record from the Clojars API, converted from JSON to EDN. The
   library defaults to the `lib` defined in build.clj, so the namespace works
   unchanged in any repo with a tools.build build.clj."
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]
            [clojure.pprint :as pprint]))

(defn build-lib
  "The `lib` symbol from `(def lib '...)` in build.clj, or nil."
  ([] (build-lib "build.clj"))
  ([path]
   (some->> (slurp path)
            (re-find #"\(def\s+lib\s+'([^\s)]+)\)")
            second
            symbol)))

(defn artifact
  "The Clojars API record for lib as an EDN map, or nil when Clojars has no
   artifact by that name."
  [lib]
  (let [{:keys [status body]} (http/get (str "https://clojars.org/api/artifacts/" lib)
                                        {:headers {"Accept" "application/json"}
                                         :throw   false})]
    (when (and (= 200 status) (seq body))
      (json/parse-string body true))))

(defn info
  "Prints a header naming lib, its latest release on its own line, then the
   pretty-printed Clojars record."
  ([] (info (build-lib)))
  ([lib]
   (println (str "Clojars info for " lib))
   (if-let [record (artifact lib)]
     (do (println (str "Latest release: " (or (:latest_release record) "none")))
         (println (str "Latest version: " (:latest_version record)))
         (println)
         (pprint/pprint record))
     (println "Not on Clojars."))))
