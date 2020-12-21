(ns ndevreeze.logger-test
    (:require [midje.sweet :as midje]
              [ndevreeze.logger :as log]))

(midje/facts
 "Test logger"

 ;; 2020-12-19: a bit strange, with dummy as postfix and prefix.
 (midje/fact "Test logger function"
             (do
               (log/init "log.out" :info)
               (log/info "Log at info")
               (slurp "log.out"))
             => "Log at info")
 )

 
