(ns tlog.views
  (:require [tlog.conf :as conf])
  (:use [clojure.java.io :only [resource]]
	[clojure.contrib.string :only [replace-str]]
	[clojure.contrib.generic.functor :only [fmap]]
	[ring.util.response :only [response content-type]]
	[hiccup.core]
	[hiccup.page-helpers :only [doctype]]))


;; Utility

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
;; These add key-value pairs to the map, so functions later in the pipeline can extract the results.

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
     (fmap admin-bar-item-linked admin-bar-item-strings))

(defn admin-bar-items
  "Takes a key for the plain item. Returns items for the admin-bar."
  [k]
  (assoc admin-bar-defaults k
	 (admin-bar-item-plain (k admin-bar-item-strings))))

(defn option-admin-bar*
  "Area with links for the logged in admin."
  [items]
  {:option-admin-bar
   (html
    [:nav {:id "admin-bar"}
     [:ul
      (for [v (vals items)]
	[:li v])]])})

(defn option-admin-bar
  ([]
     (option-admin-bar* admin-bar-defaults))
  ([k]
     (option-admin-bar* (admin-bar-items k))))

(defn option-slug-form
  "Mini-form to allow the admin to change the slug of an article."
  [{:keys [slug token]}]
  {:option-slug-form
   (html
    [:p {:class "slug"}
     [:label "Slug"]
     [:input {:type "text" :name "slug" :value slug :pattern "[a-zäöüß0-9_-]*"}]
     [:input {:type "submit" :value "Move" :disabled "disabled" :style "width:15em;"}]]
    [:script {:src "http://www.google.com/jsapi"}]
    [:script "google.load('jquery', '1.4');"]
    [:script {:src "/_ah/channel/jsapi"}]
    [:script (str "channel = new goog.appengine.Channel('" token "');")]
    [:script {:src "/scripts/slug.js"}])})

(def option-aloha-admin
     {:aloha-save-plugin
      [:script {:src "/scripts/aloha/plugins/tlog.Save/plugin.js"}]
      :aloha-admin-editables
      "$(function() {$('.admin-editable').aloha();});"})

(defn option-aloha
  [{:keys [collected-scripts aloha-save-plugin aloha-admin-editables]}] 
  {:collected-scripts
   (cons collected-scripts
	 (html
	  [:script {:src "/scripts/aloha/aloha.js"}]
	  [:script {:src "/scripts/aloha/plugins/tlog.Format/plugin.js"}]
	  [:script {:src "/scripts/aloha/plugins/com.gentics.aloha.plugins.Table/plugin.js"}]
	  [:script {:src "/scripts/aloha/plugins/com.gentics.aloha.plugins.List/plugin.js"}]
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
      $(function() {$('.editable').aloha();});"]))})

(defn article-form-js
  [{:keys [slugs token collected-scripts]}]
  {:collected-scripts
   (cons collected-scripts
	 (html
	  (let [slugs* (interpose "','" slugs)]
	    [:script "var slugs = ['" slugs* "'];"])
	    [:script {:type "text/javascript" :src "http://www.google.com/jsapi"}]
	    [:script {:type "text/javascript"} "google.load('jquery', '1.4');"]
	    [:script {:type "text/javascript" :src "/_ah/channel/jsapi"}]
	    [:script {:type "text/javascript"} (str "channel = new goog.appengine.Channel('" token "');")]
	    [:script {:type "text/javascript" :src "/scripts/add_article.js"}]))})

(defn option-comment-field
  [{:keys [collected-scripts]}]
  {:collected-scripts
   (cons collected-scripts
	 (html
	  [:script {:src "/scripts/comment.js"}]))})

(def option-footer
     {:option-footer
      (html [:footer [:p conf/footer]])})

(def option-noscript-warning
     {:option-noscript-warning
      (html [:noscript [:div#noscript-warning "This won't work with JavaScript disabled ;)"]])})


;; Switchables

(defn switch-title-linked-true
  "Do wrap the title in a link, for use in the journal."
  [{:keys [id slug title]}]
  {:switch-title-linked
   (html [:a.article-link {:id (str "title_" id), :href (str "/" slug)} title])})

(defn switch-title-linked-false
  "Do not wrap the title in a link, as that link would be to where we already are."
  [{:keys [id title]}]
  {:switch-title-linked
   (html [:span {:id (str "title_" id), :class "admin-editable"} title])})

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
    time-stamps
    [:div {:id id :class (str "article-body hyphenate admin-editable " css-class)} body]]])

(defn linked-or-plain
  [link text]
  (if (empty? link)
    text
    [:a {:href link} text]))

(defhtml comment-rendition
  [{:keys [id css-class head time-stamps index link author body updated delete-queued]}
   children
   switch-comment-deleter]
  [:div {:class (str "branch" (when delete-queued " delete-queued"))}
   [:div {:id id, :class (str "comment " css-class " index-" index (when head " head"))}
    time-stamps
    [:p.meta
     [:a.comment-anchor {:name index :href (str "#" index)} (str "#" index " ")]
     [:span.author (linked-or-plain link author) ": "]
     (switch-comment-deleter id delete-queued)]
    [:div.body body]
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
  "Takes parent ID and a list of trees, each tree consisting of a comment and its children. Render
   nested Comments."
  [parent comments switch-comment-deleter]
  (cons
   (for [tree comments]
     (let [t (first tree)
	   ts (rest tree)]
       (comment-rendition (into t (derive-from-times t))
			  (comments-rendition-recur (:id t) ts switch-comment-deleter)
                          switch-comment-deleter)))
   ;; Place comment field as a last sibling:
   (comment-field parent (-> comments last count))))

(defhtml comments-rendition
  [parent comments switch-comment-deleter]
  [:div#comments
   [:h3 "Comments"]
   [:noscript [:p "Without JavaScript, you cannot add comments, here!"]]
   [:div {:class (when (empty? comments) "empty")}
          (comments-rendition-recur parent comments switch-comment-deleter)]])

(defhtml tree-rendition
  [{:keys [comments id switch-comment-deleter] :as all}]
  (article-rendition (into all
			   (derive-from-times all)))
  (comments-rendition id comments switch-comment-deleter))

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
  [:dl
   [:dt [:label "Title"]]
   [:dd [:input {:type "text" :name "title" :autofocus "autofocus" :required "required"}]]
   [:dt [:label "Slug"]]
   [:dd [:input {:type "text" :name "slug" :required "required" :pattern "[a-zäöüß0-9_-]*"}]]]
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

(defmacro defviews
  "Macro for defining one view function per given vector."
  [& vs]
  (cons 'do
	(for [v vs]
	  (let [[name steps-per-role*] v
		;; If :everyone is not specified, default to an empty vector for it:
		steps-per-role (into {:everyone []} steps-per-role*)]
	    `(defn ~name
	       [roles# m#]
	       (let [ss*# (flatten (map ~steps-per-role roles#))
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
		 (((comp-view (cons identity fs#))) (into m# ds#))))))))

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
	 :admin [option-aloha-admin
                 switch-comment-deleter-true
		 option-slug-form
		 (option-admin-bar)]}]
  [not-found {:everyone [not-found-rendition
			 option-footer]
	      :admin [(option-admin-bar)]}]
  ;; Admin views:
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


;; POST views (independent of roles)

(defn on-add-comment
  "Answer for add-comment POST handler. Return comment-rendition."
  [roles comment* following]
  (-> (comment-rendition (reduce into [comment*
				       (derive-from-times comment*)
				       (when (zero? following) {:head "true"})])
			 (comment-field (:id comment*) following)
                         (if (some #{:admin} roles)
                           comment-deleter
                           any->nil))
      response
      content-type-html))