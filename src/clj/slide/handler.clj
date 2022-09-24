(ns slide.handler
  (:require
   [slide.shared                   :refer [server]]
   [compojure.core                 :refer [defroutes GET]]
   [compojure.route                :refer [resources not-found]]
   [ring.middleware.defaults       :refer [wrap-defaults api-defaults]]
   [ring.middleware.resource       :refer [wrap-resource]]
   [ring.middleware.session        :refer [wrap-session]]
   [ring.middleware.not-modified   :refer [wrap-not-modified]]
   [ring.middleware.content-type   :refer [wrap-content-type]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [ring.util.response             :refer [content-type resource-response]]
   [ring.middleware.cors           :refer [wrap-cors]]))

(defroutes app-routes
  (GET "/" req
    (-> "public/index.html"
        (resource-response)
        (content-type "text/html")))
  (resources "/")
  (not-found "Oups! This page doesn't exist! (404 error)"))

(def app
  (-> app-routes
      (wrap-session {:store (cookie-store {:key (byte-array (map int "a 16-byte secret"))})})
      (wrap-defaults api-defaults)
      (wrap-resource "public")
      (wrap-cors :access-control-allow-origin (re-pattern (str "https://" server "/"))
                 :access-control-allow-methods [:get :put :post :delete])
      (wrap-content-type)
      (wrap-not-modified)))
