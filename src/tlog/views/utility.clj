(ns tlog.views.utility)


;; Taken from Clojure 1.2 clojure.contrib.def:
(defn name-with-attributes
  "To be used in macro definitions. Handles optional docstrings and attribute maps for a name to be
   defined in a list of macro arguments. If the first macro argument is a string, it is added as a
   docstring to name and removed from the macro argument list. If afterwards the first macro
   argument is a map, its entries are added to the name's metadata map and the map is removed from
   the macro argument list. The return value is a vector containing the name with its extended
   metadata map and the list of unprocessed macro arguments."
  [name macro-args]
  (let [[docstring macro-args] (if (string? (first macro-args))
                                 [(first macro-args) (next macro-args)]
                                 [nil macro-args])
        [attr macro-args] (if (map? (first macro-args))
                            [(first macro-args) (next macro-args)]
                            [{} macro-args])
        attr (if docstring
               (assoc attr :doc docstring)
               attr)
        attr (if (meta name)
               (conj (meta name) attr)
               attr)]
    [(with-meta name attr) macro-args]))

;; Taken from Clojure 1.2 clojure.contrib.generic.functor, fmap, defmethod for IPersistentMap:
(defn map-map
  [f m]
  (into (empty m) (for [[k v] m] [k (f v)])))

(defn any->nil
  "Take whatever arguments and always return nil. Useful for neutralizing arguments in Hiccup forms."
  [& more]
  nil)

(defn ms-to*
  "Create a function that expects a millisecond UNIX timstamp, to convert it to a date/time string."
  [format*]
  (let [format (java.text.SimpleDateFormat. format*)]
    (.setTimeZone format (java.util.TimeZone/getTimeZone "GMT"))
    #(.format format (java.util.Date. (long %)))))

(def ms-to-day (ms-to* "yyyy-MM-dd"))
(def ms-to-day-time (ms-to* "yyyy-MM-dd '<span class=\"hour-minute\">'H:mm'</span>'"))
(def ms-to-datetime (ms-to* "yyyy-MM-dd'T'H:mm:ss'+00:00'"))
(def ms-to-rfc-3339 (ms-to* "yyyy-MM-dd'T'HH:mm:ssZ")) ;; as required for Atom feeds