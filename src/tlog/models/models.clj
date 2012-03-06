(ns tlog.models.models
  "Public datastore reading and writing functions, used exclusively by handlers."
  (:require [appengine-magic.services.datastore :as ds]
	    [appengine-magic.services.task-queues :as task]
	    [tlog.conf :as conf]
            [tlog.models.backing :as b])
  (:use [clojure.string :only [split]]
        [tlog.models.utility :only [keywordize update-in-if-key]])
  (:import tlog.models.backing.Article
           tlog.models.backing.SlugRel
           tlog.models.backing.FeedRel
           tlog.models.backing.Comment
           tlog.models.backing.DeletionQueueItem))


;; Feed

(defn feed
  "Retrieve Articles for an Atom feed. Takes the name of a feed, the number of items to include and
   an index offset for the first Article."
  [feed-name per-page offset]
  (let [rs (ds/query :kind FeedRel
                     :filter (= :feed feed-name)
                     :offset offset
                     :limit per-page
                     :sort [[:created :dsc]])
        as (map #(b/article (-> % :article-id Integer.)) rs)
        as (map b/unText-body as)]
    {:items as}))

(defn feed-selection-change!
  "Update on which feeds an Article belongs to, on a single feed-basis. Add or delete a FeeDrel, accordingly."
  [{:strs [slug feed checked]}]
  (let [id (b/slug->article-id slug)]
    (if (= checked "true")
      ;; Add FeedRel, as a checkbox has been turned on:
      (ds/save! (FeedRel. id feed (System/currentTimeMillis)))
      ;; Delete FeedRel, as a checkbox has been turned off:
      (ds/delete! (ds/query :kind FeedRel :filter ((= :feed feed) (= :article-id id)))))))


;; Articles

(defn add-feed-rels!
  [article-id feeds-string]
  (let [fs (split feeds-string #" ")
        fs (partition 2 fs)
        fs (filter #(= "true" (second %)) fs)
        fs (map first fs)
        now (System/currentTimeMillis)]
    (doseq [feed-name fs]
      (ds/save! (FeedRel. article-id feed-name now))))) 

(defn add-article!
  [{:strs [title slug body feeds]}]
  (let [body-t (ds/as-text body) ;; Converts to com.google.appengine.api.datastore.Text if too long.
                                 ;; Otherwise stays java.lang.String.
	now (System/currentTimeMillis)
	id (ds/key-id (ds/save! (Article. title body-t now now)))]
    (ds/save! (SlugRel. slug id))
    (add-feed-rels! id feeds)))

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

(defn articles-slug-title-paginated
  "Retrieving a from-to of Articles, extract slug and title, add whether it's on the deletion queue
   and data for page navigation."
  [from-to per-page]
  (b/items-paginated Article
                     from-to per-page
                     :created
                     (fn [as] (for [a as]
                                (let [id (ds/key-id a)
                                      slug (b/article-id->slug id)]
                                  ((comp #(b/assoc-delete-queued-property % :slug)
                                         #(assoc % :slug slug :id id)
                                         #(select-keys % [:slug :title]))
                                   a))))
                     b/articles-total))

(defn journal
  "Retrieve Articles that are included in the journal feed."
  [[from to] per-page]
  (let [ids (b/journal-feed-article-ids)
        total (count ids)]
    (b/paginate (b/ids->articles (b/take-range [from to] ids))
                [from to]
                per-page
                total)))


;; Comments

(defn add-comment!
  "Save new comment to datastore. Return comment map with datastore ID included."
  [{:strs [article-id parent author link body]}]
  (let [parent* (Integer. parent)
	body-t (-> body ds/as-text)
	now (System/currentTimeMillis)
	index (inc (b/comments-total))] ; Start at 1, not 0, as this will be exposed in the view
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