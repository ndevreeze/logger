(defproject ndevreeze/logger "0.2.0"
  :description "Small slf4j/log4j logging wrapper"
  :url "https://github.com/ndevreeze/logger"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [me.raynes/fs "1.4.6"]
                 [org.slf4j/slf4j-api "1.7.25"]
                 [org.slf4j/slf4j-log4j12 "1.7.25"]
                 [log4j "1.2.17"]
                 [org.clojure/tools.logging "1.1.0"] ;; was 0.2.6
                 [clojure.java-time "0.3.2"] ;;; new in Java 8, replacing Joda-time
                                             ;;; and clj-time
                 ]

  :target-path "target/%s"

  :resource-paths ["resources"]
  
  :profiles {:dev {:dependencies [[midje "1.9.9"]]}}

  :repl-options {:init-ns ndevreeze.logger}

  :repositories [["releases" {:url "https://clojars.org/repo/"
                              :creds :gpg}]]  
    
  )
  
