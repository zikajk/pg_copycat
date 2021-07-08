(ns pg-copycat.import
  (:require
   [pg-copycat.utils :as u]
   [clojure.java.shell :as shell]
   [babashka.fs :as fs]
   [clojure.term.colors :as c]))

(def temp-folder (fs/temp-dir))

(defn import-mode [mode filename]
  (case mode
    :execute ["-f" filename]
    :copy    ["-c" (format "\\COPY %s FROM '%s' USING DELIMITERS '|' CSV HEADER" (fs/file-name filename) filename)]))

(defn psql-cmd! [{:keys [host port dbname username]} filename mode]
  (let [[extra-par extra-cmd] (import-mode mode (str filename))
        stdout (shell/sh
                "psql"
                "-h" host
                "-d" dbname
                "-U" username
                "-p" (str port)
                extra-par extra-cmd)]
    (if (u/zero-exit? stdout)
      (u/status-map (:out stdout) (c/green (format "%s imported!" (fs/file-name filename))) true)
      (u/status-map (:out stdout) (c/red (:err stdout)) false))))

(defn import-folder! [folder db-opts mode]
  (let [i-files (map-indexed vector (fs/list-dir folder))]
    (for [[i f] i-files]
      (do (println (format "Importing %s ... %d / %d"
                           (fs/file-name f)
                           (inc i)
                           (count i-files)))
          (let [cmdout (psql-cmd! db-opts f mode)]
            (println (:message cmdout))
            cmdout)))))

(defn unzip-and-import! [cmdout folder db-opts mode-kw]
  (println (:message cmdout))
  (when (:ok? cmdout)
    (when (u/prompt)
      (u/unzip! (:out cmdout) folder)
      (doall
       (import-folder! folder db-opts mode-kw))
       (fs/delete-tree folder)
       (println (c/green "Import finished!")))))

(defn select-file! [gcs-uri gcs]
  (if (string? gcs)
    (u/download-from-bucket! gcs-uri)
    gcs-uri))

(defn get-file! [f-mask gcs]
  (let [files (u/list-files f-mask gcs)
        last-file (last (sort-by u/parse-timestamp files))]
    (println (format "Founded files : %s" files))
    (if-let [file (select-file! last-file gcs)]
      (u/status-map (fs/file-name file)
                    (c/green (format "Chosen file : %s" last-file))
                    true)
      (u/status-map nil
                    (c/red "Nothing has been found!")
                    false)))) 

(defn import-structure!
  "Imports complete pre-data.
  It created one schema per file"
  [opts]
  (let [searched (u/structure-f (:filename opts))
        cmdout (get-file! searched (:gcs opts))
        folder (u/touch-dir! temp-folder "structure")]
    (unzip-and-import! cmdout folder opts :execute)))

(defn import-data!
  "Imports table data based on dataset' configurations."
  [opts]
  (doseq [f (:dataset opts)]
    (let [searched (u/data-f f)
          cmdout (get-file! searched (:gcs opts))
          folder (u/touch-dir! temp-folder "dataset")]
      (unzip-and-import! cmdout folder opts :copy))))

