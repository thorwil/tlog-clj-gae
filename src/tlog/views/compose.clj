(ns tlog.views.compose
  (:use [clojure.java.io :only [resource]]
	[ring.util.response :only [response content-type]]
        tlog.views.utility
        [tlog.views.parts :only [base not-allowed-rendition]]
        [tlog.views.atom-feed :only [feed]]))

(defn content-type-html
  [r]
  (content-type r "text/html"))

(defn content-type-atom
  [r]
  (content-type r "application/atom+xml"))

(defn assoc-fn
  "Return a vector v with function f applied to the element at index i."
  [v i f]
  (assoc v i (f (nth v i))))

(defn filter-split
  "Split vector v in 2, based on predicate pred. In true, false order."
  [pred v]
  (reduce #(assoc-fn %1
		     (if (pred %2) 0 1)
		     (fn [x] (conj x %2)))
	  [[] []]
	  v))

(defn wrap-map->map
  "Wrap a function. The wrapper takes a map and returns a map updated with the result of the given
  function on the argument map, using :buildup as key, if the result is not a map already."
  [f]
  #(let [r (f %)]
     (into % (if (map? r)
	       r
	       {:buildup r}))))

(def extract-buildup (fn [{:keys [buildup]}] buildup))

(defn comp-view
  "Compose functions to build a view. Functions are applied right to left, thus need to be listed
   from outer to inner."
  [fs shell*]
  (let [wrapped (map wrap-map->map fs)
        shell (case shell*
                ;; For finalizing responses to a POST, to deliver a feed, or response to a GET (default):
                :on-post [content-type-html response extract-buildup]
                :atom-feed [constantly content-type-atom response feed]
                [constantly content-type-html response base])]
    #(apply comp (concat shell wrapped))))

(defmacro defview
  "Macro for defining a view. Takes a vector with a name, a map with vectors per role and an
   optional argument, that if present, causes construction of a view for answering a POST."
  [name per-role* & [shell]]
  ;; If :everyone is not specified, default to an empty vector for it:
  (let [per-role (into {:everyone []} per-role*)]
    `(defn ~name
       [roles# m#]
       (let [ss*# (flatten (map ~per-role roles#))
             ;; The constraint in web.xml should protect the admin-only routes, but fails at
             ;; least in development mode. Deliver not-allowed, if there would be no view
             ;; functions otherwise:
             ss# (some not-empty [ss*# [not-allowed-rendition]])
             ;; Separate functions from defs:
             [fs# ds#] (filter-split fn? ss#)]
         ;; Compose all functions. Assemble argument map from key-value pairs
         ;; from the defs and the view's argument map:
         (((comp-view (cons identity fs#) ~shell)) (into m# ds#))))))

(defn macro-for-each
  "Take a quoted macro name and a vector. Return a do form with lists forming macro and vector element pairs."
  [macro xs]
  (cons 'do
	(for [x xs]
          `(~macro ~@x))))

(defmacro defviews
  "Call defview for each given vector."
  [& vs]
  (macro-for-each 'defview vs))