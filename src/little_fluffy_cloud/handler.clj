(ns little-fluffy-cloud.handler
  "A little REST-based cloud provider

  Start a VM:
    curl -X POST -H \"Content-Type: application/json\" http://localhost:3000/vms/test-01
  or
    "
  (:use compojure.core
        [ring.middleware.json :only [wrap-json-response wrap-json-params]]
        [ring.util.response :only [response]])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [vmfest.manager :as vmfest]
            [vmfest.virtualbox.machine :as machine]))

(def ^:dynamic *vbox-server* (vmfest/server "http://localhost:18083"))
(def ^:dynamic *default-image* :debian-6.0.2.1-64bit-v0.3)
(def ^:dynamic *default-hardware* :micro)

(defn find-vm [name]
  (vmfest/find-machine *vbox-server* name))

(defn create-vm [image hardware name]
  (if-let [vm (find-vm name)]
    {:error (format "image %s already exists" name)}
    (vmfest/instance *vbox-server* name image hardware)))

(defmacro with-vm [name [vm] & body]
  `(if-let [~vm (find-vm ~name)]
     (try
       ~@body
       (catch Exception e#
         {:status 501
          :headers {"Content-type" "text/html"}
          :body "Internal Error"}))
     {:status 404
      :headers {"Content-Type" "text/html"}
      :body  (format "VM named '%s' not found." ~name) }))

(defn- hardware-map [hardware-json]
  (let [model (:micro vmfest/*machine-models*)
        memory (get hardware-json "memory")
        model (if memory (assoc model :memory-size memory) model)
        cpus (get hardware-json "cpus")
        model (if cpus (assoc model :cpu-count cpus) model)]
    model))

(defroutes app-routes
  (GET "/" [] (response {:server (:url *vbox-server*)}))
  (GET "/images" [] (response {:images (map name (vmfest/models))}))
  (GET "/images/:name" [name]
       (response (vmfest/model-info (keyword name))))
  (GET "/vms" []
       (response {:vms (map
                        #(vmfest/get-machine-attribute % :name)
                        (vmfest/machines *vbox-server*))}))
  (GET "/vms/:name" [name]
       (with-vm name [vm]
         (response {:vm vm})))
  (GET "/vms/:name/ip" [name]
       (with-vm name [vm]
         (response {:ip (vmfest/get-ip vm)})))
  (POST "/vms/:name" [image hardware name]
        (let [image (or (keyword image) *default-image*)
              hardware (or (when hardware (hardware-map hardware))
                           *default-hardware*)]
          (response (create-vm image hardware name))))
  (PUT "/vms/:name/start" [name gui]
       (with-vm name [vm]
         (let [gui (if gui "gui" "headless")]
           (response (vmfest/start vm :session-type gui)))))
  (PUT "/vms/:name/stop" [name]
       (with-vm name [vm]
         (response (vmfest/stop vm))))
  (PUT "/vms/:name/power-down" [name]
       (with-vm name [vm]
         (response (vmfest/power-down vm))))
  (DELETE "/vms/:name" [name]
          (with-vm name [vm]
            (try (vmfest/power-down vm)
                 (Thread/sleep 1000)
                 (catch Exception e "do nothing"))
            (response (vmfest/destroy vm))))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      wrap-json-params
      handler/api
      wrap-json-response))

;; Create a new VM named "test-01" with the default OS and hardware models
;; curl -X POST -H "Content-Type: application/json" http://localhost:3000/vms/test-01
;; curl -X POST -H "Content-Type: application/json" http://localhost:3000/vms/test-01\
;;    -d '{"image": "debian-6.0.2.1-64bit-v0.3", "hardware": {"cpus": 1, "memory": 512}}' \
;;
;; Start a VM already created
;; curl -X PUT -H "Content-Type: application/json" http://localhost:3000/vms/test-01/start
;; curl -X PUT -H "Content-Type: application/json" http://localhost:3000/vms/test-01/start \
;;    -d '{"gui": false}
;;
;; Stopping a running VM (asking the OS to shut down)
;; curl -X PUT -H "Content-Type: application/json" http://localhost:3000/vms/test-01/stop
;;
;; powering down a running VM
;; curl -X PUT -H "Content-Type: application/json" http://localhost:3000/vms/test-01/power-down
;;
;; Deleting a VM
;; curl -X DELETE -H "Content-Type: application/json" http://localhost:3000/vms/test-01
;;
;; Listing VMs
;; curl -X GET http://localhost:3000/vms
;;
;; Inspecting a VM (not much info, just the internal ID for VBox)
;; curl -X GET http://localhost:3000/vms/test-01
;;
;; Listing available images
;; curl -X GET http://localhost:3000/images
;;
;; getting an image's info (broken at the moment in vmfest)
;; curl -X GET http://localhost:3000/images/debian-6.0.2.1-64bit-v0.3
