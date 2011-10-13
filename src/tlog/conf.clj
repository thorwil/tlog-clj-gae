(ns tlog.conf)


;; Configuration used in models.clj:

(def articles-per-journal-page 3)
(def articles-per-admin-page 5)
(def blobs-per-page 3)


;; Configuration used in views.clj:

(def title-seperator " <- ")

(def meta-description "Interaction and visual design by Thorsten Wilms.")
(def font-link "http://fonts.googleapis.com/css?family=Lato:light,regular,regularitalic,bold,900")

(def footer "Copyright 2011 Thorsten Wilms, unless otherwise noted.")


;; Configuration used in views.clj and atom-feed.clj

(def title-main "Thorsten Wilms Design Solutions")
(def meta-author "Thorsten Wilms")


;; Configuration used in atom-feed.clj

(def articles-per-feed-page 10)
(def domain "http://www.thorstenwilms.com/")