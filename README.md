# logger

Minimal Clojure logging library, mainly for scripting 

## Why?

There are quite a few logging libraries already available, so why another one? Some goals:

* Log both to a file and to the console (stdout for now)
* Include timestamps with both milliseconds and timezone
* No config files needed
* Keep it simple, mostly for command line scripts

## Installation

Leiningen/Boot

    [ndevreeze/logger "0.2.0"]

Clojure CLI/deps.edn

    ndevreeze/logger {:mvn/version "0.2.0"}

[![Clojars Project](https://img.shields.io/clojars/v/ndevreeze/logger.svg)](https://clojars.org/ndevreeze/logger)

## Usage

Require:

    (ns my.namespace
      (:require [ndevreeze.logger :as log]))

Initialise the logger:

    (log/init "/tmp/foo.log" :info)
  
or:

    (log/init nil :debug)
  
Then log at different levels:

    (log/error "A different logfile, error level")
    (log/warn "warn level")
    (log/debug "One line at debug level")
    (log/info "At info level")

## Developing

### Testing

    $ lein midje

or:

    $ lein repl (or start in Cider)
    (use 'midje.repl)
    (autotest)
    
### Bugs

* No known errors, but see Todo below.

### Todo

* Should use Log4j v2, now using v1.
* Log to stderr; now logging to stdout, something with ConsoleAppender

## Related and similar projects (libraries)

* https://github.com/pjlegato/onelog - used as a base for this library, but some differences.
* https://github.com/malcolmsparks/clj-logging-config - also used as a base
    
## License

Copyright © 2020 Nico de Vreeze

Distributed under the Eclipse Public License, the same as Clojure.
