(ns tlog.core
  (:require [appengine-magic.core :as ae]
            [appengine-magic.services.user :as user]
            [appengine-magic.services.blobstore :as blobs]
            [appengine-magic.services.channel :as chan]
            [tlog.models :as models]
            [tlog.views :as views]
            [tlog.conf :as conf])
  (:use [ring.middleware.params :only [wrap-params]]
	[ring.util.response :only [response redirect]]
	[net.cgrand.moustache :only [app alter-response]]
	[clojure.algo.monads :only [with-monad m-chain domonad maybe-m]]
	[clojure.string :only [split join]]
	[appengine-magic.services.user :only [user-logged-in? user-admin?]]
	[appengine-magic.services.datastore :only [key-id]]))


;; Utility

(defn roles
  "Return list of roles of the current user as keys."
  []
  (cons :everyone
	(when (and (user-logged-in?) (user-admin?)) [:admin])))


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


;; Event handlers

(defn on-slugs-change!
  []
  (chan/send "trigger-on-slugs-change" (join " " (models/article-slugs))))


;; Admin GET Handlers

(defn file-form
  "File upload form, including a list of files. Uses a default or the optional argument as index
   range for the list of files."
  [index-range]
  (views/file-form (roles) (models/blobs-paginated index-range)))

(defn generate-upload-url!
  "Generate and print URL for a file upload."
  []
  (views/plain (blobs/upload-url "/admin/file-callback")))

(defn queue-delete!
  "Add a reference to the queue for delayed, cancel-able deletion."
  [kind identifier {:keys [headers]}]
  (models/queue-delete! kind identifier)
  (redirect (headers "referer")))

(defn unqueue-delete!
  "Remove a reference from deletion queue."
  [identifier {:keys [headers]}]
  (models/unqueue-delete! identifier)
  (redirect (headers "referer")))

(defn list-slugs
  "Plain text, space-separated list of slugs."
  []
  (views/list-slugs (models/article-slugs)))

(defn list-articles
  [from-to]
  (views/admin (roles)
	       (assoc (models/articles-heads-paginated from-to conf/articles-per-admin-page) :title "Articles")))

(defn article-form
  []
  (views/article-form (roles) {:title "Write"
			       :token (chan/create-channel "trigger-on-slugs-change")
			       :slugs (models/article-slugs)}))

;; Admin/Visitor GET handlers

(defn not-found
  []
  (-> (views/not-found (roles) {:title "Not found"})
      (alter-response #(assoc % :status 404))))


;; Admin POST handlers

(defn file-callback
  "Trigger callback after file upload."
  [request]
  (blobs/callback-complete request "/admin/file_done"))

(defn file-done
  "Handle the redirect from file-callback."
  [request]
  (response  "OK"))

(defn delete!
  [{:keys [params]}]
  (let [[kind identifier] (map params ["kind" "identifier"])]
    (models/delete! kind identifier)
    (when (= kind "article") (on-slugs-change!)))
  (response  "OK"))

(defn add-article!
  "Handle Submit-button triggered POST."
  [params]
  (models/add-article! (params :form-params))
  (on-slugs-change!)
  (response "OK"))

(defn move-article!
  "Change the slug of an Article."
  [{{:strs [from to]} :form-params}]
  (models/move-article! from to)
  (on-slugs-change!)
  (response "OK"))

(defn update-article!
  "Update existing Article. Used via Aloha Editor."
  [params]
  (models/update-article! (params :form-params))
  (chan/send "trigger-on-slugs-change" " ")
  (response "OK"))

(defn update-comment!
  "Update existing Comment. Used via Aloha Editor."
  [params]
  (models/update-comment! (params :form-params))
  (response "OK"))


;; Visitor GET handlers

(defn journal
  "List of Articles with full bodies."
  [range-or-nothing]
  (views/journal (roles) (models/articles-paginated range-or-nothing conf/articles-per-journal-page)))

(defn tree
  "Article with (nested) Comments."
  [slug->tree]
  (views/tree (roles) (assoc slug->tree :token (chan/create-channel "trigger-on-slugs-change"))))

(defn serve-file
  "Take a __BlobInfo__ key and request, serve file from blobstore."
  [key request]
  (blobs/serve request key))


;; Visitor POST handlers

(defn add-comment!
  "Handle Publish-button triggered POST for adding Comments."
  [{:keys [form-params]}]
  ;; The same code used for renedering nested comments recursively is used for the single additional
  ;; comment, too. This leads to the need for the double list construct:
  (let [following (Integer. (form-params "following"))]
    (views/on-add-comment (roles) (into {:comments (list (list (models/add-comment! form-params)))
                                         :following following}
                                        (when (zero? following) {:head true})))))


;; Routing

(defmacro defroutes
  "def name to routes."
  [name & more]
  `(def ~name (app ~@more)))

(defroutes admin-get-routes
  ; Match "...file", "...file/", "...file/2-1" ...:
  ["file" &] (app [[index-range valid-blobs-range]] (file-form index-range))
  ["generate_upload_url"] (generate-upload-url!)
  ["queue-delete" [kind valid-kind] [identifier not-empty]] (wrap-params (partial queue-delete! kind identifier))
  ["cancel-delete" [identifier not-empty]] (wrap-params (partial unqueue-delete! identifier))
  ["slugs"] (list-slugs)
  [[from-to valid-articles-range-admin]] (list-articles from-to)
  ["write" &] (article-form)  
  [&] (not-found))

(defroutes admin-post-routes ;; whole form is in wrap-params, see root-routes
  ["file-callback"] file-callback
  ["file_done"] file-done
  ["delete"] delete!
  ["move-article"] move-article!
  ["add-article"] add-article!
  ["update-article"] update-article!
  ["update-comment"] update-comment!)

(defroutes get-routes
  ;; Match for root, using default range, or match given index range:
  ["login" &] (-> (user/login-url) redirect constantly)
  ["logout" &] (-> (user/logout-url) redirect constantly)
  [[filename valid-filename->blob-key]] (partial serve-file filename)
  [[range-or-nothing valid-articles-range-journal]] (journal range-or-nothing)
  [[slug->tree valid-slug->tree]] (tree slug->tree)
  [&] (not-found))

(defroutes post-routes
  ["comment"] (wrap-params add-comment!))

(defroutes root-routes
  ["admin" &] {:get admin-get-routes
	       :post (wrap-params admin-post-routes)}
  [&]
  {:get get-routes
   :post post-routes})

(ae/def-appengine-app tlog-app #'root-routes)