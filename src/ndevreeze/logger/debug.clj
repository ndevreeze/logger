(ns ndevreeze.logger.debug
  "Debugging functions for logger.
   Mainly used for getting Log4j2 to work."
  (:require [clojure.string :as str]
            [java-time :as time]
            [com.widdindustries.log4j2.log-api :as log] ; TODO: rename to api or widd.
            [com.widdindustries.log4j2.log-impl :as log-impl] ; TODO: rename to api or widd.
            [com.widdindustries.log4j2.config :as config]
            [com.widdindustries.log4j2.log-impl :as widd-impl]
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

;; move to debug namespace? Or testing-namespace?
(defn print-loggers-appenders
  "Print loggers and appenders from current context to stdout"
  [title]
  (println "=================")
  (println title)
  (doseq [l (config/get-loggers (widd-impl/context))]
    (println "logger: " l)
    (doseq [[name app] (.getAppenders l)]
      (println "-> name, app: " name ", " app)))
  (println "================="))

(defn write-config
  "Write configuration for builder to stdout"
  [builder]
  (.writeXmlConfiguration builder System/out))
