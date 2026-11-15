(ns tasks.tuner
  "The palette tuner's local server, and the sync between
   dev/tuner/palette.json and the color maps in src/sugar/data.cljc.

   palette.json has one entry per color:
   {\"medium-cyan\": {\"truecolor\": [18, 199, 163], \"8bit\": 43}, ...}"
  (:require [cheshire.core :as json]
            [clojure.java.browse :refer [browse-url]]
            [clojure.string :as str]
            [org.httpkit.server :as http]))

(def ^:private index-file "dev/tuner/index.html")
(def ^:private palette-file "dev/tuner/palette.json")
(def ^:private data-file "src/sugar/data.cljc")
(def ^:private port 9631)

(def ^:private entry-re
  "One color entry line in a color map in data.cljc. Groups: the text before
   the value, the name, and the value."
  #"(?m)^(\s*\{?\"([a-z-]+)\"\s+)(\[[0-9 ]+\]|[0-9]+)")

(defn- map-bounds
  "The start and end index in s of the def named def-name, up to the next def."
  [s def-name]
  (let [start (str/index-of s (str "(def " def-name))
        end   (or (str/index-of s "(def " (inc start)) (count s))]
    [start end]))

(defn- map-entries
  "[name value-string] pairs of the color map def-name in s, in file order."
  [s def-name]
  (let [[start end] (map-bounds s def-name)]
    (mapv (fn [[_ _ k v]] [k v]) (re-seq entry-re (subs s start end)))))

(defn- color-names
  "The color names in data.cljc, in the order of colors-24-bit."
  []
  (mapv first (map-entries (slurp data-file) "colors-24-bit")))

(defn- byte? [x]
  (and (int? x) (<= 0 x 255)))

(defn- palette-error
  "Why palette (parsed palette.json) is invalid, or nil. It must have exactly
   the colors in data.cljc, each with a truecolor [r g b] and an 8bit index,
   all from 0 to 255."
  [palette names]
  (let [missing (remove #(contains? palette %) names)
        extra   (remove (set names) (keys palette))
        bad     (remove (fn [k]
                          (let [{tc "truecolor" x "8bit"} (get palette k)]
                            (and (vector? tc) (= 3 (count tc)) (every? byte? tc)
                                 (byte? x))))
                        (filter #(contains? palette %) names))]
    (cond
      (not (map? palette)) "palette.json must be a JSON object."
      (seq missing)        (str "Missing colors: " (str/join ", " missing))
      (seq extra)          (str "Unknown colors: " (str/join ", " extra))
      (seq bad)            (str "Invalid values for: " (str/join ", " bad)))))

(defn- palette->json
  "palette as JSON, one color per line, in the order of names."
  [palette names]
  (let [width (+ 3 (apply max (map count names)))]
    (str "{\n"
         (str/join ",\n"
                   (for [k names
                         :let [{tc "truecolor" x "8bit"} (get palette k)
                               key-str (str "\"" k "\":")]]
                     (str "  " key-str (apply str (repeat (- width (count key-str)) " "))
                          "{\"truecolor\": [" (str/join ", " tc) "], \"8bit\": " x "}")))
         "\n}\n")))

(defn- read-palette []
  (json/parse-string (slurp palette-file)))

(defn pull
  "Writes the color maps in data.cljc to palette.json."
  []
  (let [s       (slurp data-file)
        names   (color-names)
        tc      (into {} (map-entries s "colors-24-bit"))
        x256    (into {} (map-entries s "colors-8-bit"))
        palette (into {}
                      (for [k names]
                        [k {"truecolor" (mapv parse-long (re-seq #"\d+" (tc k)))
                            "8bit"      (parse-long (x256 k))}]))]
    (when-let [err (palette-error palette names)]
      (throw (ex-info (str data-file ": " err) {})))
    (spit palette-file (palette->json palette names))
    (println (str "Wrote " (count names) " colors to " palette-file))))

(defn push
  "Writes palette.json into the color maps in data.cljc. Only values change.
   Names, order, spacing and comments stay as they are."
  []
  (let [palette (read-palette)
        names   (color-names)]
    (when-let [err (palette-error palette names)]
      (throw (ex-info (str palette-file ": " err) {})))
    (let [s        (slurp data-file)
          values   (fn [s def-name value-str]
                     (let [[start end] (map-bounds s def-name)]
                       (str (subs s 0 start)
                            (str/replace (subs s start end)
                                         entry-re
                                         (fn [[_ before k _]]
                                           (str before (value-str (get palette k)))))
                            (subs s end))))
          s'       (-> s
                       (values "colors-24-bit" #(str "[" (str/join " " (% "truecolor")) "]"))
                       (values "colors-8-bit" #(str (% "8bit"))))]
      (if (= s s')
        (println (str data-file " already matches " palette-file))
        (do (spit data-file s')
            (println (str "Wrote " palette-file " to " data-file
                          ". Run bb test:snapshot and review the diff.")))))))

(def ^:private local-hosts
  #{(str "localhost:" port) (str "127.0.0.1:" port)})

(defn- foreign-request?
  "True when the request names a host other than this server, which a DNS
   rebinding page would, or is a POST from a page on another origin. Browsers
   send Origin with every POST, and a plain-text POST from any page needs no
   preflight, so without this any open tab could overwrite palette.json."
  [{:keys [request-method headers]}]
  (let [origin (get headers "origin")]
    (or (not (contains? local-hosts (get headers "host")))
        (and (= :post request-method)
             (some? origin)
             (not (contains? (set (map #(str "http://" %) local-hosts)) origin))))))

(defn- handler* [{:keys [request-method uri body]}]
  (case [request-method uri]
    [:get "/"]
    {:status  200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body    (slurp index-file)}

    [:get "/palette.json"]
    {:status  200
     :headers {"Content-Type" "application/json" "Cache-Control" "no-store"}
     :body    (slurp palette-file)}

    [:post "/palette.json"]
    (let [names   (color-names)
          palette (try (json/parse-string (slurp body))
                       (catch Exception _ ::unparseable))
          err     (if (= ::unparseable palette)
                    "The request body is not JSON."
                    (palette-error palette names))]
      (if err
        {:status 400 :headers {"Content-Type" "text/plain"} :body err}
        (do (spit palette-file (palette->json palette names))
            {:status 204})))

    {:status 404 :headers {"Content-Type" "text/plain"} :body "Not found"}))

(defn- handler [request]
  (if (foreign-request? request)
    {:status 403 :headers {"Content-Type" "text/plain"} :body "Forbidden"}
    (handler* request)))

(defn serve
  "Serves the tuner on localhost only, opens it in the browser, and blocks."
  []
  (http/run-server handler {:ip "127.0.0.1" :port port})
  (let [url (str "http://localhost:" port)]
    (println (str "Tuner at " url ". Save writes " palette-file ". Ctrl-C to stop."))
    (browse-url url))
  @(promise))
