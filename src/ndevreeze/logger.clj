(ns ndevreeze.logger
  "Simple logging functions.
  Similar to onelog, and also used clj-logging-config for
  inspiration. Now use a logger per *err* stream. *err* gets a new
  binding for each nRepl session, and so for each script that will run."
  (:require [clojure.string :as str]
            [java-time :as time]
            [me.raynes.fs :as fs])
  #_(:import [org.apache.log4j DailyRollingFileAppender EnhancedPatternLayout
              Level Logger WriterAppender])
  (:import [org.apache.logging.log4j LogManager Logger Level]
           [org.apache.logging.log4j.message Message]
           [org.apache.logging.log4j.core.appender ConsoleAppender ConsoleAppender$Target
            WriterAppender]
           [org.apache.logging.log4j.core.config Configurator]
           [org.apache.logging.log4j.core.config.builder.api AppenderComponentBuilder
            ComponentBuilder ConfigurationBuilder ConfigurationBuilderFactory
            LayoutComponentBuilder RootLoggerComponentBuilder]
           [org.apache.logging.log4j.core.config.builder.impl BuiltConfiguration]
           [org.apache.logging.log4j.core.layout PatternLayout]
           [java.io StringWriter PrintWriter])
  )




;; ./log4j-core/src/main/java/org/apache/logging/log4j/core/appender/WriterAppender.java

;; TODO - maybe also support other log-formats. But do want to keep it minimal.
(def log-format
  "Log format with timestamp, log-level, throwable and message"
  "[%d{yyyy-MM-dd HH:mm:ss.SSSZ}] [%-5p] %throwable%m%n")

(def loggers
  "Map of Logger objects, keyed by *err* streams"
  (atom {}))

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

(defn- ^Level as-level
  "Get Java Level value for clojure keyword.
  copied from https://github.com/malcolmsparks/clj-logging-config/blob/master/
  src/main/clojure/clj_logging_config/log4j.clj"
  [level]
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

(defmacro def-log-function
  "Macro to create a logging function for a level"
  [level]
  `(defn ~level
     ~(str "Log function for " level)
     [& forms#]
     (log ~(keyword level) forms#)))

(def-log-function trace)
(def-log-function debug)
(def-log-function info)
(def-log-function warn)
(def-log-function error)
(def-log-function fatal)

(defn- remove-all-appenders!
  "Simulate v1 method"
  [logger]
  (doseq [appender (vals (.getAppenders logger))]
    (.removeAppender logger appender)))

(defn close
  "Close the currently active logger and appenders.
   Connected to *err*"
  []
  (let [logger (get-logger *err*)]
    (remove-all-appenders! logger)
    (unregister-logger! *err*)))

(defn- get-config-builder!
  []
  (let [builder (ConfigurationBuilderFactory/newConfigurationBuilder)]
    (.setStatusLevel builder Level/DEBUG)
    (.setConfigurationName builder "DefaultLogger")
    builder))

(defn get-pattern-layout!
  [config-builder]
  (let [layout (.newLayout config-builder "PatternLayout")]
    (.addAttribute layout "pattern" log-format)
    layout))

;; DEFAULT_TARGET =  Target.SYSTEM_OUT;
(defn- get-console-appender-builder!
  [config-builder]
  (let [appender-builder (.newAppender config-builder "Console" "CONSOLE")
        layout (get-pattern-layout! config-builder)]
    (.addAttribute appender-builder "target" ConsoleAppender$Target/SYSTEM_OUT)
    (.add appender-builder layout)
    appender-builder))

(defn- get-error-appender-builder!
  [config-builder]
  (let [appender-builder (.newAppender config-builder "Error" "ERROR")
        layout (get-pattern-layout! config-builder)]
    (.addAttribute appender-builder "target" *err*)
    (.add appender-builder layout)
    appender-builder))

(defn- get-root-logger!
  [config-builder]
  (let [root-logger (.newRootLogger config-builder Level/DEBUG)]
    (.add root-logger (.newAppenderRef config-builder "Console"))
    root-logger))

;; for now no triggering Policy
(defn- rotating-appender
  "Returns a logging adapter that rotates the logfile nightly
  at about midnight."
  [config-builder root-logger logfile]
  (let [app-builder (.newAppender config-builder "LogToRollingFile" "RollingFile")]
    (.addAttribute app-builder "fileName" logfile)
    (.addAttribute app-builder "filePattern" (str logfile "-%d{MM-dd-yy-HH-mm-ss}.log."))
    (.add app-builder (get-pattern-layout! config-builder))
    ;;   rootLogger.add(builder.newAppenderRef("LogToRollingFile"));

    app-builder))

;; 2022-01-09: not sure if I need this init-your-logger and the functions it calls.
;; Configurator.reconfigure(builder.build());
(defn init-your-logger
  [filename pattern]
  (let [config-builder (get-config-builder!)
        appender-builder (get-console-appender-builder! config-builder)
        root-logger (get-root-logger! config-builder)
        rotating-builder (rotating-appender config-builder root-logger filename)]
    (.add config-builder appender-builder)
    (.add root-logger (.newAppenderRef config-builder "LogToRollingFile")) ;; logfile.
    (.add config-builder root-logger)
    (Configurator/reconfigure (.build config-builder))
    root-logger))

;; some test functions to learn how to use Log4j2, including builders, root-loggers and other loggers.
(defn log4j2-test
  []
  (init-your-logger "log4j2-test.log" log-format)
  (let [logger (LogManager/getLogger "Console")]
    (.debug logger "Hello from Log4j2"))

  8)


(defn- err-appender
  "Returns a logging adapter that logs to the console (stderr),
  connected to *err*"
  [config-builder]
  (get-error-appender-builder! config-builder))

;; old v1 version.
#_(defn- err-appender
    "Returns a logging adapter that logs to the console (stderr),
  connected to *err*"
    []
    (WriterAppender.
     (EnhancedPatternLayout. log-format)
     *err*))

;; old v1:
#_(defn- get-logger!
    "get log4j logger, so appenders can be set.
   based on name, create new one if it does not exist yet.
   Also register the logger in the atom loggers"
    [logger-name]
    (let [logger (Logger/getLogger logger-name)]
      (register-logger! *err* logger)
      logger))

(defn- get-logger!
  "get log4j logger, so appenders can be set.
   based on name, create new one if it does not exist yet.
   Also register the logger in the atom loggers"
  [logger-name]
  (let [logger (LogManager/getLogger logger-name)]
    (register-logger! *err* logger)
    logger))

(comment
  "Logger classes:

  /**
     * This method is not exposed through the public API and is used primarily for unit testing.
     *
     * @param appender The Appender to remove from the Logger.
     */
    public void removeAppender(final Appender appender) {
        privateConfig.loggerConfig.removeAppender(appender.getName());
    }

    /**
     * This method is not exposed through the public API and is used primarily for unit testing.
     *
     * @return A Map containing the Appender's name as the key and the Appender as the value.
     */
    public Map<String, Appender> getAppenders() {
        return privateConfig.loggerConfig.getAppenders();
    }
"
  )



;; rootLogger.add(builder.newAppenderRef("Console"));
(defn init-internal
  "Sets a default, appwide log adapter. Optional arguments set the
  default logfile and loglevel. If no logfile is provided, logs to
  stderr only.
  Should be able to handle multiple init calls.
  Return map with keys for logger created (or re-used) and logfile name"
  ([logfile loglevel]
   (let [config-builder (get-config-builder!)
         root-logger (get-root-logger! config-builder)
         logger (if logfile
                  (get-logger! logfile)
                  (get-logger! (str *err*)))]
     (.setLevel logger (as-level loglevel))
     #_(.removeAllAppenders logger)
     (remove-all-appenders! logger)
     ;; rootLogger.add(builder.newAppenderRef("LogToRollingFile"));
     ;; (.addAppender logger (.build (err-appender config-builder)))
     (.add root-logger (.newAppenderRef config-builder "Error"))
     #_(.add logger (.newAppenderRef config-builder "Error"))
     (when logfile
       #_(.addAppender logger (rotating-appender config-builder root-logger logfile))
       (rotating-appender config-builder root-logger logfile)
       (debug "Logging to:" logfile))
     (Configurator/reconfigure (.build config-builder))
     {:logger logger :logfile logfile}))
  ([logfile] (init-internal logfile :info))
  ([] (init-internal nil :info)))

(defn to-pattern
  "Convert location (pattern shortcut) to an actual pattern for a log file.
   Or if location is not a keyword (but a string), treat it as a directory"
  [location]
  (if (keyword? location)
    (get {:home   "%h/log/%n-%d.log"
          :cwd    "%c/log/%n-%d.log"
          :script "%s/log/%n-%d.log"
          :temp   "%t/log/%n-%d.log"} location)
    (str location "/%n-%d.log")))

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
  (str/replace pattern #"%([hcstnd])" (fn [[_ letter]]
                                        (replace-letter opts letter))))

(defn init-with-map
  "Initialises logging, possibly with a log-file.

  level     - log level like :info, :debug
  file      - give an explicit full path. Default is nil, use pattern
  pattern   - use a pattern for the log file path, see below
  location  - some pattern defaults/shortcuts, :home, :cwd, :script, :temp,
              default is nil
  name      - script name, \"script\" bij default
  cwd       - give an explicit current-working-dir, default is nil
  overwrite - boolean, overwrite an existing log file, default false

  if all of file, pattern and location are nil, do not create a
  logfile, just log to the console.

  Return map with keys for logger created and logfile name. logfile contains
  forward slashes only, even on Windows.

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
         overwrite false}}]
  (let [pattern (if location
                  (to-pattern location)
                  pattern)
        path (if file
               (-> file fs/expand-home fs/absolute str (str/replace "\\" "/"))
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
  location  - some pattern defaults/shortcuts, :home, :cwd, :script, :temp,
              Or a directory as string. default is nil
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

;;;;;;;;;;;;;;;;;;;;
;; Baelding test, everything below. If it works, we can merge
;;;;;;;;;;;;;;;;;;;;

;; maak dingen als layout compleet, voordat je ze aan appenders
;; toevoegt. En ook hierna pas appenders aan de builder toevoegen.
;; volgorde is idd belangrijk: de layouts worden gekopieerd naar de appenders.
(defn baeldung-test
  []
  ;; builder = ConfigurationBuilderFactory.newConfigurationBuilder();
  (let [builder (ConfigurationBuilderFactory/newConfigurationBuilder)
        console (.newAppender builder "stdout" "Console")
        file (.newAppender builder "log" "File")
        ;;        rolling (.newAppender builder "rolling" "RollingFile")
        standard (.newLayout builder "PatternLayout")
        root-logger (.newRootLogger builder Level/DEBUG) ;; was ERROR
        logger (.newLogger builder "com" Level/DEBUG)
        ]
    ;;    (.addAttribute rolling "fileName" "rolling.log")
    ;;    (.addAttribute rolling "filePattern" "rolling-%d{MM-dd-yy}.log.gz")
    ;;    (.add builder rolling)
    (.addAttribute standard "pattern" "%d [%t] %-5level: %msg%n%throwable")
    (.add console standard)
    (.add file standard)
    ;;    (.add rolling standard)

    (.add builder console)

    (.addAttribute file "fileName" "target/logging.log")
    (.add builder file)

    (.add root-logger (.newAppenderRef builder "stdout"))
    (.add builder root-logger)

    (.add logger (.newAppenderRef builder "log"))
    (.addAttribute logger "additivity" false)
    (.add builder logger)

    ;; builder.writeXmlConfiguration(System.out);
    (.writeXmlConfiguration builder System/out)

    ;; Configurator.initialize(builder.build());
    ;; vorige deed een reconfigure, voor beiden iets te zeggen.
    (Configurator/initialize (.build builder))

    #_(println "config after initialize:")
    #_(.writeXmlConfiguration builder System/out)

    (let [logger (LogManager/getLogger "Console")]
      (.error logger "Hello from Log4j2 with error"))

    (let [logger (LogManager/getLogger "log")]
      (.error logger "Hello from Log4j2 with getlogger log"))

    (let [logger (LogManager/getLogger "com")]
      (.error logger "Hello from Log4j2 with getlogger com"))

    (let [logger (LogManager/getLogger "bla")]
      (.error logger "Hello from Log4j2 with getlogger bla")
      (println "logger: " logger))

    ;; root-logger is a builder, cannot use.
    #_(.error root-logger "Hello from root-logger")


    )


  9)

;; orig, zoveel mogelijk de java volgorde.
#_(defn baeldung-test
    []
    ;; builder = ConfigurationBuilderFactory.newConfigurationBuilder();
    (let [builder (ConfigurationBuilderFactory/newConfigurationBuilder)
          console (.newAppender builder "stdout" "Console")
          file (.newAppender builder "log" "File")
          ;;        rolling (.newAppender builder "rolling" "RollingFile")
          standard (.newLayout builder "PatternLayout")
          root-logger (.newRootLogger builder Level/DEBUG) ;; was ERROR
          logger (.newLogger builder "com" Level/DEBUG)
          ]
      (.add builder console)
      (.addAttribute file "fileName" "target/logging.log")
      (.add builder file)
      ;;    (.addAttribute rolling "fileName" "rolling.log")
      ;;    (.addAttribute rolling "filePattern" "rolling-%d{MM-dd-yy}.log.gz")
      ;;    (.add builder rolling)
      (.addAttribute standard "pattern" "%d [%t] %-5level: %msg%n%throwable")
      (.add console standard)
      (.add file standard)
      ;;    (.add rolling standard)

      (.add root-logger (.newAppenderRef builder "stdout"))
      (.add builder root-logger)

      (.add logger (.newAppenderRef builder "log"))
      (.addAttribute logger "additivity" false)
      (.add builder logger)

      ;; builder.writeXmlConfiguration(System.out);
      (.writeXmlConfiguration builder System/out)

      ;; Configurator.initialize(builder.build());
      ;; vorige deed een reconfigure, voor beiden iets te zeggen.
      (Configurator/initialize (.build builder))

      (let [logger (LogManager/getLogger "Console")]
        (.error logger "Hello from Log4j2 with error"))

      (let [logger (LogManager/getLogger "log")]
        (.error logger "Hello from Log4j2 with getlogger log"))

      (let [logger (LogManager/getLogger "com")]
        (.error logger "Hello from Log4j2 with getlogger com"))

      (let [logger (LogManager/getLogger "bla")]
        (.error logger "Hello from Log4j2 with getlogger bla")
        (println "logger: " logger))

      ;; root-logger is a builder, cannot use.
      #_(.error root-logger "Hello from root-logger")


      )


    9)
