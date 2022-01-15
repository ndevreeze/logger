(ns ndevreeze.logger.debug
  "Debugging functions for logger.
   Mainly used for getting Log4j2 to work."
  (:require
   [com.widdindustries.log4j2.log-impl :as widd-impl] ; TODO: rename to api or widd.
   [com.widdindustries.log4j2.config :as config]))

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
