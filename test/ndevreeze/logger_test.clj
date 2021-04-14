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
             => "[INFO ] Logging to: log1.out\n[INFO ] Log at info 1\n")

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
             => (str "[INFO ] Logging to: log2.out\n[INFO ] "
                     "Log at info 2\n[DEBUG] Log at debug 2\n"))

 ;; only to stdout/err, not to a file.
 (midje/fact "Test logger function only info, stdio"
             (do
               (log/init-internal nil :info)
               (log/info "Log at info 3")
               (log/debug "Log at debug 3")
               (log/close)
               12)
             => 12)

 (midje/fact "Test logger function incl debug, only stdio"
             (do
               (log/init-internal nil :debug)
               (log/info "Log at info 4")
               (log/debug "Log at debug 4")
               (log/close)
               13)
             => 13))
