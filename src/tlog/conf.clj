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
(def feed-url "http://www.thorstenwilms.com/atom")


;; Used in atom-feed.clj:

(def articles-per-feed-page 10)
(def domain "http://www.thorstenwilms.com/")
(def author-email "self@thorstenwilms.com")


;; Used in html.clj:
;; Feeds of selected articles will change rarely, if ever, so they are hard-coded:
;; (def feeds '[journal
;;              planet-ubuntu ;; http://planet.ubuntu.com/
;;              planet-linuxaudio ;; http://planet.linuxaudio.org/
;;              graphicsplanet]) ;; http://www.graphicsplanet.org/
(def feeds (array-map "journal" true
                      "planet-ubuntu" true ;; http://planet.ubuntu.com/
                      "planet-linuxaudio" false ;; http://planet.linuxaudio.org/
                      "graphicsplanet" false)) ;; http://www.graphicsplanet.org/