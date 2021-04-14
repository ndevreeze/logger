(ns ndevreeze.logger
  (:require
   [java-time :as time]
   [clojure.string :as str]
   [me.raynes.fs :as fs])
  (:import [org.apache.log4j DailyRollingFileAppender
            EnhancedPatternLayout Level Logger WriterAppender]))

;; Similar to onelog, and also used clj-logging-config for
;; inspiration. Now use a logger per *err* stream. *err* gets a new
;; binding for each nRepl session, and so for each script run.

;; TODO - should use Log4j v2, now using v1.

;; TODO - maybe also support other log-formats. But do want to keep it minimal.
(def log-format "[%d{yyyy-MM-dd HH:mm:ss.SSSZ}] [%-5p] %throwable%m%n")

;; Map of Logger objects, keyed by *err* streams
(def loggers (atom {}))

(defn- register-logger!
  "Register a Logger by associating it with a (dynamic) stream like *err*"
  [stream logger]
  (swap! loggers assoc stream logger))

(defn- unregister-logger!
  "Unregister the Logger associated with the (dynamic) stream"
  [stream]
  (swap! loggers dissoc stream))

;; TODO - public for now, used by genie. Maybe need another solution.
(defn get-logger 
  "Get logger associated with (error) stream"
  [stream]
  (get @loggers stream))

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

;; TODO - maybe also forms that use a dynamic var *logger* in a binding form.

(defn log
  "log to the logger associated with the current *err* stream"
  ([logger level forms]
   (when logger
     (.log logger (as-level level) (str/join " " forms))))
  ([level forms]
   (log (get-logger *err*) level forms)))

(defn trace
  [& forms]
  (log :trace forms))

(defn debug
  [& forms]
  (log :debug forms))

(defn info
  [& forms]
  (log :info forms))

(defn warn
  [& forms]
  (log :warn forms))

(defn error
  [& forms]
  (log :error forms))

(defn fatal
  [& forms]
  (log :fatal forms))

(defn close
  "Close the currently active logger and appenders.
   Connected to *err*"
  []
  (let [logger (get-logger *err*)]
    (.removeAllAppenders logger)
    (unregister-logger! *err*)))

(defn- rotating-appender
  "Returns a logging adapter that rotates the logfile nightly at about midnight."
  [logfile]
  (DailyRollingFileAppender.
   (EnhancedPatternLayout. log-format)
   logfile
   ".yyyy-MM-dd"))

#_(defn- out-appender
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

#_(defn- console-appender
    "Returns a logging adapter that logs to the console (stderr)"
    []
    (ConsoleAppender.
     (EnhancedPatternLayout. log-format)))

(defn- get-logger!
  "get log4j logger, so appenders can be set.
   based on name, create new one if it does not exist yet.
   Also register the logger in the atom loggers"
  [name]
  (let [logger (Logger/getLogger name)]
    (register-logger! *err* logger)
    logger))

(defn init-internal
  "Sets a default, appwide log adapter. Optional arguments set the
  default logfile and loglevel. If no logfile is provided, logs to
  stderr only.
  Should be able to handle multiple init calls.
  Return logger created (or re-used)"
  ([logfile loglevel]
   (let [logger (if logfile
                  (get-logger! logfile)
                  (get-logger! (str *err*)))]
     (.setLevel logger (as-level loglevel))
     (.removeAllAppenders logger)
     (.addAppender logger (err-appender))
     (when logfile
       (.addAppender logger (rotating-appender logfile))
       (info "Logging to:" logfile))
     logger))
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
  [{:keys [cwd name]
    :or {name "script"}} letter]
  (case letter
    "h" (str (fs/home))
    "c" (str (fs/absolute (or (fs/expand-home cwd) ".")))
    "s" "TBD-script-dir"
    "t" (str (fs/tmpdir))
    "n" (or name "script-name")
    "d" (current-date-time)))

(defn to-log-file
  "Create a log file name based on given options and pattern"
  [opts pattern]
  (str/replace pattern #"%([hcstnd])" (fn [[_ letter]] (replace-letter opts letter))))

(defn init-with-map
  "Initialises logging, possibly with a log-file.

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
  [{:keys [level file pattern location overwrite] :as opts
    :or {level :info
         file nil
         pattern "%h/log/%n-%d.log"
         location nil
         overwrite false}    }]
  (let [pattern (if location
                  (to-pattern location)
                  pattern)
        path (if file
               (-> file fs/expand-home fs/absolute str)
               (to-log-file opts pattern))]
    (when overwrite
      (fs/delete path))
    (init-internal path level)))

(defn init
  "Initialises logging, possibly with a log-file.

  If called with:
  - 0 parameters - init with no logfile at level :info
  - 1 parameter that is a map - see below.
  - 1 parameter that is a string or file - logfile, level will be :info
  - 2 parameters - a logfile and a log level

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
  ([par1]
   (if (map? par1)
     (init-with-map par1)
     (init-internal par1 :info)))
  ([logfile loglevel] (init-internal logfile loglevel))
  ([] (init-internal nil :info)))

#_(defn init
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
