(ns user
  "Start appengine-magic development mode with Jetty.
   Paste to REPL to make sure datastore operations will be available from the REPL (they won't after
   slime-eval-buffer).

   Triggering this script via slime-load-hook does not work, as appengine-magic tries to determine
   what kind of environment it runs in (see its in-appengine-interactive-mode?), resulting in
   ae/serve being unavailabel, then."
  (:require [appengine-magic.core :as ae]
            [tlog.core])
  (:use [clojure.stacktrace]
        [clojure.repl]))

(compile 'tlog.core)
(in-ns 'tlog.core)
(ae/serve tlog-app)
(println "Interactive Jetty instance started.")
(intern 'clojure.core '*out* *out*)