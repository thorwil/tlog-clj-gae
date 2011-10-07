(ns tlog.views.views
  (:use [ring.util.response :only [response]]
        tlog.views.parts
        [tlog.views.compose :only [defview defviews]]))


;; Views taking roles into consideration

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
			 option-noscript-warning]}]
  ;; POST views:
  [on-add-comment {:everyone [comments-rendition-recur
                              switch-comment-deleter-false]
                   :admin [option-comments-admin-editable
                           switch-comment-deleter-true]}
   true])


;; Views independent of roles

(defn plain
  [s]
  ((comp constantly response) s))

(defn list-slugs
  "Plain text, space-separated list of slugs."
  [slugs]
  (plain (interpose " " slugs)))