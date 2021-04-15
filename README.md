# logger

Minimal Clojure logging library, mainly for scripting 

## Why?

There are quite a few logging libraries already available, so why another one? Some goals:

* Log both to a file and to the console (stdout for now)
* Include timestamps with both milliseconds and timezone
* No config files needed
* Keep it simple, mostly for command line scripts
* Support multiple log files, e.g. server/daemon and client/script logs.

## Installation

Leiningen/Boot

    [ndevreeze/logger "0.4.0"]

Clojure CLI/deps.edn

    ndevreeze/logger {:mvn/version "0.4.0"}

[![Clojars Project](https://img.shields.io/clojars/v/ndevreeze/logger.svg)](https://clojars.org/ndevreeze/logger)

## Usage

Require:

    (ns my.namespace
      (:require [ndevreeze.logger :as log]))

Initialise the logger:

    (log/init "/tmp/foo.log" :info)
  
or, to only log to stderr:

    (log/init nil :debug)
  
or, init with a map, with these keys:

   - level     - log level like :info, :debug
   - file      - give an explicit full path. Default is nil, use pattern
   - pattern   - use a pattern for the log file path, see below
   - location  - some pattern defaults/shortcuts, :home, :cwd, :script, :temp, default is nil
   - name      - script name, \"script\" bij default
   - cwd       - give an explicit current-working-dir, default is nil
   - overwrite - boolean, overwrite an existing log file, default false

  if all of file, pattern and location are nil, do not create a
  logfile, just log to the console.

  To use in pattern:
  
   - %h = home dir
   - %c = current dir
   - %s = script dir (TBD)
   - %t = temp dir (/tmp, or c:/tmp)
   - %n = script name
   - %d = datetime, as yyyy-mm-ddTHH-MM-SS"

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

* No known errors, but see Todo below. Use Github issues if you want to report a bug.

### Todo

* Should use Log4j v2, now using v1.

## Related and similar projects (libraries)

* https://github.com/pjlegato/onelog - used as a base for this library, but some differences.
* https://github.com/malcolmsparks/clj-logging-config - also used as a base
    
## License

Copyright © 2020, 2021 Nico de Vreeze

Distributed under the Eclipse Public License, the same as Clojure.
