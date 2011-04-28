(ns swank-clj.commands.debugger
  "Debugger commands.  Everything that the proxy responds to"
  (:require
   [swank-clj.logging :as logging]
   [swank-clj.jpda :as jpda]
   [swank-clj.connection :as connection]
   [swank-clj.debug :as debug]
   [swank-clj.inspect :as inspect]
   [swank-clj.messages :as messages]
   [swank-clj.swank.core :as core]
   [clojure.java.io :as io]
   )
  (:use
   [swank-clj.commands :only [defslimefn]]))

(defn invoke-restart [restart]
  ((nth restart 2)))

(defslimefn backtrace [connection start end]
  (let [level-info (connection/sldb-level-info connection)]
    (debug/build-backtrace level-info start end)))

(defslimefn invoke-nth-restart-for-emacs [connection level n]
  (let [level-info (connection/sldb-level-info connection level)
        thread-id (debug/invoke-restart connection level-info n)]
    ;;(connection/sldb-drop-level connection n)
    (connection/send-to-emacs
     connection (messages/debug-return thread-id level))
    nil))

(defslimefn frame-catch-tags-for-emacs [connection n]
  nil)

(defslimefn frame-locals-for-emacs [connection n]
  (let [level-info (connection/sldb-level-info connection)]
    (messages/frame-locals
     (debug/frame-locals-with-string-values level-info n))))

(defslimefn frame-locals-and-catch-tags [connection n]
  (list (frame-locals-for-emacs connection n)
        (frame-catch-tags-for-emacs connection n)))

(defslimefn frame-source-location [connection n]
  (let [level-info (connection/sldb-level-info connection)]
    (debug/source-location-for-frame level-info n)))

(defslimefn inspect-frame-var [connection frame index]
  (let [inspector (connection/inspector connection)
        level-info (connection/sldb-level-info connection)
        object (debug/nth-frame-var level-info frame index)]
    (when object
      (inspect/reset-inspector inspector)
      (inspect/inspect-object inspector object)
      (messages/inspector
       (inspect/display-values inspector)))))

;;; Threads
(defslimefn list-threads
  "Return a list (LABELS (ID NAME STATUS ATTRS ...) ...).
LABELS is a list of attribute names and the remaining lists are the
corresponding attribute values per thread."
  [connection]
  (let [threads (debug/thread-list connection)
        labels '(:id :name :state :suspends)]
    (cons labels threads)))

;;; TODO: Find a better way, as Thread.stop is deprecated
(defslimefn kill-nth-thread [connection index]
  (when index
    (when-let [thread (debug/nth-thread connection index)]
      (println "Thread: " thread)
      (debug/stop-thread (first thread)))))
