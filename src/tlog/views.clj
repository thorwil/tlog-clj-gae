(ns tlog.views
  (:require [tlog.conf :as conf])
  (:use [clojure.java.io :only [resource]]
	[ring.util.response :only [response content-type]]
	[hiccup.core]
	[hiccup.page-helpers :only [doctype]]))


;; Utility

;; Taken from Clojure 1.2 clojure.contrib.def:
(defn name-with-attributes
  "To be used in macro definitions. Handles optional docstrings and attribute maps for a name to be
   defined in a list of macro arguments. If the first macro argument is a string, it is added as a
   docstring to name and removed from the macro argument list. If afterwards the first macro
   argument is a map, its entries are added to the name's metadata map and the map is removed from
   the macro argument list. The return value is a vector containing the name with its extended
   metadata map and the list of unprocessed macro arguments."
  [name macro-args]
  (let [[docstring macro-args] (if (string? (first macro-args))
                                 [(first macro-args) (next macro-args)]
                                 [nil macro-args])
        [attr macro-args] (if (map? (first macro-args))
                            [(first macro-args) (next macro-args)]
                            [{} macro-args])
        attr (if docstring
               (assoc attr :doc docstring)
               attr)
        attr (if (meta name)
               (conj (meta name) attr)
               attr)]
    [(with-meta name attr) macro-args]))

;; Taken from Clojure 1.2 clojure.contrib.generic.functor, fmap, defmethod for IPersistentMap:
(defn map-map
  [f m]
  (into (empty m) (for [[k v] m] [k (f v)])))

(defn ms-to*
  "Create a function that expects a millisecond UNIX timstamp, to convert it to a date/time string."
  [format*]
  (let [format (java.text.SimpleDateFormat. format*)]
    (.setTimeZone format (java.util.TimeZone/getTimeZone "GMT"))
    #(.format format (java.util.Date. (long %)))))

(def ms-to-day (ms-to* "yyyy-MM-dd"))
(def ms-to-day-time (ms-to* "yyyy-MM-dd '<span class=\"hour-minute\">'H:mm'</span>'"))
(def ms-to-datetime (ms-to* "yyyy-MM-dd'T'H:mm:ss'+00:00'"))

(defmacro when-> [x & fs]
  `(when ~x (-> ~x ~@fs)))

(defn any->nil
  "Take whatever arguments and always return nil. Useful for neutralizing arguments in Hiccup forms."
  [& more]
  nil)


;; Optional HTML parts
;; These add key-value pairs to the map that is passed through the functions that build a view, so
;; functions later in the pipeline can extract the results.

(defmacro defopt
  "Take name, optional doc-string, argument-list, body and optional keyname. Use name to create a
   keyname, if none is given. Return a form that returns the body as value to the keyname in a
   hash-map."
  [name* & more]
  (let [[name args*] (name-with-attributes name* more)
        [args body] args*
        k (or (nth args* 2 nil) (keyword name*))]
    `(defn ~name ~args
       {~k ~body})))

(def admin-bar-item-strings
  (array-map :list ["List" "/admin"]
             :write ["Write" "/admin/write"]
             :file ["File" "/admin/file"]
             :logout ["Log out" "/logout"]))

(defhtml admin-bar-item-linked
  [[text link]]
  [:a {:href link} text])

(defhtml admin-bar-item-plain
  [[text link]]
  [:span text])

(def admin-bar-defaults
  (map-map admin-bar-item-linked admin-bar-item-strings))

(defn admin-bar-items
  "Takes a key for the plain item. Returns items for the admin-bar."
  [k]
  (assoc admin-bar-defaults k
	 (admin-bar-item-plain (k admin-bar-item-strings))))

(defopt option-admin-bar*
  "Area with links for the logged in admin."
  [items]
  (html
   [:nav {:id "admin-bar"}
    [:ul
     (for [v (vals items)]
       [:li v])]])
  :option-admin-bar)

(defn option-admin-bar
  ([]
     (option-admin-bar* admin-bar-defaults))
  ([k]
     (option-admin-bar* (admin-bar-items k))))

(defopt option-slug-form
  "Mini-form to allow the admin to change the slug of an article."
  [{:keys [slug token]}]
  (html
   [:p#slug
    [:label "Slug"]
    [:input {:type "text" :name "slug" :value slug :pattern "[a-zäöüß0-9_-]*"}]
    [:input {:type "submit" :value "Move" :disabled "disabled" :style "width:15em;"}]]
   [:script {:src "http://www.google.com/jsapi"}]
   [:script "google.load('jquery', '1.4');"]
   [:script {:src "/_ah/channel/jsapi"}]
   [:script (str "channel = new goog.appengine.Channel('" token "');")]
   [:script {:src "/scripts/slug.js"}]))

(def option-aloha-admin
  {:aloha-save-plugin
   [:script {:src "/scripts/aloha/plugins/tlog.Save/plugin.js"}]
   :aloha-admin-editables
   "$(function() {$('.admin-editable').aloha();});"})

(defopt option-aloha
  [{:keys [collected-scripts aloha-save-plugin aloha-admin-editables]}] 
  (cons collected-scripts
        (html
         [:script {:src "/scripts/aloha/aloha.js"}]
         [:script {:src "/scripts/aloha/plugins/tlog.Format/plugin.js"}]
         [:script {:src "/scripts/aloha/plugins/com.gentics.aloha.plugins.Table/plugin.js"}]
         [:script {:src "/scripts/aloha/plugins/com.gentics.aloha.plugins.List/plugin.js"}]
         [:script {:src "/scripts/aloha/plugins/com.gentics.aloha.plugins.Link/plugin.js"}]
         aloha-save-plugin
         [:script
          "GENTICS.Aloha.settings = {
               'plugins': {
                 'tlog.Format': {
                      // all elements with no specific configuration get this configuration
	              config: ['strong', 'em', 'sub', 'sup', 'ol', 'ul', 'p', 'title', 'h1', 'h2',
                               'h3', 'h4', 'h5', 'h6', 'pre', 'removeFormat'],
	              editables: {
                          // no formatting allowed for title
                          '.title': [ ]
	              }
	          },
                  'com.gentics.aloha.plugins.List': {
                      config: ['ul', 'ol'],
                      editables: {
                          // no lists allowed for title
		          '.title': [ ]
                      }
                  }
             }
           }
           "
          aloha-admin-editables
          "
           $(function() {$('.editable').aloha();});"]))
  :collected-scripts)

(def option-comments-admin-editable {:option-comments-admin-editable "admin-editable"})

(defopt option-comment-field
  [{:keys [collected-scripts]}]
  (cons collected-scripts
        (html
         [:script {:src "/scripts/comment.js"}]))
  :collected-scripts)

(defopt article-form-js
  [{:keys [slugs token collected-scripts]}]
  (cons collected-scripts
        (html
         (let [slugs* (interpose "','" slugs)]
           [:script "var slugs = ['" slugs* "'];"])
         [:script {:type "text/javascript" :src "http://www.google.com/jsapi"}]
         [:script {:type "text/javascript"} "google.load('jquery', '1.4');"]
         [:script {:type "text/javascript" :src "/_ah/channel/jsapi"}]
         [:script {:type "text/javascript"} (str "channel = new goog.appengine.Channel('" token "');")]
         [:script {:type "text/javascript" :src "/scripts/article.js"}]))
  :collected-scripts)

(def option-footer
     {:option-footer
      (html [:footer [:p conf/footer]])})

(def option-noscript-warning
     {:option-noscript-warning
      (html [:noscript [:div#noscript-warning "This won't work with JavaScript disabled ;)"]])})


;; Switchables

(defopt switch-title-linked-true
  "Do wrap the title in a link, for use in the journal."
  [{:keys [id slug title]}]
  (html [:a.article-link {:id (str "title_" id), :href (str "/" slug)} title])
  :switch-title-linked)

(defopt switch-title-linked-false
  "Do not wrap the title in a link, as that link would be to where we already are."
  [{:keys [id title]}]
  (html [:span {:id (str "title_" id), :class "admin-editable"} title])
  :switch-title-linked)

;; on-add-comment will need this directly ...
(def comment-deleter
  (fn [id delete-queued] (let [[class link text] (if delete-queued
                                                   ["cancel-delete" "/admin/cancel-delete/" "Cancel deletion"]
                                                   ["do-delete" "/admin/queue-delete/comment/" "Delete"])]
                           (html [:a {:class (str "comment-deleter " class) :href (str link id)} text]))))

;; ... but it also has to be an option for normal views, thus wrapped in a map:
(def switch-comment-deleter-true
  {:switch-comment-deleter
   comment-deleter})

(def switch-comment-deleter-false
  {:switch-comment-deleter
   any->nil})


;; HTML Parts

(def title-with
     ^{:doc "Build string for the <head>, <title> tag."}
     #(str %
	   (when (not-empty %) conf/title-seperator)
	   conf/title-main))

(defhtml base
  "Outer page skeleton."
  [{:keys [title
	   option-noscript-warning
	   buildup
	   option-admin-bar
	   collected-scripts
	   option-footer]}]
  (doctype :html5)
  [:html {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:meta {:name "description" :content conf/meta-description}]
    [:meta {:name "author" :content conf/meta-author}]
    [:title (title-with title)]

    [:link {:href conf/font-link
	    :rel "stylesheet"
	    :type "text/css"}]
    [:link {:rel "stylesheet" :href "/main.css" :type "text/css"}]
    
    "<!--[if lt IE 9]>"
    [:script {:src "//html5shiv.googlecode.com/svn/trunk/html5.js"}]
    "<![endif]-->"

    [:script {:src "/scripts/hyphenator.js" :type "text/javascript"}]
    [:script {:type "text/javascript"}
     "Hyphenator.config({remoteloading : false});"
     "Hyphenator.run();"]]
   [:body
    [:div#top option-admin-bar]
    option-noscript-warning
    [:div#main
     [:header
      [:h1 conf/title-main]
      [:nav
       [:ul
	[:li [:a {:href "/"} "Home"]]
	[:li [:a {:href "/portfolio"} "Portfolio"]]
	[:li [:a {:href "/about"} "About"]]]]]
     [:div#content buildup]
     option-footer]]
   collected-scripts])

(defn pair-to-range-str
  [[a b]]
  (str a "-" b))

(defhtml page-navigation
  "Render page navigation."
  [[headwards tailwards] url-base]
  [:nav#pages
   (if tailwards
     [:a {:href (str url-base (pair-to-range-str tailwards))} "Newer"]
     [:span "Newer"])
   (if headwards
     [:a {:href (str url-base (pair-to-range-str headwards))} "Older"]
     [:span "Older"])])

(defn maybe-page-navigation
  "Insert page navigation, if there are other pages."
  [[headwards tailwards] url-base]
  (when (or headwards tailwards)
    (page-navigation [headwards tailwards] url-base)))

(defhtml time*
  [t attr-map]
  [:time (into {:datetime (ms-to-datetime t)} attr-map) (ms-to-day-time t)])

(defhtml time-created
  [t]
  [:p (time* t {:pubdate "pubdate"})])

(defhtml time-updated
  [t]
  [:p "Updated:" [:br] (time* t {})])

(defn derive-from-times
  "Derive everything that depends on whether there has been an update.
   Thus prepare for only showing time of update, if one happened,
   and for setting CSS classes accordingly.
   Required before article-rendition."
  [{:keys [created updated]}]
  (let [[maybe-updated css-class] (if (= created updated)
				    [nil "not-updated"]
				    [(time-updated updated) "updated"])]
    {:time-stamps (html
		   [:div.times
		    (time-created created)
		    maybe-updated])
     :css-class css-class}))

(defhtml article-rendition
  "Full article content to be used once on single pages and several times in the journal."
  [{:keys [id title created updated body
	   option-slug-form switch-title-linked
	   time-stamps css-class]}]
  [:article
   [:header
    option-slug-form
    [:h2 switch-title-linked]
    time-stamps]
    [:div {:id id :class (str "article-body hyphenate admin-editable " css-class)} body]])

(defn linked-or-plain
  [link text]
  (if (empty? link)
    text
    [:a {:href link} text]))

(defhtml comment-rendition
  [{:keys [id css-class head time-stamps index link author body updated delete-queued
           option-comments-admin-editable]}
   children
   switch-comment-deleter]
  [:div {:class (str "branch" (when delete-queued " delete-queued"))}
   [:div {:id id, :class (str "comment " css-class " index-" index (when head " head"))}
    time-stamps
    [:p.meta
     [:a.comment-anchor {:name index :href (str "#" index)} (str "#" index " ")]
     [:span {:id (str "comment-author_" id)
             :class (str "author " option-comments-admin-editable)}
             (linked-or-plain link author) ": "]
     (switch-comment-deleter id delete-queued)]
    [:div {:id (str "comment-body_" id)
           :class (str "body " option-comments-admin-editable)} body]
    children]])

(defhtml comment-field
  "Takes a parent comment ID and the number of comments that follow on the same level. Renders
   Aloha editable to be placed at every end in the comment tree. Wrapped in another div that will
   collect the fields and button that may be inserted below the initial field."
  [parent following]
  [:div.comment-form
   [:div {:class "hyphenate editable start-blank"
	  :onmouseover (str "configureField(" parent ", this, " following ");")}
    [:span.internal-label "Reply"]]])

(defn comments-rendition-recur
  "Takes parent ID and a list of branches, each consisting of a comment and its children. Renders
   nested Comments."
  [{:keys [id comments switch-comment-deleter] :as params}
   following] ;; id refers to parent
  (cons
   (let [total (count comments)]
     (map (fn [done [branch & branches]]
            (comment-rendition (reduce into [branch
                                             (derive-from-times branch)
                                             (select-keys params [:option-comments-admin-editable])])
                               (comments-rendition-recur (assoc params :id (:id branch)
                                                                       :comments branches)
                                                         (- total done))
                               switch-comment-deleter))
          (range 1 Double/POSITIVE_INFINITY) ;; effectively up to (inc total)
          comments))
   ;; Place comment field as a last sibling:
   (comment-field id following)))

(defhtml comments-rendition
  [{:keys [comments] :as params}]
  [:div#comments
   [:h3 "Comments"]
   [:noscript [:p "Without JavaScript, you cannot add comments, here!"]]
   [:div {:class (when (empty? comments) "empty")}
    (comments-rendition-recur params 0)]])

(defhtml tree-rendition
  [params]
  (article-rendition (into params
			   (derive-from-times params)))
  (comments-rendition params))

(defhtml journal-li
  "<li> element with a whole article."
  [i]
  [:li
   (article-rendition (reduce into [i
				    (derive-from-times i)
				    (switch-title-linked-true i)]))])

(defhtml journal-rendition
  [buildup]
  [:ul#journal
   (for [i (:items buildup)]
     (journal-li i))]
  (maybe-page-navigation ((juxt :headwards :tailwards) buildup) "/"))

(defhtml admin-articles-table-row
  [slug delete-queued title]
  [:tr
   (let [[link label] (if delete-queued
			["/admin/cancel-delete/" "Cancel deletion"]
			["/admin/queue-delete/article/" "Delete"])]
     [:td [:a.delete {:href (str link slug)} label]])
   [:td [:a.view {:href (str "/" slug)} title]]])

(defhtml admin-rendition
  [buildup]
  [:h2 "Articles"]
  [:table#stored-items
   (for [i (:items buildup)]
     (admin-articles-table-row (:slug i) (:delete-queued i) (:title i)))]
  (maybe-page-navigation ((juxt :headwards :tailwards) buildup) "/admin/"))

(defhtml article-form-rendition
  [_]
  [:h2 "Write Article"]
  [:table.form
   [:tr
    [:td [:label "Title"]]
    [:td [:input {:type "text" :name "title" :autofocus "autofocus" :required "required"}]]]
   [:tr
    [:td [:label "Slug"]]
    [:td [:input {:type "text" :name "slug" :required "required" :pattern "[a-zäöüß0-9_-]*"}]]]]
  [:div {:id "slug" :class "article-body hyphenate admin-editable start-blank"} ""]
  [:input {:type "submit" :value "Add new article" :disabled "disabled"}])

(defhtml file-form-tr
  [{:keys [filename delete-queued]}]
  [:tr
   (let [[a c] (if delete-queued
		 ["/admin/cancel-delete/" "Cancel deletion"]
		 ["/admin/queue-delete/blob/" "Delete"])]
     [:td [:a {:href (str a filename)} c]])
   [:td [:a {:href (str "/" filename) :class "view"} filename]]])

(defhtml file-form-rendition
  [buildup]
  [:h2 "File upload"]
  [:input {:type "file" :name "files" :multiple "" :style "height: 1.5em; margin-bottom: 1.5em;"}]
  [:table#stored-items
   (for [i (:items buildup)]
     (file-form-tr i))]
  [:script {:type "text/javascript" :src "http://www.google.com/jsapi"}]
  [:script {:type "text/javascript"} "google.load('jquery', '1.4');"]
  [:script {:type "text/javascript" :src "/scripts/file.js"}]
  (maybe-page-navigation ((juxt :headwards :tailwards) buildup) "/admin/file/"))

(def not-found-rendition
     {:buildup (html [:p "Page not found."])})

(def not-allowed-rendition
     {:buildup (html [:p "You do not have the necessary permissions to access this page."])})


;; Views taking roles into consideration

(defn wrap-into
  "Wrapper that passes on a map to the given function.
   The result from the wrapped function is inserted/updated-within the map,
   with :buildup as default key. A wrapped function may specifiy a different key
   by evaluating to a map with a single key-value pair."
  [f]
  #(let [r (f %)]
     (into % (if (map? r)
	       r
	       {:buildup r}))))

(defn content-type-html
  [r]
  (content-type r "text/html"))

(defn comp-view
  "Compose functions to build a view.
   Functions are applied right to left, thus need to be listed from outer to inner."
  [fs]
  (let [wfs (map wrap-into fs)]
    #(comp constantly content-type-html response base (apply comp wfs))))

(defn assoc-fn
  "Return a vector v with function f applied to the element at index i."
  [v i f]
  (assoc v i (f (nth v i))))

(defn filter-split
  "Split vector v in 2, based on predicate pred. In true, false order."
  [pred v]
  (reduce #(assoc-fn %1
		     (if (pred %2) 0 1)
		     (fn [x] (conj x %2)))
	  [[] []]
	  v))

(defmacro defview
  "Macro for defining a view. Takes a vector with a name and map with vectors per role."
  [[name per-role*]]
  ;; If :everyone is not specified, default to an empty vector for it:
  (let [per-role (into {:everyone []} per-role*)]
    `(defn ~name
       [roles# m#]
       (let [ss*# (flatten (map ~per-role roles#))
             ;; The constraint in web.xml should protect the admin-only routes, but fails at
             ;; least in development mode. Deliver not-allowed, if there would be no view
             ;; functions otherwise:
             ss# (first (filter not-empty
                                [ss*#
                                 [not-allowed-rendition]]))
             ;; Separate functions from defs:
             [fs# ds#] (filter-split fn? ss#)]
         ;; Compose all functions. Assemble argument map from key-value pairs
         ;; from the defs and the view's argument map:
         (((comp-view (cons identity fs#))) (into m# ds#))))))

(defn macro-for-each
  "Take a quoted macro name and a vector. Return a do form with lists forming macro and vector element pairs."
  [macro xs]
  (cons 'do
	(for [x xs]
          (list macro x))))

(defmacro defviews
  "Call defview for each given vector."
  [& vs]
  (macro-for-each 'defview vs))

(defviews
  ;; Visitor/admin views:
  [journal {:everyone [journal-rendition
  		       option-footer]
  	    :admin [option-aloha
		    option-aloha-admin
  		    (option-admin-bar)]}]
  [tree {:everyone [tree-rendition
                    switch-comment-deleter-false
		    switch-title-linked-false
		    option-footer
		    option-comment-field
		    option-aloha]
	 :admin [option-comments-admin-editable
                 option-aloha-admin
                 switch-comment-deleter-true
		 option-slug-form
		 (option-admin-bar)]}]
  [not-found {:everyone [not-found-rendition
			 option-footer]
	      :admin [(option-admin-bar)]}]
  ;; Admin only views:
  [admin {:admin [admin-rendition
		  (option-admin-bar :list)
		  option-noscript-warning]}]
  [file-form {:admin [file-form-rendition
		      (option-admin-bar :file)
		      option-noscript-warning]}]
  [article-form {:admin [article-form-rendition
			 option-aloha
			 option-aloha-admin
			 article-form-js
			 (option-admin-bar :write)
			 option-noscript-warning]}])


;; Views independent of roles

(defn plain
  [s]
  ((comp constantly response) s))

(defn list-slugs
  "Plain text, space-separated list of slugs."
  [slugs]
  (plain (interpose " " slugs)))


;; POST views

(defn on-add-comment
  "Answer for add-comment POST handler. Return comment-rendition."
  [roles comment* following]
  (let [[option-comments-admin-editable* comment-deleter*] (if (some #{:admin} roles)
                                                             [option-comments-admin-editable comment-deleter]
                                                             [nil                            any->nil])]
    (-> (comment-rendition (reduce into [comment*
                                         (derive-from-times comment*)
                                         option-comments-admin-editable*
                                         (when (zero? following) {:head "true"})])
                           (comment-field (:id comment*)
                                          following)
                           comment-deleter*)
        response
        content-type-html)))