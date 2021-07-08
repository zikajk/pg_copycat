(ns pg-copycat.export
  (:require
   [pg-copycat.utils :as u ]
   [babashka.fs :as fs]
   [spartan.spec]
   [clojure.java.shell :as shell]
   [honey.sql :as hsql]
   [clojure.term.colors :as c]))

(def temp-folder
  (fs/temp-dir))

(defn- build-select-query
  "Takes <tbl> name, where hsql query and limit-no and builds query."
  [tb-opts]
  (let [tb (:tbname tb-opts)
        extra-opts (dissoc tb-opts :tbname)]
    (hsql/format
     (merge {:select [:*] :from [tb]}
            extra-opts)
     {:inline true})))

(defn- stdout-to-file!
  "Reads bash output and writes it to a file."
  [stdout dir filename]
  (if (and (empty? (:err stdout)) (zero? (:exit stdout)))
    (do (spit (fs/file dir filename) (:out stdout))
        (u/status-map filename (c/green "OK!") true))
    (u/status-map nil (c/red (:err stdout)) false)))
    
(defn- dump-structure!
  "Dump db's structure."
  [{:keys [host port dbname username]} folder]
  (println (format "Exporting %s structure..." dbname))
  (let [cmdout (-> (shell/sh "pg_dump"
                             "--clean"
                             "-h" host
                             "-d" dbname
                             "-U" username
                             "-p" (str port)
                             "-x" "-O" "-s")
                   (stdout-to-file! folder dbname))]
    (println (:message cmdout))))

(defmulti dump-data!
  "Dumps table-data from database configured in <ds-opts>.
  It takes <tbname>, hsql form of <where> and limit-no to build a query."
  (fn [db-opts _ _]
    (:dbtype db-opts)))

(defmethod dump-data! "postgresql"
  [db-opts tb-opts folder]
  (let [query (build-select-query tb-opts)
        {:keys [host port dbname username]} db-opts]
    (println (format "Exporting data from pg table : %s" (:tbname tb-opts)))
    (let [cmdout 
          (-> (shell/sh
               "psql"
               "-h" host
               "-d" dbname
               "-U" username
               "-p" (str port)
               "-c" (format "COPY (%s) TO STDOUT with (format csv, header, delimiter '|')" (first query)))
              (stdout-to-file! folder (name (:tbname tb-opts))))]
      (println (:message cmdout)))))

(defn create-structure-folder! [name]
  (u/touch-dir! temp-folder (format "structure_%s" name)))

(defn export-structure!
  "Exports complete pre-data.
   It creates one file prers existing schema."
  [db-opts]
  (let [folder (create-structure-folder! (:dbname db-opts)) 
        filename (u/zip-filename folder)]
    (dump-structure! db-opts folder)
    (u/zip-and-delete! folder filename)
    (u/move-file! filename (:gcs db-opts))))

(defn create-data-folder! [name dbname]
  (u/touch-dir! temp-folder
                "data"
                (format "dataset_%s_%s"
                        name dbname)))

(defn export-data!
  "Exports table data based on datasets' configurations."
  [db-opts]
  (doseq [ds-file (:dataset db-opts)]
    (println ds-file)
    (if (fs/exists? ds-file)
      (let [folder (create-data-folder! (u/filename-no-ext ds-file)
                                        (:dbname db-opts)) 
            dataset  (read-string (slurp ds-file))
            max-tb   (count dataset)
            filename (u/zip-filename folder)]
        (doseq [[idx item] (map-indexed vector dataset)]
          (println (format "%d/%d" (inc idx) max-tb))
          (dump-data! db-opts item folder))
        (u/zip-and-delete! folder filename)
        (u/move-file! filename (:gcs db-opts)))
      (println (c/red (format "%s file is missing!" ds-file))))))

