(ns tlog.views.atom-feed
  "Functions for buidling an atom feed."
  (:use [hiccup.core :only [html defhtml escape-html]]
        [tlog.conf :only [domain title-main author author-email copyright]]
        [tlog.views.utility :only [ms-to-rfc-3339]]))


(defhtml feed
  [{:keys [latest buildup]}]
  (str "<?xml version=\"1.0\" encoding=\"utf-8\"?>")
  [:feed {:xmlns "http://www.w3.org/2005/Atom"}
   [:id (str domain "atom")]
   [:title title-main]
   [:link {:rel "self" :href "/atom"}]
   [:updated (ms-to-rfc-3339 latest)]
   [:author
    [:name author]
    [:email author-email]]
   buildup])

(defhtml entry
  [{:keys [slug updated body]}]
  (let [link (str domain slug)]
    [:entry
     [:title "foo"]
     [:id link]
     [:updated (ms-to-rfc-3339 updated)]
     [:link {:rel "alternate" :href link}]
     [:content {:type "html"} (escape-html body)]]))

(defn feed-rendition
  [{:keys [items] :as data}]
  (assoc data :buildup (for [i items]
                         (entry i))))
