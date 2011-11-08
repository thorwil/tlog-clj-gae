(ns tlog.views.atom-feed
  "Functions for buidling an atom feed."
  (:use [hiccup.core :only [html defhtml escape-html]]
        [tlog.conf :only [domain feed-url title-main author author-email copyright]]
        [tlog.views.utility :only [ms-to-rfc-3339]]))


(defhtml feed
  [{:keys [feed-name latest buildup]}]
  (str "<?xml version=\"1.0\" encoding=\"utf-8\"?>")
  [:feed {:xmlns "http://www.w3.org/2005/Atom"}
   [:id feed-url]
   [:title title-main]
   [:link {:rel "self" :href (str "/atom/" feed-name)}]
   (when latest [:updated (ms-to-rfc-3339 latest)])
   [:author
    [:name author]
    [:email author-email]]
   buildup])

(defhtml entry
  [{:keys [slug title updated body]}]
  (let [link (str domain slug)]
    [:entry
     [:title title]
     [:id link]
     [:updated (ms-to-rfc-3339 updated)]
     [:link {:rel "alternate" :href link}]
     [:content {:type "html"} (escape-html body)]]))

(defn derive-latest
  "Take a map of articles (items). Return the newest timestamp found."
  [articles]
  (let [times (mapcat #(vals (select-keys % [:created :updated])) articles)]
    (apply max times)))

(defn feed-rendition
  [{:keys [items]}]
  (if (empty? items)
    {}
    {:latest (derive-latest items)
     :buildup (map entry items)}))