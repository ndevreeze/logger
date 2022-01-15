(ns ndevreeze.logger
  "Simple logging functions.
  Similar to onelog, and also used clj-logging-config for
  inspiration. Now use a logger per *err* stream. *err* gets a new
  binding for each nRepl session, and so for each script that will run."
  (:require [clojure.string :as str]
            [java-time :as time]
            [com.widdindustries.log4j2.log-api :as log] ; TODO: rename to api or widd.
            [com.widdindustries.log4j2.log-impl :as log-impl] ; TODO: rename to api or widd.
            [com.widdindustries.log4j2.config :as config]

            [me.raynes.fs :as fs])
  (:import [org.apache.logging.log4j LogManager Logger Level]
           ;;         [org.apache.logging.log4j.message Message]
           [org.apache.logging.log4j.core.appender ConsoleAppender$Target
            WriterAppender FileAppender]
           [org.apache.logging.log4j.core LoggerContext Appender]
           [org.apache.logging.log4j.core.config Configurator]
           [org.apache.logging.log4j.core.config.builder.api AppenderComponentBuilder
            ComponentBuilder ConfigurationBuilder ConfigurationBuilderFactory
            LayoutComponentBuilder RootLoggerComponentBuilder]
           ;;           [org.apache.logging.log4j.core.config.builder.impl BuiltConfiguration]
           [org.apache.logging.log4j.core.layout PatternLayout]
           )
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
  (println "register-logger! with stream, logger:" stream "," logger)
  (swap! loggers assoc stream logger))

(defn- unregister-logger!
  "Unregister the Logger associated with the (dynamic) stream"
  [stream]
  (println "unregister-logger! with stream:" stream)
  (swap! loggers dissoc stream))

;; TODO - public for now, used by genie. Maybe need another solution.
(defn get-logger
  "Get logger associated with (error) stream"
  [stream]
  (println "get-logger with stream:" stream)
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
;; TODO: the str/join forms causes space between each letter of string in some cases (log4j2).
(defn log
  "log to the logger associated with the current *err* stream"
  ([logger level forms]
   (println "fn log called for logger, level and forms: " logger level forms)
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

;; TODO: define with doseq on level?
(def-log-function trace)
(def-log-function debug)
(def-log-function info)
(def-log-function warn)
(def-log-function error)
(def-log-function fatal)

;; move to debug namespace? Or testing-namespace?
(defn print-loggers-appenders
  "Print loggers and appenders from current context to stdout"
  [title]
  (println "=================")
  (println title)
  (doseq [l (config/get-loggers (log-impl/context))]
    (println "logger: " l)
    (doseq [[name app] (.getAppenders l)]
      (println "-> name, app: " name ", " app)))
  (println "================="))

(defn- remove-all-appenders!
  "Simulate v1 method"
  [logger]
  (println "remove-all-appenders! for: " logger)
  (doseq [appender (vals (.getAppenders logger))]
    (println "Removing appender:" appender)
    (.stop appender)
    (.removeAppender logger appender)))

(defn close
  "Close the currently active logger and appenders.
   Connected to *err*"
  []
  (print-loggers-appenders "Before closing current logger for *err*")
  (let [logger (get-logger *err*)]
    (println "close logger: " logger)
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



(defn init-system!
  "Initialize logging system for log4j2.
  Called once when loading namespace"
  []
  (let [builder (config/builder)]
    (.add builder (-> (.newRootLogger builder Level/OFF)))
    (let [context (config/start builder)]
      (.writeXmlConfiguration builder System/out)
      (println)
      context)))

(init-system!)


(defn- get-logger!
  "get log4j logger, so appenders can be set.
   based on name, create new one if it does not exist yet.
   Also register the logger in the atom loggers"
  [logger-name]
  (let [logger (LogManager/getLogger logger-name)]
    (register-logger! *err* logger)
    logger))

;; changed a bit, directly giving level.
(defn set-level
  "FYI the root logger name is the empty string. or you can refer to it via LogManager/ROOT_LOGGER_NAME"
  [logger-name level]
  (let [ctx ^org.apache.logging.log4j.core.LoggerContext (log-impl/context)]
    (-> ctx
        (.getConfiguration)
        (.getLoggerConfig (str logger-name))
        (.setLevel level))
    (.updateLoggers ctx)))

;; maybe need root-logger as parent.  also need level, normally
;; INFO. But see no method to set this. Maybe while getting the logger
(defn make-logger
  "Dynamically create a logger, with a builder.
   Normally called after initial config is done."
  [^String name]
  (let [logger (LogManager/getLogger ^String name)]
    (println "make-logger busy, created (before .setLevel): " logger)
    (.setLevel logger Level/INFO)
    (.setAdditive logger false)
    (set-level name Level/INFO)
    (println "make-logger done, created: " logger)
    (println " with level: " (.getLevel logger))
    (println "Level/INFO: " Level/INFO)
    logger))

(defn make-logger2
  "Dynamically create a logger, with a builder.
   Normally called after initial config is done."
  [^String name ^Level level]
  (let [logger (LogManager/getLogger ^String name)]
    (println "make-logger busy, created (before .setLevel): " logger)
    (.setLevel logger level)
    (.setAdditive logger false)
    (set-level name level)
    (println "make-logger done, created: " logger)
    (println " with level: " (.getLevel logger))
    (println "Level/INFO: " Level/INFO)
    logger))

(defn make-layout
  "Dynamically create a Layout for a FileAppender, with a builder.
   But not with a config"
  [pattern]
  (-> (PatternLayout/newBuilder)
      (.withPattern pattern)
      (.build)))

(defn make-file-appender
  "Dynamically create a file appender, with a builder.
   Normally called after initial config is done."
  [name filename]
  (println "make-file-appender with name, filename:" name "," filename)
  (-> (FileAppender/newBuilder)
      (.setName name)
      (.withFileName filename)
      (.withLayout (make-layout log-format))
      (.build)))

(defn make-writer-appender
  "Mostly for *err* streams.
   Name is needed for init."
  [name writer]
  (println "make-writer-appender with name, writer:" name "," writer)
  (-> (WriterAppender/newBuilder)
      (.setName name)
      (.setTarget writer)
      (.withLayout (make-layout log-format))
      (.build)))

;; rootLogger.add(builder.newAppenderRef("Console"));
;; (str *err*) -> "java.io.PrintWriter@1c7d2933", so should be unique.
;; maybe need to remove previous appenders.
(defn init-internal
  "Sets a default, appwide log adapter. Optional arguments set the
  default logfile and loglevel. If no logfile is provided, logs to
  stderr only.
  Should be able to handle multiple init calls.
  Return map with keys for logger created (or re-used) and logfile name.
  Also register-logger!, so it can be deregistered when done."
  ([logfile loglevel]
   (let [logger (make-logger2 ^String (str *err*) (as-level loglevel))
         app (when logfile (make-file-appender (str "file:" *err*) logfile))
         err-app (make-writer-appender (str "err:" *err*) *err*)
         ctx (log-impl/context)
         cfg (.getConfiguration ctx)]
     (.start err-app)
     (println "Started err-app: " err-app)
     (.addLoggerAppender cfg logger err-app)
     (when app
       (.start app)
       (println "Started app: " app)
       (.addAppender cfg app)
       (.addAppender logger app))
     (.updateLoggers ctx)
     (print-loggers-appenders "At end of init-internal")
     (register-logger! *err* logger)
     (debug "Logging to:" logfile)
     {:logger logger :logfile logfile}))
  ([logfile] (init-internal logfile :info))
  ([] (init-internal nil :info)))

#_(defn init-internal
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
         (debug "Logging to:" logfile)         )
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
#_(defn baeldung-test
    []
    ;; builder = ConfigurationBuilderFactory.newConfigurationBuilder();
    (let [builder (ConfigurationBuilderFactory/newConfigurationBuilder)
          standard (.newLayout builder "PatternLayout")
          console (.newAppender builder "stdout" "Console")
          file (.newAppender builder "log" "File")
          triggering-policies (.newComponent builder "Policies")
          cron-trig-policy (.newComponent builder "CronTriggeringPolicy")
          size-trig-policy (.newComponent builder "SizeBasedTriggeringPolicy")
          rolling (.newAppender builder "rolling" "RollingFile")

          root-logger (.newRootLogger builder Level/DEBUG) ;; was ERROR
          logger (.newLogger builder "com" Level/DEBUG)
          ]
      (.addAttribute standard "pattern" "%d [%t] %-5level: %msg%n%throwable")
      (.add console standard)
      (.add file standard)
      (.add rolling standard)

      (.add builder console)

      (.addAttribute file "fileName" "target/logging.log")
      (.add builder file)

      (.addAttribute rolling "fileName" "rolling.log")
      (.addAttribute rolling "filePattern" "rolling-%d{MM-dd-yy}.log.gz")

      (.addAttribute cron-trig-policy "schedule" "0 0 0 * * ?")
      (.addAttribute size-trig-policy "size" "100M")
      (.addComponent triggering-policies cron-trig-policy)
      (.addComponent triggering-policies size-trig-policy)
      (.addComponent rolling triggering-policies)
      (.add builder rolling)

      (.add root-logger (.newAppenderRef builder "stdout"))
      (.add builder root-logger)

      (.add logger (.newAppenderRef builder "log"))
      (.addAttribute logger "additivity" false)
      (.add builder logger)

      ;; builder.writeXmlConfiguration(System.out);
      (.writeXmlConfiguration builder System/out)

      ;; Configurator.initialize(builder.build());
      ;; vorige deed een reconfigure, voor beiden iets te zeggen.
      (let [logger-context (Configurator/initialize (.build builder))]

        #_(println "config after initialize:")
        #_(.writeXmlConfiguration builder System/out)

        (let [logger2 (LogManager/getLogger "Console")]
          (.info logger2 "Hello from Log4j2 with info"))

        (let [logger2 (LogManager/getLogger "log")]
          (.info logger2 "Hello from Log4j2 with getlogger log"))

        (let [logger2 (LogManager/getLogger "com")]
          (.info logger2 "Hello from Log4j2 with getlogger com"))

        ;; the config logger is different, cannot use here.
        ;; (.info logger "Hello from Log4j2 with config logger")

        (let [logger2 (LogManager/getLogger "bla")]
          (.info logger2 "Hello from Log4j2 with getlogger bla")
          (println "logger: " logger2))



        ;; create a new logger to a new file after these first logs.
        ;; log something that only goes to this new file.
        (let [file2 (.newAppender builder "log2" "File")
              logger2 (.newLogger builder "com2" Level/INFO)]
          (.add file2 standard)
          (.addAttribute file2 "fileName" "target/logging2.log")
          (.add builder file2)
          (.add logger2 (.newAppenderRef builder "log2"))
          (.addAttribute logger2 "additivity" false)
          (.add builder logger2)

          ;; builder.writeXmlConfiguration(System.out);
          (.writeXmlConfiguration builder System/out)

          ;; Configurator.initialize(builder.build());
          ;; vorige deed een reconfigure, voor beiden iets te zeggen.
          ;; 2022-01-10: check of dit init moet worden, of reconfig.
          ;; 2022-01-10: lijkt geen initialize te zijn, dan com2 gewoon naar Console, en niet naar file.
          #_(Configurator/initialize (.build builder))
          (Configurator/reconfigure)
          #_(Configurator/reconfigure (.build builder))

          #_(println "config after initialize:")
          #_(.writeXmlConfiguration builder System/out)

          (let [logger2 (LogManager/getLogger "com")]
            (.info logger2 "Hello from Log4j2 with getlogger com"))

          (let [logger2a (LogManager/getLogger "com2")]
            (.info logger2a "Hello from Log4j2 with getlogger com2"))

          (let [logger2a (LogManager/getLogger "log2")]
            (.info logger2a "Hello from Log4j2 with getlogger log2"))

          )
        (Configurator/shutdown logger-context)
        )



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


(defn std-out-appender [builder appender-name pattern]
  (-> builder
      (.newAppender appender-name, "CONSOLE")
      (.addAttribute "target" ConsoleAppender$Target/SYSTEM_OUT)
      (.add (-> (.newLayout builder "PatternLayout")
                (.addAttribute "pattern", pattern)))))

(defn std-err-appender [builder appender-name pattern]
  (-> builder
      (.newAppender appender-name, "CONSOLE")
      (.addAttribute "target" ConsoleAppender$Target/SYSTEM_ERR)
      (.add (-> (.newLayout builder "PatternLayout")
                (.addAttribute "pattern", pattern)))))

(defn file-appender [builder appender-name pattern filename]
  (-> builder
      (.newAppender appender-name, "File")
      (.addAttribute "fileName" filename)
      (.add (-> (.newLayout builder "PatternLayout")
                (.addAttribute "pattern", pattern)))))

(defn new-logger [builder level ref logger-name]
  (->
   (.newLogger builder logger-name level)
   (.add (.newAppenderRef builder ref))
   (.addAttribute "additivity", false)))

(defn root-logger [builder level]
  (-> (.newRootLogger builder level)))

;; the equivalent of having magic xml file on classpath
(defn setup-logging-both []
  (let [builder (config/builder)
        std-err-app-name "Stderr"
        file-app-name "file"
        logger (-> (.newLogger builder "ndevreeze.logger" Level/DEBUG)
                   (.addAttribute "additivity" false))]
    (-> builder
        (.add (std-err-appender builder std-err-app-name log-format))
        (.add (file-appender builder file-app-name log-format
                             "target/logfile.log"))
        (.add (root-logger builder org.apache.logging.log4j.Level/INFO))
        (.add (-> logger
                  (.add (.newAppenderRef builder std-err-app-name))
                  (.add (.newAppenderRef builder file-app-name))))
        (config/start))
    (.writeXmlConfiguration builder System/out)
    (println)))

(defn setup-logging-stderr-half
  "Create both appenders, but only add stderr to config."
  []
  (let [builder (config/builder)
        std-err-app-name "Stderr"
        file-app-name "file"
        logger (-> (.newLogger builder "ndevreeze.logger" Level/DEBUG)
                   (.addAttribute "additivity" false))]
    (-> builder
        (.add (std-err-appender builder std-err-app-name log-format))
        (.add (file-appender builder file-app-name log-format
                             "target/logfile.log"))
        (.add (root-logger builder org.apache.logging.log4j.Level/INFO))
        (.add (-> logger
                  (.add (.newAppenderRef builder std-err-app-name)))))
    (.newAppenderRef builder file-app-name) ;; create the ref, don't use it yet.
    (let [context (config/start builder)]
      (.writeXmlConfiguration builder System/out)
      (println)
      context)    ))

(defn setup-logging-stderr []
  (let [builder (config/builder)
        std-err-app-name "Stderr"
        logger (-> (.newLogger builder "ndevreeze.logger" Level/DEBUG)
                   (.addAttribute "additivity" false))]
    (-> builder
        (.add (std-err-appender builder std-err-app-name log-format))
        (.add (root-logger builder org.apache.logging.log4j.Level/OFF))
        (.add (-> logger
                  (.add (.newAppenderRef builder std-err-app-name)))))
    (let [context (config/start builder)]
      (.writeXmlConfiguration builder System/out)
      (println)
      context))  )

;; from widd/config:
;; should this be an appender-config, an appender, or an appender-ref?
;; TODO: appender-from-ctx looks the same as the given appender, same class and address.
(defn add-appender-to-running-context
  "Return logger"
  ([appender] (add-appender-to-running-context appender (log-impl/context)))
  ([^Appender appender ^LoggerContext context]
   (println "Starting appender: " appender)
   (.start appender)
   (-> (.getConfiguration context)
       (.addAppender appender))
   (let [appender-from-ctx (-> (.getConfiguration context)
                               (.getAppender (.getName appender)))]
     (doseq [logger (config/get-loggers context)]
       (println "Add app-from-ctx to logger: " appender-from-ctx logger)
       (.info config/status-logger (str "adding appender to " (.getName logger)))
       (.addAppender logger appender-from-ctx)))
   (.updateLoggers context)
   (config/get-loggers context)))

(defn mk-record-event
  []
  (let [state (atom [])]
    (->
     (fn [event] (swap! state conj event))
     (with-meta {:state state}))))



;; from widd/log_api.clj
#_(defn set-level
    "FYI the root logger name is the empty string. or you can refer to it via LogManager/ROOT_LOGGER_NAME"
    [logger-name level]
    (let [ctx ^org.apache.logging.log4j.core.LoggerContext (log-impl/context)]
      (-> ctx
          (.getConfiguration)
          (.getLoggerConfig (str logger-name))
          (.setLevel (Level/valueOf (str/upper-case (name level)))))
      (.updateLoggers ctx)))



#_(defn make-logger
    "Dynamically create a logger, with a builder.
   Normally called after initial config is done."
    [name]
    (LogManager/getLogger name))

(defn add-file-appender
  "Dynamically add file appender after (config/start)"
  [name]
  (let [app (make-file-appender name "target/logfile-dyn.log")
        logger (add-appender-to-running-context app)]
    [logger app]))

(defn add-logger-file-appender
  "Dynamically add logger and file appender after (config/start)"
  [name]
  (println "add-logger-file-appender with name: " name)
  (let [logger (make-logger ^String name)
        app (make-file-appender name "target/logfile-dyn2.log")
        err-app (make-writer-appender "err-app" *err*)
        ctx (log-impl/context)
        cfg (.getConfiguration ctx)]
    (.start app)
    (.start err-app)
    (println "Started app: " app)
    (println "Started err-app: " err-app)
    (.addLoggerAppender cfg logger app)
    (.addAppender cfg err-app)
    (.addAppender logger err-app)
    [logger app])  )

;; deze eerst gebruikt, ook goed. Maar met addLoggerAppender iets kleiner.
#_(defn add-logger-file-appender
    "Dynamically add logger and file appender after (config/start)"
    [name]
    (println "add-logger-file-appender with name: " name)
    (let [logger (make-logger ^String name)
          app (make-file-appender name "target/logfile-dyn2.log")
          ctx (log-impl/context)]
      (.start app)
      (println "Started app: " app)
      (-> (.getConfiguration ctx)
          (.addAppender app))
      (let [app-from-ctx (-> (.getConfiguration ctx)
                             (.getAppender (.getName app)))]
        (println "Adding app-from-ctx to logger:" app-from-ctx logger)
        (.addAppender logger app-from-ctx))
      [logger app]))

(defn stop-logging
  "Stop logging, including closing log files"
  []
  (Configurator/shutdown (log-impl/context)))

(defn stop-appender
  "Stop appender and remove from config"
  [app]
  (.stop app)
  (let [ctx (log-impl/context)]
    (doseq [logger (config/get-loggers ctx)]
      (.info config/status-logger (str "removing appender from " (.getName logger)))
      (.removeAppender logger app))))

(defn get-appenders
  ([] (get-appenders (log-impl/context)))
  ([^LoggerContext context]
   (->> (config/get-loggers context)
        (mapcat (fn [l] (.getAppenders l))))))

(defn stop-logger-appender
  [logger appender]
  (println "Stopping logger and appender: " logger appender)
  (stop-appender appender)
  (let [ctx (log-impl/context)
        cfg (.getConfiguration ctx)]
    (.removeLogger cfg (.getName logger))
    (.updateLoggers ctx)
    ;; (.reconfigure ctx) ;; seems to remove all loggers, start from
    ;; scratch. We don't want this.
    ))

(defn stop-logger-with-appenders
  [logger]
  (println "Stopping logger with appenders: " logger)
  (doseq [[_name app] (.getAppenders logger)]
    (stop-appender app))
  (let [ctx (log-impl/context)
        cfg (.getConfiguration ctx)]
    (.removeLogger cfg (.getName logger))
    (.updateLoggers ctx)
    ;; (.reconfigure ctx) ;; seems to remove all loggers, start from
    ;; scratch. We don't want this.
    ))

(defn widd-test
  "Using code from https://github.com/henryw374/clojure.log4j2"
  []
  #_(setup-logging-both)
  #_(def state (-> (testing/setup-recording-context) testing/context-state))
  (setup-logging-stderr)

  (log/info "hello")

  ;; change log level to trace

  (log/set-level 'ndevreeze.logger :trace)

  (let [[_loggers app] (add-file-appender "file")]

    (log/info "Appenders: {}" (config/get-appenders))
    (log/info "context->data: {}" (config/context->data))

    (print-loggers-appenders "After add-file-appender (dyn.log)")

    (log/info "Log after adding file appender (in dyn.log)")
    #_(doseq [logger loggers]
        (println "logger: " logger)
        (log logger :info "log logger :info - Log after adding file appender"))

    ;; prb also remove from context
    (stop-appender app)
    (log/info "Log after stopping file appender")
    )

  (let [[logger app] (add-logger-file-appender "loggerfile")]

    #_(log logger :info "LA: Appenders: {}" (config/get-appenders))
    #_(log/info "LA: context->data: {}" (config/context->data))

    (log logger :info ["LA: Log after adding file appender (dyn2)"])
    (register-logger! *err* logger)
    (info "LA-info: Log after adding file appender (dyn2)")
    (print-loggers-appenders "After add-logger-file-appender")
    ;; prb also remove from context

    (log logger :info ["LA: log logger :info - Log after adding file appender (dyn2)"])
    (log/info "LA1a: Log just before stopping logger file appender (stderr)")
    (stop-logger-with-appenders logger)
    (print-loggers-appenders "After stopping logger and appender")
    (log/info "LA1b: Log after stopping logger file appender (stderr)")
    (log logger :info ["LA2: log after stopping logger and file appender (dyn2, not shown)"])
    )


  (stop-logging)

  ;; warning is shown: WARN No Log4j 2 configuration file found. Using
  ;; default configuration (logging only errors to the console), or
  ;; user programmatically provided configurations. Set system
  ;; property 'log4j2.debug' to show Log4j 2 internal initialization
  ;; logging. See
  ;; https://logging.apache.org/log4j/2.x/manual/configuration.html
  ;; for instructions on how to configure Log4j 2
  (log/info "Log after stop-logging")
  10)
