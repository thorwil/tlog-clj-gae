(ns tlog.conf)


;; Used in models.clj:

(def articles-per-journal-page 3)
(def articles-per-admin-page 5)
(def blobs-per-page 3)


;; Used in views.clj:

(def title-seperator " <- ")

(def meta-description "Interaction and visual design by Thorsten Wilms.")
(def font-link "http://fonts.googleapis.com/css?family=Lato:light,regular,regularitalic,bold,900")


;; Used in views.clj and atom-feed.clj:

(def title-main "Thorsten Wilms Design Solutions")
(def author "Thorsten Wilms")


;; Used in html.clj and atom-feed.clj:

(def copyright "Copyright 2011 Thorsten Wilms, unless otherwise noted.")


;; Used in atom-feed.clj:

(def articles-per-feed-page 10)
(def domain "http://www.thorstenwilms.com/")
(def author-email "self@thorstenwilms.com")