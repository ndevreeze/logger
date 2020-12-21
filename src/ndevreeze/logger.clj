(ns ndevreeze.logger
  (:require [clojure.tools.cli :as cli]
            [me.raynes.fs :as fs]
            [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.data.csv :as csv]
            [clojure.edn :as edn]
            [java-time :as time]
            [clojure.tools.logging :as log])
  
  (:import [org.apache.log4j ConsoleAppender DailyRollingFileAppender EnhancedPatternLayout
            Level Logger]))

;; similar to onelog, also used clj-logging-config for inspiration.
;; TODO - should use Log4j v2, now using v1.

(def production-log-prefix-format "[%d{yyyy-MM-dd HH:mm:ss.SSSZ}] [%-5p] %throwable%m%n")

;; copied from https://github.com/malcolmsparks/clj-logging-config/blob/master/src/main/clojure/clj_logging_config/log4j.clj
(defn- ^Level as-level [level]
  (cond
   (nil? level) nil
   (= :inherit level) nil
   (keyword? level) (get {:all Level/ALL
                          :debug Level/DEBUG
                          :error Level/ERROR
                          :fatal Level/FATAL
                          :info Level/INFO
                          :off Level/OFF
                          :trace Level/TRACE
                          :warn Level/WARN} level)
   (instance? Level level) level))

(defn- rotating-appender
  "Returns a logging adapter that rotates the logfile nightly at about midnight."
  [logfile]
  (DailyRollingFileAppender.
   (EnhancedPatternLayout. production-log-prefix-format)
   logfile
   ".yyyy-MM-dd"))

;; TODO - log to stderr, or set as option. Can be done in the constructor of ConsoleAppender
;; have .getTarget, but not .setTarget, needs to be set at construction time.
(defn- console-appender
  "Returns a logging adapter that logs to the console (stderr)"
  []
  (ConsoleAppender.
   (EnhancedPatternLayout. production-log-prefix-format)))

(defn- get-root-logger
  "get root log4j logger, so 2 appenders can be set"
  []
  (Logger/getRootLogger))

;; 2020-12-20: inspired by https://github.com/pjlegato/onelog/blob/master/src/onelog/core.clj
(defn init
  "Sets a default, appwide log adapter. Optional arguments set the
  default logfile and loglevel. If no logfile is provided, logs to
  stdout only."
  ([logfile loglevel]
   (let [root (get-root-logger)]
     (.setLevel root (as-level loglevel))
     (.addAppender root (console-appender))
     (if logfile
       (.addAppender root (rotating-appender logfile)))))
  ([logfile] (init logfile :info))
  ([] (init nil :info)))

(defn trace 
  [& forms]
  (log/trace (apply str forms)))

(defn debug
  [& forms]
  (log/debug (apply str forms)))

(defn info
  [& forms]
  (log/info (apply str forms)))

;; TODO - maybe add colours again, but only to console, not to the file.
(defn warn
  [& forms] 
  (log/warn (apply str forms)))

(defn error
  [& forms] 
  (log/error (apply str forms)))

(defn fatal
  [& forms] 
  (log/fatal (apply str forms)))


