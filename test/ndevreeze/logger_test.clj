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
             (remove-timestamps "[2020-12-21 18:45:22.361+0100] [INFO ] info")
             => "[INFO ] info")

 (midje/fact "Test remove-timestamps multiline"
             (remove-timestamps (str "[2020-12-21 18:45:22.361+0100]"
                                     " [INFO ] Log at info\n[2020-12-21 "
                                     "18:45:22.361+0100] [DEBUG] Log at debug"))
             => "[INFO ] Log at info\n[DEBUG] Log at debug")

 (midje/fact "Test logger function only info, also to file"
             (let [logfile "log1.out"]
               (fs/delete logfile)
               (log/init-internal logfile :info)
               (log/info "Log at info 1")
               (log/debug "Log at debug 1")
               (log/close)
               (-> logfile
                   slurp
                   remove-timestamps))
             => "[INFO ] Log at info 1\n")

 (midje/fact "Test logger function incl debug, also to file"
             (let [logfile "log2.out"]
               (fs/delete logfile)
               (log/init-internal logfile :debug)
               (log/info "Log at info 2")
               (log/debug "Log at debug 2")
               (log/close)
               (-> logfile
                   slurp
                   remove-timestamps))
             => (str "[DEBUG] Logging to: log2.out\n[INFO ] "
                     "Log at info 2\n[DEBUG] Log at debug 2\n"))

 ;; only to stdout/err, not to a file.
 ;; and mainly to test we do not get an Exception.
 (midje/fact "Test logger function only info, stdio"
             (do
               (log/init-internal nil :info)
               (log/info "Log at info 3")
               (log/debug "Log at debug 3")
               (log/close)
               12)
             => 12)

 ;; also only checking for Exceptions.
 (midje/fact "Test logger function incl debug, only stdio"
             (do
               (log/init-internal nil :debug)
               (log/info "Log at info 4")
               (log/debug "Log at debug 4")
               (log/close)
               13)
             => 13)

 (midje/fact "Test to-pattern"
             (log/to-pattern :home)
             => "%h/log/%n-%d.log")

 (midje/fact "Test to-pattern dir"
             (log/to-pattern "/tmp")
             => "/tmp/%n-%d.log")

 )

(midje/facts
 "Learn using log4j2"

 #_(midje/fact "Log to stdout"
               (log/log4j2-test)
               => 8)

 #_(midje/fact "Bealdung test to file"
               (log/baeldung-test)
               => 9)

 ;; 2022-01-15: even zonder deze, focus op de echte testcases.
 #_(midje/fact "Widd test to file"
               (log/widd-test)
               => 10)



 )
