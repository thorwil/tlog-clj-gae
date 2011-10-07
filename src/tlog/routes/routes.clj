(ns tlog.routes.routes
  (:require [appengine-magic.core :as ae]
            [appengine-magic.services.user :as user])
  (:use [ring.middleware.params :only [wrap-params]]
	[ring.util.response :only [redirect]]
	[net.cgrand.moustache :only [app]]
	[clojure.string :only [join]]
	[appengine-magic.services.user :only [user-logged-in? user-admin?]]
	[appengine-magic.services.datastore :only [key-id]]
        tlog.routes.validators
        tlog.routes.handlers))


(defmacro defroutes
  "def name to a moustache app form."
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
