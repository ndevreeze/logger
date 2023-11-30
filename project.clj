(defproject ndevreeze/logger "0.6.1"
  :description "Small log4j2 logging wrapper"
  :url "https://github.com/ndevreeze/logger"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [clj-commons/fs "1.6.310"]
                 [org.apache.logging.log4j/log4j-core "2.22.0"]
                 [org.apache.logging.log4j/log4j-api "2.22.0"]
                 [clojure.java-time "1.4.2"]]

  :target-path "target/%s"

  :resource-paths ["resources"]
  
  :profiles {:dev {:dependencies [[midje "1.10.9"]]}}

  :repl-options {:init-ns ndevreeze.logger}

  :codox
  {:output-path "docs/api"
   :metadata {:doc/format :markdown}
   :source-uri "https://github.com/ndevreeze/logger/blob/master/{filepath}#L{line}"}

  :repositories [["releases" {:url "https://clojars.org/repo/"
                              :creds :gpg}]])
  
