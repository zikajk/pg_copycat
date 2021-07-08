(ns pg-copycat.test-import
  (:require [pg-copycat.import :refer :all])
  (:use [clojure.test]))


(deftest import-mode-test
  (testing "Import-mode output..."
    (is (= ["-f" "/home/test/mockup.extra"]
           (import-mode :execute "/home/test/mockup.extra")))
    (is (= ["-c" "\\COPY mockup.extra FROM '/home/test/mockup.extra' USING DELIMITERS '|' CSV HEADER"]
           (import-mode :copy "/home/test/mockup.extra")))))


