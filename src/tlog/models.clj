(ns tlog.models
  (:require [appengine-magic.services.datastore :as ds]
	    [appengine-magic.services.task-queues :as task]
	    [tlog.conf :as conf])
  (:use [clojure.contrib.math :only [abs]])
  (:import com.google.appengine.api.blobstore.BlobInfo))


;; Utility

(defmacro hash-map-syms-as-keys
  "Create hash-map with the names of the given symbols as keys."
  [& syms]
  (zipmap (map (comp keyword name) syms) syms))

(defn ceiling
  ([p]
     (ceiling p 1))
  ([p d]
     (let [q (quot p d)
	   r (rem p d)]
       (if (pos? (* r d))
	 (+ q 1)
	 q))))


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
  "Does there exist a DeletionQueueItem for the given key?"
  [key-string]
  (ds/exists? DeletionQueueItem key-string))

(defn assoc-delete-queued-property
  "assoc whether the item is on the deletion queue."
  [item key-string]
  (assoc item :delete-queued (delete-queued? (key-string item))))


;; Articles

(defn add-article!
  [{:strs [title slug body]}]
  (let [body-t (-> body ds/as-text)
	now (System/currentTimeMillis)
	id (ds/key-id (ds/save! (Article. title body-t now now)))]
    (ds/save! (SlugRel. slug id))))

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

(defn update-article!
  "Save Article to datastore."
  [{:strs [title body id]}]
  (let [body-t (-> body ds/as-text)
	now (System/currentTimeMillis)
	old (article (Integer. id))]
    (ds/save! (assoc old :title title :body body-t :updated now))))

(defn article-slugs
  "List all Article slugs in use."
  []
  (map :slug (ds/query :kind SlugRel)))

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

(defn move-article!
  "Delete the old SlugRel and create a new one, to change the slug of an Article."
  [from to]
  (let [id (slug->article-id from)
	old (ds/retrieve SlugRel from)]
    (ds/delete! old)
    (ds/save! (SlugRel. to id))))

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

;; Specialize articles-paginated* for a view including bodies:
(def articles-paginated (partial articles-paginated* #(unText-body %)))

;; Specialize articles-paginated* for just listing deletion-queue, title, link:
(def articles-heads-paginated (partial articles-paginated* #(select-keys % [:slug :title])))

(defn articles-default-range [n]
  "Article number range for the n last items to appear, if the url doesn't include a range."
  (default-range Article n))


;; Comments

(defn add-comment!
  "Save new comment to datastore. Return comment map with datastore ID included."
  [{:strs [article-id parent author link body]}]
  (let [parent* (Integer. parent)
	body-t (-> body ds/as-text)
	now (System/currentTimeMillis)
	index (-> Comment get-total inc)] ; Start at 1, not 0, as this will be exposed in the view
    (-> (ds/save! (Comment. parent* index author link body-t now now))
        assoc-datastore-id-property
        unText-body)))

(defn comments-for-parent
  "Query for Comments that reference parent-id."
  [parent-id]
  (for [c (ds/query :kind Comment :filter (= :parent parent-id))]
    (let [c* (-> c assoc-datastore-id-property unText-body)]
      (assoc-delete-queued-property c* :id))))

(defn comments-for-parent-head-marked
  [parent-id]
  (let [cs (comments-for-parent parent-id)]
    (if (empty? cs)
      cs
      (cons (assoc (first cs) :head "true") (rest cs)))))

(defn comments
  "Retrieve all comments for the ID, then recurse to retrieve comments for the IDs of the just
   retrieved comments. The whole will be wrapped in ()."
  [id]
  (for [c (comments-for-parent-head-marked id)]
    (cons c (comments (Integer. (:id c))))))

(defn update-comment!
  "Receive either id and body, or id, author and link in a map with string keys. Update existing Comment record."
  [{:strs [id] :as params}]
  (let [update* (reduce #(assoc %1 (keyword (first %2)) (second %2)) {} (vec params)) ;; string to keyword keys
        body (:body update*)
        update (if body ;; convert body, if included
                 (assoc update* :body (-> body ds/as-text))
                 update*)
	now (System/currentTimeMillis)
	old (ds/retrieve Comment (Integer. id))]
    (ds/save! (reduce into [old update {:updated now}]))))


;; Trees (Article and Comments)

(defn tree
  "Tree consisting of an Article and 0 to n Comments, via article-id. nil if there is no Article."
  [id]
  (let [a (article id)
	cs (comments id)]
    (if (nil? a)
      nil
      (assoc a :comments cs))))

(defn slug->tree
  [slug]
  (when-let [id (slug->article-id slug)]
    (assoc (tree id) :slug slug)))


;; Blobs

(defn blob-key-by-filename
  [name]
  (-> (ds/query :kind "__BlobInfo__" :filter (= :filename name)) first :blob-key))

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

(defn blobs-paginated
  "Retrieve a range of Blobs. Add data for page navigation."
  [from-to]
  (items-paginated "__BlobInfo__"
		   from-to conf/blobs-per-page
		   :creation
		   #(for [b %]
		      (assoc-delete-queued-property b :filename))))

(defn blobs-default-range []
  "Blob number range for the n last items to appear, if the url doesn't include a range."
  (default-range "__BlobInfo__" conf/blobs-per-page))


;; Deletion queue functions for Articles, Blobs and Comments

(defn queue-delete!
  "Takes kind and identifier as strings. Adds item id to queue for delayed, cancel-able deletion."
  [kind identifier]
  (task/add! :url "/admin/delete"
             :countdown-ms (* 1000 60 2)
             :params {:kind kind
                      :identifier identifier})
  (ds/save! (DeletionQueueItem. identifier)))

(defmacro when-delete
  "ds/delete! doesn't like nil, so wrap it and a given query or retrieve in when-let."
  [test & more]
  `(when-let [x# ~test]
     (ds/delete! x#)
     ~@more))

(defn unqueue-delete!
  "Cancel delayed deletion by deleting the DeletionQueueItem belonging to the key."
  [identifier]
  (when-delete (ds/retrieve DeletionQueueItem identifier)))


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

(defn delete!
  [kind identifier]
  (when-let [d (ds/retrieve DeletionQueueItem identifier)]
    (ds/delete! d)
    ((kind->delete-fn kind) identifier)))