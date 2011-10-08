(ns tlog.routes.routes
  "Map URL patterns to handlers."
  (:require [appengine-magic.core :as ae]
            [appengine-magic.services.user :as user]
            [tlog.routes.validators :as valid]
            [tlog.routes.handlers :as h])
  (:use [ring.middleware.params :only [wrap-params]]
	[ring.util.response :only [redirect]]
	[net.cgrand.moustache :only [app]]
	[clojure.string :only [join]]
	[appengine-magic.services.user :only [user-logged-in? user-admin?]]
	[appengine-magic.services.datastore :only [key-id]]))


(defmacro defroutes
  "def name to a moustache app form."
  [name & more]
  `(def ~name (app ~@more)))

(defroutes admin-get-routes
  ; Match "...file", "...file/", "...file/2-1" ...:
  ["file" &] (app [[index-range valid/blobs-range]] (h/file-form index-range))
  ["generate_upload_url"] (h/generate-upload-url!)
  ["queue-delete" [kind valid/kind] [identifier not-empty]] (wrap-params (partial h/queue-delete!
                                                                                  kind
                                                                                  identifier))
  ["cancel-delete" [identifier not-empty]] (wrap-params (partial h/unqueue-delete!
                                                                 identifier))
  ["slugs"] (h/list-slugs)
  [[from-to valid/articles-range-admin]] (h/list-articles from-to)
  ["write" &] (h/article-form)
  [&] (h/not-found))

(defroutes admin-post-routes ;; whole form is in wrap-params, see root-routes
  ["file-callback"] h/file-callback
  ["file_done"] h/file-done
  ["delete"] h/delete!
  ["move-article"] h/move-article!
  ["add-article"] h/add-article!
  ["update-article"] h/update-article!
  ["update-comment"] h/update-comment!)

(defroutes get-routes
  ;; Match for root, using default range, or match given index range:
  ["login" &] (-> (user/login-url) redirect constantly)
  ["logout" &] (-> (user/logout-url) redirect constantly)
  [[filename valid/filename->blob-key]] (partial h/serve-file filename)
  [[range-or-nothing valid/articles-range-journal]] (h/journal range-or-nothing)
  [[slug->tree valid/slug->tree]] (h/tree slug->tree)
  [&] (h/not-found))

(defroutes post-routes
  ["comment"] (wrap-params h/add-comment!))

(defroutes root-routes
  ["admin" &] {:get admin-get-routes
	       :post (wrap-params admin-post-routes)}
  [&]
  {:get get-routes
   :post post-routes})

(ae/def-appengine-app tlog-app #'root-routes)
