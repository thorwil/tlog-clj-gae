(ns tlog.models.models
  "Public datastore reading and writing functions, used exclusively by handlers."
  (:require [appengine-magic.services.datastore :as ds]
	    [appengine-magic.services.task-queues :as task]
	    [tlog.conf :as conf]
            [tlog.models.backing :as b])
  (:use [tlog.models.utility :only [keywordize update-in-if-key]])
  (:import tlog.models.backing.Article
           tlog.models.backing.SlugRel
           tlog.models.backing.Comment
           tlog.models.backing.DeletionQueueItem))


;; Articles

(defn add-article!
  [{:strs [title slug body]}]
  (let [body-t (ds/as-text body)
	now (System/currentTimeMillis)
	id (ds/key-id (ds/save! (Article. title body-t now now)))]
    (ds/save! (SlugRel. slug id))))

(defn update-article!
  "Save Article to datastore."
  [{:strs [title body id]}]
  (let [body-t (-> body ds/as-text)
	now (System/currentTimeMillis)
	old (b/article (Integer. id))]
    (ds/save! (assoc old :title title :body body-t :updated now))))

(defn article-slugs
  "List all Article slugs in use."
  []
  (map :slug (ds/query :kind SlugRel)))

(defn move-article!
  "Delete the old SlugRel and create a new one, to change the slug of an Article."
  [from to]
  (let [id (b/slug->article-id from)
	old (ds/retrieve SlugRel from)]
    (ds/delete! old)
    (ds/save! (b/SlugRel. to id))))

;; Specialize b/articles-paginated* for a view including bodies:
(def articles-paginated (partial b/articles-paginated* #(b/unText-body %)))

;; Specialize b/articles-paginated* for just listing deletion-queue, title, link:
(def articles-heads-paginated (partial b/articles-paginated* #(select-keys % [:slug :title])))


;; Comments

(defn add-comment!
  "Save new comment to datastore. Return comment map with datastore ID included."
  [{:strs [article-id parent author link body]}]
  (let [parent* (Integer. parent)
	body-t (-> body ds/as-text)
	now (System/currentTimeMillis)
	index (-> Comment b/get-total inc)] ; Start at 1, not 0, as this will be exposed in the view
    (-> (ds/save! (b/Comment. parent* index author link body-t now now))
        b/assoc-datastore-id-property
        b/unText-body)))

(defn update-comment!
  "Receive either id and body, or id, author and link in a map with string keys. Update existing
   Comment record."
  [{:strs [id] :as update}]
  (let [update (keywordize update)
        update (update-in-if-key update :body ds/as-text)
	now {:updated (System/currentTimeMillis)}
	old (ds/retrieve Comment (Integer. id))]
    (ds/save! (reduce into [old update now]))))


;; Blobs

(defn blobs-paginated
  "Retrieve a range of Blobs. Add data for page navigation."
  [from-to]
  (b/items-paginated "__BlobInfo__"
                     from-to conf/blobs-per-page
                     :creation
                     #(for [b %]
                        (b/assoc-delete-queued-property b :filename))))


;; Deletion queue functions for Articles, Blobs and Comments

(defn queue-delete!
  "Takes kind and identifier as strings. Adds item id to queue for delayed, cancel-able deletion."
  [kind identifier]
  (task/add! :url "/admin/delete"
             :countdown-ms (* 1000 60 2)
             :params {:kind kind
                      :identifier identifier})
  (ds/save! (b/DeletionQueueItem. identifier)))

(defn unqueue-delete!
  "Cancel delayed deletion by deleting the DeletionQueueItem belonging to the key."
  [identifier]
  (b/when-delete (ds/retrieve DeletionQueueItem identifier)))


;; Delete

(defn delete!
  [kind identifier]
  (when-let [d (ds/retrieve DeletionQueueItem identifier)]
    (ds/delete! d)
    ((b/kind->delete-fn kind) identifier)))