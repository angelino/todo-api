(ns main
  (:require [io.pedestal.http.route :as route]
            [io.pedestal.http :as http]
            [io.pedestal.test :as test]))

(defn response [status body & {:as headers}]
  {:status status :body body :headers headers})

(def ok       (partial response 200))
(def created  (partial response 201))
(def accepted (partial response 202))

(def echo
  {:name :echo
   :enter (fn [context]
            (let [request (:request context)
                  response (ok context)]
              (assoc context :response response)))})

(defonce database (atom {}))

(def db-interceptor
  {:name :database-interceptor
   :enter (fn [context]
            (update context :request assoc :database @database))
   :leave (fn [context]
            (if-let [[op & args] (:tx-data context)]
              (do
                (apply swap! database op args)
                (assoc-in context [:request :database] @database))
              context))})

(defn make-list [name]
  {:name name
   :items {}})

(defn make-list-item [name]
  {:name name
   :done? false})

(def list-create
  {:name :list-create
   :enter (fn [context]
            (let [name (get-in context [:request :query-params :name] "Unnamed List")
                  new-list (make-list name)
                  db-id (str (gensym "l"))
                  url (route/url-for :list-view :params {:list-id db-id})]
              (assoc context
                     :tx-data [assoc db-id new-list]
                     :response (created new-list "Location" url))))})

; uri, verb, interceptor, route-name
(def routes
  (route/expand-routes
   #{["/todo" :get echo :route-name :list-query-form]
     ["/todo/:list-id" :get echo :route-name :list-view]
     ["/todo" :post [db-interceptor list-create]]
     ["/todo/:list-id/:item-id" :get echo :route-name :list-item-view]}))
(def service-map
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8890})

(defn start []
  (http/start (http/create-server service-map)))


;; For interactive development
(defonce server (atom nil))

(defn start-dev []
  (reset! server
          (http/start (http/create-server (assoc service-map ::http/join? false)))))

(defn stop-dev []
  (http/stop @server))

(defn restart []
  (stop-dev)
  (start-dev))


;; Test helper
(defn test-request [verb url]
  (test/response-for (::http/service-fn @server) verb url))
