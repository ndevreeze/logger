(defproject ndevreeze/logger "0.6.2"
  :description "Small log4j2 logging wrapper"
  :url "https://github.com/ndevreeze/logger"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.4"]
                 [clj-commons/fs "1.6.312"]
                 [org.apache.logging.log4j/log4j-core "2.25.3"]
                 [org.apache.logging.log4j/log4j-api "2.25.3"]
                 [clojure.java-time/clojure.java-time "1.4.3"]   ; 2024-04-03: wrt time/interval in missed-sales.
                 [org.threeten/threeten-extra "1.8.0"]             ; 2024-04-03: prb this needs to be close to java-time
                 ]

  :target-path "target/%s"

  :resource-paths ["resources"]
  
  :profiles {:dev {:dependencies [[midje "1.10.10"]]}}

  :repl-options {:init-ns ndevreeze.logger}

  :codox
  {:output-path "docs/api"
   :metadata {:doc/format :markdown}
   :source-uri "https://github.com/ndevreeze/logger/blob/master/{filepath}#L{line}"}

  :repositories [["releases" {:url "https://clojars.org/repo/"
                              :creds :gpg}]])
  
