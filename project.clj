(defproject ndevreeze/logger "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.cli "1.0.194"]
;;                 [org.clojure/java.jdbc "0.7.6"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.csv "1.0.0"]
                 [clojure.java-time "0.3.2"]
                 [me.raynes/fs "1.4.6"]

                 [org.slf4j/slf4j-api "1.7.25"]
                 [org.slf4j/slf4j-log4j12 "1.7.25"]
                 [log4j "1.2.17"]
                 [org.clojure/tools.logging "1.1.0"] ;; was 0.2.6
                 
  ;;               [nl.nicodevreeze/ndv "0.1.0-SNAPSHOT"]
                 [nl.nicodevreeze/datetime "0.1.0-SNAPSHOT"]
  ;;             [nl.nicodevreeze/dynamicdb "0.1.0-SNAPSHOT"] ;; includes SQLite and Postgres
                 [ndevreeze/flexdb "0.4.0"]
                 [nl.nicodevreeze/xml-db "0.1.0-SNAPSHOT"]
                 ]

  :target-path "target/%s"

  :resource-paths ["resources"]
  
  ;; 2020-05-05: moved pomegranate to dev, not sure yet if this works.
  :profiles {:dev {:dependencies [[midje "1.9.9"]
                                  [clj-commons/pomegranate "1.2.0"]]}
             }

  ;; 2020-05-08: this should take care of setting the current namespace in a REPL.
  ;; 2020-05-21: not in .core namespace anymore.
  :repl-options {:init-ns ndevreeze.logger}

  :repositories [["releases" {:url "https://clojars.org/repo/"
                              :creds :gpg}]]  
    
  )
  
