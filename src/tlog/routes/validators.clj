(ns tlog.routes.validators
  "Functions that return their argument unchanged, if it matches one or several predicates, or that
   convert their argument if possible. If a predicate evaluates to false or if a conversion is not
   possible, they return nil. To be used for URL matching."
  (:require [tlog.models.for_validators :as models]
            [tlog.conf :as conf])
  (:use [clojure.algo.monads :only [with-monad m-chain domonad maybe-m]]
	[clojure.string :only [split]]))


;; Predicates

(defn filename?
  "Test for basename.extension format."
  [str]
  (re-find #"\." str))


;; Template for validators

(defn valid
  [pred]
  #(when (pred %) %))

(defmacro defvalid
  "Macro for validators that evaluate to their argument if the predicate on the argument is true,
   otherwise to nil."
  [name pred]
  `(def ~name (valid ~pred)))


;; Validators

(defn kind
  "Nil or a given valid entity name."
  [s]
  (some #{s} ["article" "blob" "comment"]))

(defvalid filename filename?)

(defn filename->blob-key
  "Nil, unless s is in filename.extension format and maps to a blob-key, which will be returned, then."
  [s]
  (domonad maybe-m [s* (filename s)]
	   (models/blob-key-by-filename s*)))

(defn ->int
  "Nil, or if possible, s converted to an integer."
  [s]
  (try (Integer. s)
       (catch Exception e nil)))

(defvalid larger-zero #(> % 0))

(with-monad maybe-m
  (def nr (m-chain [->int
                    larger-zero])))

(defn ->pair
  "Nil, or a vector with the parts of s before and after a '-'."
  [s]
  (let [ss (split s #"-")]
    (when (= 2 (count ss)) ss)))

(defn nr-pair [[a b]]
  (domonad maybe-m
	   [a* (nr a)
	    b* (nr b)]
	   [a* b*]))

(with-monad maybe-m
  (def items-range (m-chain [->pair
                             nr-pair])))

(defn items-range-or-default
  "default-from-to, if from-to is empty,
   from-to converted to a pair in a vector, if it maps to an article or blob index range,
   otherwise nil."
  [from-to default-from-to]
  (if (empty? from-to)
    (default-from-to)
    (items-range from-to)))

(defn articles-range
  "items-range-or-default specialized for articles."
  [from-to-default maybe-from-to]
  (items-range-or-default maybe-from-to #(models/articles-default-range from-to-default)))

(defn articles-range-admin
  [from-to]
  (articles-range conf/articles-per-admin-page from-to))

(defn articles-range-journal
  [from-to]
  (articles-range conf/articles-per-journal-page from-to))

(defn blobs-range
  "items-range-or-default specialized for blobs."
  [from-to]
  (items-range-or-default from-to models/blobs-default-range))

(with-monad maybe-m
  (def slug->tree (m-chain [not-empty
                            models/slug->tree])))
