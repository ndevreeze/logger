(ns ndevreeze.logger-test
  (:require [midje.sweet :as midje]
            [clojure.string :as str]
            [me.raynes.fs :as fs]
            [ndevreeze.logger :as log]))

(defn remove-timestamps
  [text]
  (str/replace text #"(^|\n)\[[^\]]+\] " (fn [[_ x]] (str x))))

(midje/facts
 "Test logger"

 (midje/fact "Test remove-timestamps"
             (remove-timestamps "[2020-12-21 18:45:22.361+0100] [INFO ] Log at info")
             => "[INFO ] Log at info")

 (midje/fact "Test remove-timestamps multiline"
             (remove-timestamps "[2020-12-21 18:45:22.361+0100] [INFO ] Log at info\n[2020-12-21 18:45:22.361+0100] [DEBUG] Log at debug")
             => "[INFO ] Log at info\n[DEBUG] Log at debug")
  
 ;; 2020-12-19: a bit strange, with dummy as postfix and prefix.
 (midje/fact "Test logger function only info"
             (do
               (fs/delete "log.out")
               (log/init-internal "log.out" :info)
               (log/info "Log at info 1")
               (log/debug "Log at debug 1")
               (-> "log.out"
                   slurp
                   remove-timestamps))
             => "[INFO ] Logging to: log.out\n[INFO ] Log at info 1\n")

 (midje/fact "Test logger function incl debug"
             (do
               (fs/delete "log.out")
               (log/init-internal "log.out" :debug)
               (log/info "Log at info 2")
               (log/debug "Log at debug 2")
               (-> "log.out"
                   slurp
                   remove-timestamps))
             => "[INFO ] Logging to: log.out\n[INFO ] Log at info 2\n[DEBUG] Log at debug 2\n")
 )

 
