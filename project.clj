(defproject little-fluffy-cloud "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [compojure "1.1.1"]
                 [ring/ring-jetty-adapter "1.1.1"]
                 [vmfest "0.2.5"]
                 [ring/ring-json "0.1.0"]]
  :plugins [[lein-ring "0.7.1"]]
  :ring {:handler little-fluffy-cloud.handler/app}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.2"]]}})
