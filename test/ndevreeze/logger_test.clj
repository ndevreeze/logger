(ns ndevreeze.logger-test
    (:require [midje.sweet :as midje]
              [ndevreeze.logger :refer :all]))

#_(midje/facts
 "Test several facts"

 ;; 2020-12-19: a bit strange, with dummy as postfix and prefix.
 (midje/fact "Test dummy function"
             (logger-dummy) => "Dummy-logger")
 )

 
