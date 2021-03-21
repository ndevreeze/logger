(ns ndevreeze.logger
  (:require [clojure.tools.logging :as log]
            [java-time :as time]
            [clojure.string :as str]
            [me.raynes.fs :as fs])
  (:import [org.apache.log4j ConsoleAppender DailyRollingFileAppender
            EnhancedPatternLayout Level Logger WriterAppender]))

;; similar to onelog, also used clj-logging-config for inspiration.
;; TODO - should use Log4j v2, now using v1.
;; TODO - support multiple (root?) loggers, when running in
;;        server-mode, and two scripts run at the same time (log4j v2?).
;;        This is still untested, although we do support serial calls now.

;; close log-file? Maybe needed in cljsh if we run multiple scripts?
;; in cljsh de *err* and *out* streams are created and closed for each client session. We use these streams for our logging, namely de *err* stream.

;; TODO - maybe also support other log-formats. But do want to keep it minimal.
(def log-format "[%d{yyyy-MM-dd HH:mm:ss.SSSZ}] [%-5p] %throwable%m%n")

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

(defn trace 
  [& forms]
  (log/trace (apply str forms)))

(defn debug
  [& forms]
  (log/debug (apply str forms)))

(defn info
  [& forms]
  (log/info (apply str forms)))

;; TODO - maybe add colours again (as in onelog), but only to console, not to the file.
(defn warn
  [& forms] 
  (log/warn (apply str forms)))

(defn error
  [& forms] 
  (log/error (apply str forms)))

(defn fatal
  [& forms] 
  (log/fatal (apply str forms)))

(defn- rotating-appender
  "Returns a logging adapter that rotates the logfile nightly at about midnight."
  [logfile]
  (DailyRollingFileAppender.
   (EnhancedPatternLayout. log-format)
   logfile
   ".yyyy-MM-dd"))

(defn- out-appender
  "Returns a logging adapter that logs to the console (stderr), connected to *out*"
  []
  (WriterAppender.
   (EnhancedPatternLayout. log-format)
   *out*))

(defn- err-appender
  "Returns a logging adapter that logs to the console (stderr), connected to *err*"
  []
  (WriterAppender.
   (EnhancedPatternLayout. log-format)
   *err*))

(defn- console-appender
  "Returns a logging adapter that logs to the console (stderr)"
  []
  (ConsoleAppender.
   (EnhancedPatternLayout. log-format)))

(defn- get-root-logger
  "get root log4j logger, so appenders can be set"
  []
  (Logger/getRootLogger))

;; 2020-12-20: inspired by https://github.com/pjlegato/onelog/blob/master/src/onelog/core.clj
;; TODO - do we always need the root-logger? Because if multiple script are running in the cljsh-server,
;; we need to separate them, separate contexts.

;; 2020-12-31: need a different structure, with usecases:
;; - remove all file appenders from a category.
;; - add an appender to a category, given type, name, and constructor function.
;; so need a nested structure. [cat type] is the main key. value is another map, with key=name, value=appender.
(defonce appenders (atom {}))

(defn maybe-add-appender!
  "Add an appender to the category iff it's not already added.
   Maybe also only create appender if needed, give a constructor-function"
  [category app-type name appender-fn]
  (when-not (get-in @appenders [[category app-type] name])
    (let [appender (appender-fn)]
      (swap! appenders assoc-in [[category app-type] name] appender)
      (.addAppender category appender))))

(defn remove-file-appenders!
  "Remove all file appenders, but keep out/err stream appenders"
  [category]
  (doseq [[_ app] (get @appenders [category :file])]
    (.removeAppender category app))
  (swap! appenders assoc [category :file] {}))

(defn get-appenders
  "Return the atom/struct with all appenders, for debugging"
  []
  @appenders)

(defn init-internal
  "Sets a default, appwide log adapter. Optional arguments set the
  default logfile and loglevel. If no logfile is provided, logs to
  stdout only.
  Should be able to handle multiple init calls."
  ([logfile loglevel]
   (let [root (get-root-logger)]
     (.setLevel root (as-level loglevel))
     (remove-file-appenders! root)
     (maybe-add-appender! root :stdio *err* err-appender)
     (if logfile
       (maybe-add-appender! root :file logfile (fn [] (rotating-appender logfile))))
     (when logfile
       (log/info "Logging to:" logfile))))
  ([logfile] (init-internal logfile :info))
  ([] (init-internal nil :info)))

(defn to-pattern
  "Convert pattern shortcut to an actual pattern for a log file"
  [pattern]
  (get {:home   "%h/log/%n-%d.log"
        :cwd    "%c/log/%n-%d.log"
        :script "%s/log/%n-%d.log" 
        :temp   "%t/log/%n-%d.log"} pattern))

(defn current-date-time
  "Return current date and time in format yyyy-mm-ddTHH-MM-SS, based on
  current timezone"
  []
  (let [fmt "yyyy-MM-dd'T'HH-mm-ss"
        inst (time/instant)
        tz (time/zone-id)
        odt (time/offset-date-time inst tz)]
    (time/format fmt odt)))

(defn replace-letter
  "Replace %h pattern etc with the actual values"
  [{:keys [cwd name] :as opts} letter]
  (case letter
    "h" (str (fs/home))
    "c" (str (fs/absolute (or (fs/expand-home cwd) ".")))
    "s" "TBD-script-dir"
    "t" (str (fs/tmpdir))
    "n" (or name "script-name")
    "d" (current-date-time)))

(defn to-log-file
  "Create a log file name based on given options and pattern"
  [{:keys [cwd name] :as opts} pattern]
  (str/replace pattern #"%([hcstnd])" (fn [[_ letter]] (replace-letter opts letter))))

(defn init
  "Initialises a log file.

  level     - log level like :info, :debug
  file      - give an explicit full path. Default is nil, use pattern
  pattern   - use a pattern for the log file path, see below
  location  - some pattern defaults/shortcuts, :home, :cwd, :script, :temp, default is nil
  name      - script name, \"script\" bij default
  cwd       - give an explicit current-working-dir, default is nil
  overwrite - boolean, overwrite an existing log file, default false

  if all of file, pattern and location are nil, do not create a
  logfile, just log to the console.
  
  To use in pattern:
   - %h = home dir
   - %c = current dir
   - %s = script dir (TBD)
   - %t = temp dir (/tmp, or c:/tmp)
   - %n = script name
   - %d = datetime, as yyyy-mm-ddTHH-MM-SS"
  [{:keys [level file pattern location name cwd overwrite] :as opts
    :or {level :info
         file nil
         pattern "%h/log/%n-%d.log"
         location nil
         name "script"
         cwd nil
         overwrite false}}]
  (let [pattern (if location
                  (to-pattern location)
                  pattern)
        path (if file
               (-> file fs/expand-home fs/absolute str)
               (to-log-file opts pattern))]
    (if overwrite
      (fs/delete path))
    (init-internal path level)))
