(defproject ndevreeze/logger "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [me.raynes/fs "1.4.6"]
                 [org.slf4j/slf4j-api "1.7.25"]
                 [org.slf4j/slf4j-log4j12 "1.7.25"]
                 [log4j "1.2.17"]
                 [org.clojure/tools.logging "1.1.0"] ;; was 0.2.6
                 ]

  :target-path "target/%s"

  :resource-paths ["resources"]
  
  :profiles {:dev {:dependencies [[midje "1.9.9"]]}}

  :repl-options {:init-ns ndevreeze.logger}

  :repositories [["releases" {:url "https://clojars.org/repo/"
                              :creds :gpg}]]  
    
  )
  
