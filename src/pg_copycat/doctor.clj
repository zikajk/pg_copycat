(ns pg-copycat.doctor
  (:require
   [pg-copycat.utils :refer [gcloud-auth?]]
   [clojure.java.shell :as shell]
   [clojure.term.colors :as c]
   [babashka.fs :as fs]))


(def prerequisities
  [["gcloud"  "To communicate with the bucket where you may want to download from or upload files to."]
   ["docker"  "Should be installed if you want to run a local database."]
   ["psql"    "Used when exporting data from tables. It is also used when importing data."]
   ["pg_dump" "Used to export pre-data and post-data."]
   ["zip"     "To pack files during export."]
   ["unzip"   "The unpack files during import."]])

(defn where-installed?
  "Checks if you have installed <command>"
  [command]
  (let [out (shell/sh "bash" "-c"
                      (str  "type " command))]
    out))

(defn print-status
  ([cmd-name info]
   (println (c/red (format "%s has not been found!" cmd-name))
            (c/white info))
   (newline))
  ([cmd-name info out]
   (println (c/green (format "%s has been found in: %s" cmd-name out))
            (c/white info))
   (newline)))

(defn check-installed
  "Checks if you have installed psql and pg_dump.
  It also checks db connection before any action."
  []
  (println (c/blue "<-@-@-@-@-@-@-@-@-@-@-@-@-@-@->"))
  (println (c/on-grey (c/bold "Checking installed software:")))
  (doseq [[cmd-name info] prerequisities]
    (let [cmd (where-installed? cmd-name)]
      (if (zero? (:exit cmd))
        (print-status cmd-name info (:out cmd))
        (print-status cmd-name info))))
  (println (c/on-grey (c/bold "Checking other prerequisites:")))
  (if (fs/exists? (fs/file (System/getProperty "user.home") ".pgpass"))
    (println (c/green ".pgpass has been found. It's passwords might be used."))
    (println (c/red ".pgpass has not been found! You should create it and configure access to your databases.")))
  (if (gcloud-auth?)
    (println (c/green "You appear to be logged to GCLOUD CLI Tools."))
    (println (c/red "Do 'gcloud auth login' to access Google Cloud Storage.")))
  (c/blue "<-@-@-@-@-@-@-@-@-@-@-@-@-@-@->"))
  
