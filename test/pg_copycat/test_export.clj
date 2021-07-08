(ns pg-copycat.test-export
  (:require [pg-copycat.export :refer :all])
  (:use [clojure.test]))

(deftest build-select-query-test
  (testing "build-select-query test..."
    (is (= ["SELECT * FROM 'ok'"]
           (build-select-query {:tbname "ok"})))
    (is (= ["SELECT * FROM 'ok' LIMIT 100"]
           (build-select-query {:tbname "ok" :limit 100})))))


