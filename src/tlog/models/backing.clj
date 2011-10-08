(ns tlog.models.backing
  "Private model business."
  (:require [appengine-magic.services.datastore :as ds]
	    [appengine-magic.services.task-queues :as task]
	    [tlog.conf :as conf])
  (:use [clojure.math.numeric-tower :only [abs]]
        [tlog.models.utility :only [hash-map-syms-as-keys]])
  (:import com.google.appengine.api.blobstore.BlobInfo))


;; Entities

(ds/defentity SlugRel [^:key slug, article-id])
;; Use datastore given ID of the Article as article-id, and thus as parent for 1st level Comments.
  
(ds/defentity Article [title, body, created, updated])
;; Letting the datastore generate keys allows referencing Articles from Comments, independent of
;; slugs.

(ds/defentity Comment [parent, index, author, link, body, created, updated])

(ds/defentity DeletionQueueItem [^:key identifier])
;; Use queued tasks for cancelable, delayed deletion (instead of asking: are you sure?).
;; Initially, there was no programmatic way to delete queued tasks, and there is still none
;; accessible via appengine-magic. So instead have the tasks execute deletion only if there still
;; is a matching DeletionQueueItem.


;; Pagination functions used for both Article and BlobInfo

(defn get-total
  "Query for number of datastore items of the given kind."
  [kind]
  (ds/query :kind kind :count-only? true))

(defn headwards-tailwards
  "Determine values for headwards and tailwards, used for page navigation.
   Assumes newer-to-older = tail-to-head, higher-to-lower number ordering."
  [[from to] per-page total]
  (let [headwards (if (<= to 1)
		    nil
		    (let [from* (dec to)
			  to* (max (- from* (dec per-page)) 1)]
		      [from* to*]))
	tailwards (if (>= from total)
		    nil
		    (let [to* (inc from)
			  from* (min (+ to* (dec per-page)) total)]
		      [from* to*]))]
    [headwards tailwards]))

(defn items-paginated
  "Retrieve a range of items with data for page navigation.
   The process-fn argument allows easy access to the map, before wrapping it in :items in an outer map."
  [kind from-to per-page sort-by process-fn]
  (let [total (get-total kind)
	width (-> (reduce - from-to) abs inc)
	offset (- total (first from-to))
	items* (ds/query :kind kind
			 :limit width ; number of items
			 :offset offset ; 0 delivers newest item
			 :sort [[sort-by :dsc]])
	items (process-fn items*)
	[headwards tailwards] (headwards-tailwards from-to per-page total)]
	(hash-map-syms-as-keys items headwards tailwards)))

(defn default-range
  "Number range for the n last items to appear, if the url doesn't include a range."
  [entity n]
  (let [total (get-total entity)]
    [total (- total (dec n))]))


;; Enriching property maps for Articles, Blobs and Comments

(defn assoc-datastore-id-property
  "assoc datastore ID for entities with no specific key property."
  [e]
  (assoc e :id (-> e ds/key-id str)))

(defn delete-queued?
  "Is there a DeletionQueueItem for the given key-string?"
  [key-string]
  (ds/exists? DeletionQueueItem key-string))

(defn assoc-delete-queued-property
  "assoc whether the item is on the deletion queue."
  [item key-string]
  (assoc item :delete-queued (delete-queued? (key-string item))))


;; Articles

(defn safe-get-value
  "Extract content from text class if it exists, else empty string."
  [s]
  (if (instance? com.google.appengine.api.datastore.Text s)
    (.getValue s)
    ""))

(defn article
  "Article retrived by ID, or nil."
  [id]
  (let [a (ds/retrieve Article id)]
    (when-not (empty? a)
      (assoc (update-in a [:body] #(safe-get-value %)) :id id))))

(defn articles
  "All Articles."
  []
  (ds/query :kind Article))


(defn slug->article-id
  "Used for route validation, on whether there's an Article for the given slug, in which case
   the id is returned. Does only look for a SlugRel, without checking if its article-id points
   to an existing Article."
  [slug]
  (and (not-empty slug)
       (when-let [s (ds/retrieve SlugRel slug)]
	 (:article-id s))))

(defn article-id->slug
  "Slug associated with the ID of an Article."
  [id]
  (-> (ds/query :kind SlugRel :filter (= :article-id id)) first :slug))

(defn articles-heads
  []
  "Slugs and titles of Articles."
  (for [a (articles)]
    (select-keys a [:slug :title])))

(defn articles-heads-status
  []
  "Maps of Article properties plus whether Article is on deletion queue."
  (let [a (articles-heads)]
    (assoc-delete-queued-property a :slug)))

(defn unText-body
  "Convert body property of post from com.google.appengine.api.datastore.Text to java.lang.String."
  [post]
  (update-in post [:body] (fn [b] (.getValue b))))

(defn articles-paginated*
  "Template for retrieving a from-to of Articles with data for page navigation."
  [f from-to per-page]
  (items-paginated Article
		   from-to per-page
		   :created
		   (fn [as] (for [a as]
			      (let [id (ds/key-id a)
				    slug (article-id->slug id)]
				((comp #(assoc-delete-queued-property % :slug)
				       #(assoc % :slug slug :id id)
				       f)
				 a))))))


;; Comments

(defn comments-for-parent
  "Query for Comments that reference parent-id."
  [parent-id]
  (for [c (ds/query :kind Comment :filter (= :parent parent-id))]
    (let [c* (-> c assoc-datastore-id-property unText-body)]
      (assoc-delete-queued-property c* :id))))

(defn comments
  "Retrieve all comments for the ID, then recurse to retrieve comments for the IDs of the just
   retrieved comments. The whole will be wrapped in ()."
  [id]
  (for [c (comments-for-parent id)]
    (cons c (comments (Integer. (:id c))))))


;; Trees (Article and Comments)

(defn tree
  "Tree consisting of an Article and 0 to n Comments, via article-id. nil if there is no Article."
  [id]
  (let [a (article id)
	cs (comments id)]
    (if (nil? a)
      nil
      (assoc a :comments cs))))


;; Blobs

(defn blobs
  "Query for all Blobs."
  []
  (ds/query :kind "__BlobInfo__" :sort [[:creation :dsc]]))

(defn blobs-heads
  []
  "Filenames of Blobs."
  (for [b (blobs)]
    (select-keys b [:filename])))

(defn blobs-heads-status
  []
  "Maps of Blob properties plus whether a Blob is on the deletion queue."
  (assoc-delete-queued-property (blobs-heads) :filename))


;; Deletion queue functions for Articles, Blobs and Comments

(defmacro when-delete
  "ds/delete! doesn't like nil, so wrap it and a given query or retrieve in when-let."
  [test & more]
  `(when-let [x# ~test]
     (ds/delete! x#)
     ~@more))


;; Delete

(defn delete-article!
  "Delete Article and its SlugRel"
  [identifier]
  (when-let [s (ds/retrieve SlugRel identifier)]
    (let [id (:article-id s)]
      (when-delete (ds/retrieve Article id))
      (when-delete (ds/delete! (flatten (comments id)))))
    (ds/delete! s)))

(defn delete-blob!
  [identifier]
  ;; It seems ds/retrieve doesn't work for BlobInfo, so use ds/query:
  (when-delete (ds/query :kind "__BlobInfo__" :filter (= :filename identifier))))

(defn delete-comment!
  "Delete a comment and its sub-comments."
  [identifier*]
  (let [identifier (Integer. identifier*)]
    (when-delete (ds/retrieve Comment identifier)
                 (when-delete (ds/delete! (flatten (comments identifier)))))))

(def kind->delete-fn {"article" delete-article!
                      "blob" delete-blob!
                      "comment" delete-comment!})