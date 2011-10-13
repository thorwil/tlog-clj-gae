(ns tlog.views.atom-feed
  "Functions for buidling an atom feed."
  (:use [hiccup.core :only [html defhtml]]
        [tlog.conf :only [domain title-main meta-author]]
        [tlog.views.utility :only [ms-to-rfc-3339]]))


(defhtml feed
  [{:keys [latest buildup]}]
  (str "<?xml version=\"1.0\" encoding=\"utf-8\"?>")
  [:feed {:xmlns "http://www.w3.org/2005/Atom"}
   [:id domain]
   [:title title-main]
   [:link {:rel "self" :href "/atom"}]
   [:updated (ms-to-rfc-3339 latest)]
   [:author [:name meta-author]]
   buildup])

(defhtml entry
  [_]
  [:entry
   [:title "foo"]
   [:link {:href "bar"}]
   [:id "urn:uuid:1225c695-cfb8-4ebb-aaaa-80da344efa6a"]
   [:updated "2003-12-13T18:30:02Z"]
   [:summary "Summary text."]])
