(ns ndevreeze.logger.debug
  "Debugging functions for logger.
   Mainly used for getting Log4j2 to work."
  (:import org.apache.logging.log4j.LogManager))

;; thanks to https://github.com/henryw374/clojure.log4j2
(defn get-loggers
  "Get loggers from context.
   Use default context if none given."
  ([] (get-loggers (LogManager/getContext false)))
  ([context]
   (->> (.getLoggers (.getConfiguration context))
        keys
        (map (fn [logger-name]
               (.getLogger context logger-name))))))

;; move to debug namespace? Or testing-namespace?
(defn print-loggers-appenders
  "Print loggers and appenders from current context to stdout"
  [title]
  (println "=================")
  (println title)
  (doseq [l (get-loggers (LogManager/getContext false))]
    (println "logger: " l)
    (doseq [[name app] (.getAppenders l)]
      (println "-> name, app: " name ", " app)))
  (println "================="))

(defn write-config
  "Write configuration for builder to stdout"
  [builder]
  (.writeXmlConfiguration builder System/out))
