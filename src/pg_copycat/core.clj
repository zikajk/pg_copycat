(ns pg-copycat.core
  (:gen-class)
  (:require
   [clojure.string :refer [join]]
   [pg-copycat.doctor :refer [check-installed]]
   [pg-copycat.export :as export]
   [pg-copycat.import :as import]
   [clojure.term.colors :as c]
   [pg-copycat.utils :refer [connected? valid-bucket?]]
   [clojure.tools.cli :refer [parse-opts]]
   [babashka.fs :as fs]))

(def LOGO
  "+------------------------+\n|   ____  ______  ___    |\n|  /    )/      \\/   \\   |\n| (     / __    _\\    )  |\n|  \\    (/ x)  ( x)   )  |\n|   \\_  (_  )   \\ )  /   |\n|     \\  /\\_/    \\)_/    |\n|      \\/  //|  |\\\\      |\n|      /   v |  | v      |\n|     /     \\__/    \\    |\n|    /               \\   |\n|   |     (^^\\/^^)    |  |\n|   |      \\^  ^/     |  |\n|   |       \\^^/      |  |   \n|   |        \\/       |  | \n|    \\_______________/   |\n|    pg_copycat   0.0.1  |\n|                        |\n+------------------------+\n")

(def cli-options
  [["-c"           "--config-path CONFIG-PATH}" "A path to EDN config file containing a database configuration."]
   [ "-d"           "--dataset DATASET" "Specifies the dataset to be imported or exported. Can be used more than once."
    :multi         true
    :default       []
    :update-fn     conj]
   ["-g"           "--gcs GCS" "The name of GCS you will use for import or export."]
   ["-f"           "--filename FILENAME" "Search for a file during import - use a partion or an exact filename."
    :default       nil]
   ["-H"           "--help" "Print this help information"]
   [nil             "--version" "Print version and exit"]])

(defn usage [options-summary]
  (->> [(c/cyan LOGO) 
        ""
        "Usage: pg_copycat [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  doctor              Check installed software."
        "  export-structure    Export DB structure to a timestamped ZIP file,"
        "  export-data         Export dataset(s)' to ZIP file(s)."
        "  export-all          Run export-structure and export-data."
        "  import-structure    Import structure ZIP file."
        "  import-data         Import dataset(s)' ZIP file(s)."
        "  import-all          Run import-structure and import-data."
        ""
        "Please refer to the manual page for more information."]
       (join \newline)))



(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (join \newline errors)))

(defn validate-action [action]
  (#{"export-structure" "export-data" "export-all"
     "import-structure" "import-data" "import-all"}
   action))

(defn validate-args [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}
      errors
      {:exit-message (error-msg errors)}
      (= "doctor" (first arguments))
      {:exit-message (check-installed) :ok? true}
      :else
      (if-let [config-file (:config-path options)]
        (if (fs/exists? config-file)
          (let [options (merge options (read-string (slurp config-file)))]
            (cond
              (not (connected? options))
              {:exit-message (format "Cannot connect to database [%s]" (:dbname options))}
              (not (valid-bucket? options))
              {:exit-message (format "Cannot access %s" (:gcs options))}
              (and (= 1 (count arguments)) (validate-action (first arguments)))
              {:action (first arguments) :options options}
              :else
              {:exit-message "You are missing a correct action argument"}))
          {:exit-message (format "%s does not seems like a valid config file!" (:config-path options))})
        {:exit-message (usage summary)}))))

(defn exit [status msg]
  (println msg)
  (println "Exiting...")
  (System/exit status))

(defn -main [& args]
  (let [{:keys [action options exit-message ok?]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case action
        "export-structure"  (export/export-structure!       options)
        "export-data"       (export/export-data!            options) 
        "export-all"        (do (export/export-structure!   options)
                                (export/export-data!        options))
        "import-structure"  (import/import-structure!       options)
        "import-data"       (import/import-data!            options)
        "import-all"        (do (import/import-structure!   options)
                                (import/import-data!        options))))))




