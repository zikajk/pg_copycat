#!/usr/bin/env bb

(require '[clojure.test :as t]
         '[babashka.classpath :as cp])

(cp/add-classpath "src:test")

(require 'pg-copycat.test-import 'pg-copycat.test-export)                  

(def test-results
  (t/run-tests 'pg-copycat.test-import 'pg-copycat.test-export))           

(def failures-and-errors
  (let [{:keys [:fail :error]} test-results]
    (+ fail error)))                                 

(System/exit failures-and-errors)                    



