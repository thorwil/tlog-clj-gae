(defproject tlog "0.3.0-SNAPSHOT"
  :description "A blog for Google App Engine"
  :repositories {"sonatype-oss-public" "https://oss.sonatype.org/content/groups/public/"}
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/algo.monads "0.1.1-SNAPSHOT"]
                 [org.clojure/math.numeric-tower "0.0.1-SNAPSHOT"]
		 [net.cgrand/moustache "1.0.0"]
		 [hiccup "0.3.6"]]
  :dev-dependencies [[appengine-magic "0.4.6-SNAPSHOT"]])
