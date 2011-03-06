
(defproject populator "0.0.1"
  :description "See the popularity of terms in real-time."
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
		 [compojure "0.5.3"]
		 [ring/ring-jetty-adapter "0.3.1"]
		 [hiccup "0.3.1"]]
  :dev-dependencies [[swank-clojure "1.2.1"]
		     [clj-ssh "0.2.0"]
		     [lein-ring "0.3.2"]]
  :jvm-opts ["-Xmn500M" "-Xms2000M" "-Xmx2000M" "-server"]
  :ring {:handler populator.core/app})
