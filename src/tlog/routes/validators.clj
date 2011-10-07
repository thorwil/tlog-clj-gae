(ns tlog.routes.validators
  (:require [tlog.models :as models]
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

(defn valid-kind
  "Nil or a given valid entity name."
  [s]
  (some #{s} ["article" "blob" "comment"]))

(defvalid valid-filename filename?)

(defn valid-filename->blob-key
  "Nil, unless s is in filename.extension format and maps to a blob-key, which will be returned, then."
  [s]
  (domonad maybe-m [s* (valid-filename s)]
	   (models/blob-key-by-filename s*)))

(defn valid->int
  "Nil, or if possible, s converted to an integer."
  [s]
  (try (Integer. s)
       (catch Exception e nil)))

(defvalid valid-larger-zero #(> % 0))

(with-monad maybe-m
  (def valid-nr (m-chain [valid->int
			  valid-larger-zero])))

(defn valid->pair
  "Nil, or a vector with the parts of s before and after a '-'."
  [s]
  (let [ss (split s #"-")]
    (when (= 2 (count ss)) ss)))

(defn valid-nr-pair [[a b]]
  (domonad maybe-m
	   [a* (valid-nr a)
	    b* (valid-nr b)]
	   [a* b*]))

(with-monad maybe-m
  (def valid-items-range (m-chain [valid->pair
				   valid-nr-pair])))

(defn valid-items-range-or-default
  "default-from-to, if from-to is empty,
   from-to converted to a pair in a vector, if it maps to an article or blob index range,
   otherwise nil."
  [from-to default-from-to]
  (if (empty? from-to)
    (default-from-to)
    (valid-items-range from-to)))

(defn valid-articles-range
  "valid-items-range-or-default specialized for articles."
  [from-to-default maybe-from-to]
  (valid-items-range-or-default maybe-from-to #(models/articles-default-range from-to-default)))

(defn valid-articles-range-admin
  [from-to]
  (valid-articles-range conf/articles-per-admin-page from-to))

(defn valid-articles-range-journal
  [from-to]
  (valid-articles-range conf/articles-per-journal-page from-to))
  
(defn valid-blobs-range
  "valid-items-range-or-default specialized for blobs."
  [from-to]
  (valid-items-range-or-default from-to models/blobs-default-range))

(with-monad maybe-m
  (def valid-slug->tree (m-chain [not-empty
				  models/slug->tree])))
