(ns tlog.routes.handlers
  (:require [appengine-magic.services.blobstore :as blobs]
            [appengine-magic.services.channel :as chan]
            [tlog.models.models :as models]
            [tlog.views.views :as views]
            [tlog.conf :as conf])
  (:use [ring.util.response :only [response redirect]]
	[net.cgrand.moustache :only [alter-response]]
	[clojure.string :only [join]]
	[appengine-magic.services.user :only [user-logged-in? user-admin?]]))


(defn- roles
  "Return list of roles of the current user as keys."
  []
  (cons :everyone
	(when (and (user-logged-in?) (user-admin?)) [:admin])))

(defn- slugs
  []
  (conj (models/article-slugs) "admin" "login" "logout" "comment"))

;; Event handlers

(defn on-slugs-change!
  []
  (chan/send "trigger-on-slugs-change" (join " " (slugs))))


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

(defn list-articles
  [from-to]
  (views/admin (roles)
	       (assoc (models/articles-heads-paginated from-to conf/articles-per-admin-page) :title "Articles")))

(defn article-form
  []
  (views/article-form (roles) {:title "Write"
			       :token (chan/create-channel "trigger-on-slugs-change")
			       :slugs (slugs)}))

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
  (on-slugs-change!)
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
  (views/tree (roles) (assoc slug->tree :token (chan/create-channel "trigger-on-slugs-change")
                                        :slugs (slugs))))

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
  (views/on-add-comment (roles) {:comments (list (list (models/add-comment! form-params)))}))