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
 ;; 2020-12-31: start using *out*. Some issues with reusing or closing the *out* stream, so just one test for now, check in cljsh.
 (midje/fact "Test logger function only info, also to file"
             (let [logfile "log1.out"]
               (fs/delete logfile)
               (log/init-internal logfile :info)
               (log/info "Log at info 1")
               (log/debug "Log at debug 1")
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
               (-> logfile
                   slurp
                   remove-timestamps))
             => "[INFO ] Logging to: log2.out\n[INFO ] Log at info 2\n[DEBUG] Log at debug 2\n")

 ;; alleen naar stdout/err, niet naar file, wat gaat er dan goed/fout?
 (midje/fact "Test logger function only info, stdio"
             (do
               (log/init-internal nil :info)
               (log/info "Log at info 3")
               (log/debug "Log at debug 3")
               12)
             => 12)

 (midje/fact "Test logger function incl debug, only stdio"
             (do
               (log/init-internal nil :debug)
               (log/info "Log at info 4")
               (log/debug "Log at debug 4")
               13)
             => 13)
 
 
 )




;; testje met functie met 1 of 2 params
(defn testje
  "1 of 2 params"
  ([a b]
   (println "a,b=" a b))
  ([a]
   (testje a 0)))
