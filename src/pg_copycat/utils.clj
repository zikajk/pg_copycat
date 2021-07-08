(ns pg-copycat.utils
  (:require [clojure.java.shell :as shell]
            [clojure.string :as string] 
            [babashka.fs :as fs]
            [clojure.term.colors :as c]))


(defn zero-exit? [m]
  (cond
    (zero? (:exit m)) true
    :else             false))

(defn gcloud-auth? []
  (zero-exit? (shell/sh "gcloud" "auth" "print-identity-token")))

(defn valid-bucket? [options]
  (if-let [gcs-uri (:gcs options)]
    (zero-exit? (shell/sh "gsutil" "ls" gcs-uri))
    true))

(defmulti connected?
  (fn [ds-opts]
    (:dbtype ds-opts)))

(defmethod connected? "postgresql" [ds-opts]
  (let [{:keys [host port dbname username]} ds-opts]
    (zero-exit? (shell/sh "pg_isready" "-d" dbname "-h" host "-p" (str port) "-U" username))))

(defn touch-dir!
  "Checks if the path exists.
  If not, creates a path based on <folders>."
  [main-folder & folders]
  (let [path (apply fs/file (cons main-folder folders))]
    (when-not (fs/exists? path) (fs/create-dirs path))
    path))

(defn filename-no-ext
  "Removes directories and extension from filename."
  [file]
  (-> file fs/file-name fs/split-ext first))


(defn parse-timestamp [s]
  (-> (fs/file-name s)
      (string/split #"_")
      last
      fs/split-ext
      first))

(defn valid-file? [file prefix]
  (and (string/starts-with? file prefix)
       (string/ends-with? file ".zip")))

(defn list-files [f-mask gcs]
  (if (string? gcs)
    (string/split (:out (shell/sh "gsutil" "ls" (format "%s/%s*.zip" gcs f-mask))) #"\n")
    (filterv #(valid-file? % f-mask) (map fs/file-name (fs/list-dir ".")))))

(defn prompt []
  (println "Do you want to continue [y/n]?")
  (= (read-line) "y"))

(defn status-map [output message ok?]
  {:out output
   :message message
   :ok? ok?})

(defn move-to-bucket [filename gs-uri]
 (shell/sh
  "gsutil" "-m"
  "mv" filename
  (format "%s/%s" gs-uri filename)))

(defn move-file! [filename gcs]
  (if gcs
    (move-to-bucket filename gcs)
    (fs/move filename (System/getProperty "user.dir"))))

(defn zip-filename [folder]
  (str (format "%s_%d.zip" (fs/file-name folder)
               (quot (System/currentTimeMillis) 1000))))

(defn- zip-folder!
  [folder filename]
  (shell/sh "zip" "-j"
            "-r" filename
            (str folder)))

(defn zip-and-delete!
  "Zips and then deleted folder on path."
  [folder filename]
  (zip-folder! folder filename)
  (fs/delete-tree folder))

(defn unzip!
  [filename target-folder]
  (shell/sh "unzip" (str filename)
            "-d" (str target-folder)))

(defn complete-filemask [filename prefix]
  (if (string/starts-with? filename prefix)
    filename
    (format "%s_%s" prefix filename)))

(defn structure-f [f]
  (if f (complete-filemask f "structure")
      "structure"))

(defn data-f [f]
  (complete-filemask f "dataset"))

(defn download-from-bucket! [gcs-uri]
  (when (seq gcs-uri)
    (println (c/green (format "Downloading : %s" gcs-uri)))
    (shell/sh "gsutil" "-m" "cp" gcs-uri (fs/file-name gcs-uri))
    (fs/file-name gcs-uri)))
