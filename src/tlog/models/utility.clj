(ns tlog.models.utility)


(defmacro hash-map-syms-as-keys
  "Create hash-map with the names of the given symbols as keys."
  [& syms]
  (zipmap (map (comp keyword name) syms) syms))

(defn keywordize
  "Take map with string keys. Return map with keyword keys."
  [m]
  (update-keys keyword m))

(defn update-in-if-key
  "Take a function, map and key. If the key is in the map, pass the corresponding value through the
   function. Return resulting map."
  [m k f]
  (if-let [v (m k)]
    (update-in m [k] f) ;; A naked update-in would add the key to the map, if it wasn't present before
    m))