(ns tlog.models.for_validators
  "Model functions to be used in validators, exclusively."
  (:use [appengine-magic.services.datastore :only [query]]
        [tlog.models.backing :only [default-range articles-total articles-in-feed-total blobs-total
                                    slug->article-id tree]]
        [tlog.conf :only [blobs-per-page]])
  (:import tlog.models.backing.Article))


;; Articles

(defn articles-default-range
  "Article number range for the n last items to appear, if the url doesn't include a range."
  [n]
  (default-range n (articles-total)))

(defn articles-in-feed-default-range
  "Article number range for the n last items to appear, if the url doesn't include a range."
  [n feed-name]
  (default-range n (articles-in-feed-total feed-name)))


;; Trees (Article and Comments)

(defn slug->tree
  [slug]
  (when-let [id (slug->article-id slug)]
    (assoc (tree id) :slug slug)))


;; Blobs

(defn blob-key-by-filename
  [name]
  (-> (query :kind "__BlobInfo__" :filter (= :filename name)) first :blob-key))

(defn blobs-default-range
  "Blob number range for the n last items to appear, if the url doesn't include a range."
  []
  (default-range blobs-per-page blobs-total))