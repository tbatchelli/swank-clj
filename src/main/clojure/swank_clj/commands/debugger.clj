(ns swank-clj.commands.debugger
  "Debugger commands.  Everything that the proxy responds to."
  (:require
   [swank-clj.logging :as logging]
   [swank-clj.jpda :as jpda]
   [swank-clj.connection :as connection]
   [swank-clj.debug :as debug]
   [swank-clj.inspect :as inspect]
   [swank-clj.messages :as messages]
   [swank-clj.swank.core :as core]
   [clojure.java.io :as io])
  (:use
   [swank-clj.commands :only [defslimefn]]))

(defn invoke-restart [restart]
  ((nth restart 2)))

(defslimefn backtrace [connection start end]
  (messages/stacktrace-frames
   (debug/backtrace connection start end) start))

(defslimefn debugger-info-for-emacs [connection start end]
  (debug/debugger-info-for-emacs connection start end))

(defslimefn invoke-nth-restart-for-emacs [connection level n]
  (debug/invoke-restart connection level n))

(defn invoke-named-restart
  [connection kw]
  (debug/invoke-named-restart connection kw))

(defslimefn throw-to-toplevel [connection]
  (invoke-named-restart connection :quit))

(defslimefn sldb-continue [connection]
  (invoke-named-restart connection :continue))

(defslimefn sldb-abort [connection]
  (invoke-named-restart connection :abort))

(defslimefn frame-catch-tags-for-emacs [connection n]
  nil)

(defslimefn frame-locals-for-emacs [connection n]
  (let [[level-info level] (connection/current-sldb-level-info connection)]
    (messages/frame-locals
     (debug/frame-locals-with-string-values level-info n))))

(defslimefn frame-locals-and-catch-tags [connection n]
  (list (frame-locals-for-emacs connection n)
        (frame-catch-tags-for-emacs connection n)))

(defslimefn frame-source-location [connection frame-number]
  (messages/location
   (debug/frame-source-location connection frame-number)))

(defslimefn inspect-frame-var [connection frame index]
  (let [inspector (connection/inspector connection)
        [level-info level] (connection/current-sldb-level-info connection)
        object (debug/nth-frame-var level-info frame index)]
    (when object
      (inspect/reset-inspector inspector)
      (inspect/inspect-object inspector object)
      (messages/inspector
       (inspect/display-values inspector)))))

;;; Threads
(def ^{:private true} thread-data-fn
  (comp
   seq
   (juxt #(:id % "")
         :name
         #(:status % "")
         #(:at-breakpoint? % "")
         #(:suspended? % "")
         #(:suspend-count % ""))))

(defslimefn list-threads
  "Return a list (LABELS (ID NAME STATUS ATTRS ...) ...).
LABELS is a list of attribute names and the remaining lists are the
corresponding attribute values per thread."
  [connection]
  (let [threads (debug/thread-list connection)
        labels '(:id :name :state :at-breakpoint? :suspended? :suspends)]
    (cons labels (map thread-data-fn threads))))

;;; TODO: Find a better way, as Thread.stop is deprecated
(defslimefn kill-nth-thread
  [connection index]
  (when index
    (when-let [thread (debug/nth-thread connection index)]
      (debug/stop-thread (:id thread)))))

;;; Breakpoints
;;; These are non-standard slime functions
(defslimefn line-breakpoint
  [connection namespace filename line]
  (debug/line-breakpoint connection namespace filename line))

(defslimefn break-on-exceptions
  "Control which expressions are trapped in the debugger"
  [connection filter-caught? class-exclusions])

;;; stepping
(defslimefn sldb-step [connection frame]
  (invoke-named-restart connection :step-into))

(defslimefn sldb-next [connection frame]
  (invoke-named-restart connection :step-next))

(defslimefn sldb-out [connection frame]
  (invoke-named-restart connection :step-out))

;; eval
(defslimefn eval-string-in-frame [connection expr n]
  (debug/eval-string-in-frame connection expr n))
