(ns slide.rpc
  (:require-macros
   [javelin.core :refer [defc defc=]]
   [cljs.core.async.macros :refer [go]])
  (:require
   [slide.shared :refer [port server db-port db-server db-name secure?]]
   [javelin.core :refer [cell]]
   [cljs-http.client :as http]
   [cljs-http.core :as hc]
   [chord.client :refer [ws-ch]]
   [clojure.string :as s]
   [cemerick.url :refer [url url-encode]]
   [cljs.core.async :refer [<! >! close!] :as async]))

;; CouchDB connection
(def urls (str "http" (when secure? \s) "://" db-server ":" db-port "/"))
(def urld (str urls db-name))
(def urlq (str urld "/_find"))
;; Merge with :json-params for authentication
(def db-auth {:basic-auth {:username "admin"
                           :password "Cl0jure!"}})

;; patch cljs-http to not mangle JSON
(defn json-decode-raw
  "JSON decode an object from `s`."
  [s]
  (let [v (when-not (s/blank? s) (js/JSON.parse s))]
    (when (some? v)
      (js->clj v))))

(defn wrap-json-response
  "Decode application/json responses."
  [client]
  (fn [request]
    (-> #(http/decode-body % json-decode-raw "application/json" (:request-method request))
        (async/map [(client request)]))))

(def request (-> hc/request
                 http/wrap-accept
                 http/wrap-form-params
                 http/wrap-multipart-params
                 http/wrap-edn-params
                 http/wrap-edn-response
                 http/wrap-transit-params
                 http/wrap-transit-response
                 http/wrap-json-params
                 wrap-json-response
                 http/wrap-content-type
                 http/wrap-query-params
                 http/wrap-basic-auth
                 http/wrap-oauth
                 http/wrap-method
                 http/wrap-url
                 http/wrap-channel-from-request-map
                 http/wrap-default-headers))

(defn qpost
  "Like #'request, but sets the :method and :url as appropriate."
  [url & [req]]
  (request (merge req {:method :post :url url})))


(defn qput
  "Like #'request, but sets the :method and :url as appropriate."
  [url & [req]]
  (request (merge req {:method :put :url url})))

(defn qget
  "Like #'request, but sets the :method and :url as appropriate."
  [url & [req]]
  (request (merge req {:method :get :url url})))

;; RPC launcher
(defc error nil)
(defc loading #{})

(defn launch-fn 
  "Launches RPC call for `f` in backend. Return value goes into cell."
  [f cl & args]
  (go
    (let [{:keys [ws-channel error] :as call} (<! (ws-ch (str "ws" (when secure? \s) "://" server (when-not secure? (str ":" port)) "/ws")
                                                {:format :transit-json}))]
      (swap! loading conj call)
      (if error
        (js/console.log "Error:" (pr-str error))
        (do
          (>! ws-channel {:type :rpc :f f :args args})
          (let [msg (:message (<! ws-channel))]
            (cond (= msg :castranil) (reset! cl nil)
                  (some? (:castraexpt msg)) (reset! error (:castraexpt msg))
                  :else (reset! cl msg)))))
      (close! ws-channel)
      (swap! loading disj call))))

;;; UUID generator of CouchDB
(defc uuids nil)

(defn get-uuid
  "Returns `n` or 1 UUIDs in a vector."
  [& n]
  (go (let [result
            (<! (qget (str "http" (when secure? \s) "://" db-server ":" db-port "/_uuids"
                               (when (some? n) (str "?count=" (first n))))))]
        (reset! uuids (:uuids (:body result))))))

;;; CRUD: CREATE RETRIEVE UPDATE DELETE
;;; CREATE
(defn doc-add
  "Add document to CouchDB and run callback for refresh."
  [m & {cb :cb :or {:cb identity}}]
  (go (let [uuid (-> (<! (qget (str urls "/_uuids"))) :body (get "uuids") first)
            result (<! (qput (str urld "/" uuid)
                                 (merge {:json-params m} db-auth)))]
        (when-not (:success result)
          (reset! error (:body result)))
        (cb))))

;;; RETRIEVE
(defn query
  "Fire Mango query to CouchDB.
   JSON query `m` will be sent to DB. Result gets sent to cell `cl`.
   An optional funtion `:func` is applied to the result set.
   `page` is the page number to get. `pages` is a hash-map containing bookmarks.
   Initialize that map as nil."
  [m cl & {:keys [cb func page-size page pages] :or {cb identity, func identity, page-size 25, pages (cell :none)}}]
  (go
    (let [result
          (<! (qpost urlq
                         (merge {:json-params
                                 (merge m {:limit page-size
                                           :bookmark (if (or (nil? page)
                                                             (= page 0)) nil
                                                         (get-in @pages [:bookmarks page]))})}
                                db-auth)))
          next-bookmark (-> result :body (get "bookmark"))]
      (when (or (= @pages :none) (empty? @pages)) (reset! pages {:bookmarks {0 nil}}))
      (if (:success result)
        (do (reset! cl (-> result :body (get "docs") func))
            (when (and (not= @pages :none)
                       (not (-> @pages :bookmarks vals set (contains? next-bookmark))))
              (swap! pages assoc-in [:bookmarks (inc page)]
                     next-bookmark))
            (when (not= @pages :none) (swap! pages assoc :curpage (or page 0))))
        (reset! error (:body result)))
      (cb))))

;;; UPDATE
(defn doc-update
  "Update document in CouchDB and run callback for refresh."
  [id m & {cb :cb :or {:cb identity}}]
  (go (let [old (-> (<! (qpost urlq (merge {:json-params {"selector" {"_id" id}}}
                                               db-auth)))
                    :body (get "docs") first)
            result (-> (<! (qput (str urld "/" id)
                                     (merge {:json-params (merge old m)} db-auth))))]
        (when-not (:success result)
          (reset! error (:body result)))
        (cb))))

;;; ATTACHMENT
(defn doc-attach
  "Attach file to document in CouchDB and run callback for refresh."
  [id f fname & {:keys [meta-info cb] :or {:cb identity}}]
  (go (let [old (-> (<! (qpost urlq (merge {:json-params {:selector {"_id" id}}}
                                           db-auth)))
                    :body (get "docs") first)
            rev (get old "_rev")
            meta-old (get old "meta")
            meta-new (assoc meta-old fname (first meta-info))
            result (-> (<! (qput (str urld "/" id "/" (url-encode fname) "?rev=" rev)
                                 (merge {:multipart-params [[fname f]]} db-auth))))]
        (if (:success result)
          (doc-update id {"meta" meta-new} identity)
          (reset! error (:body result)))
        (cb))))

(defn doc-del-attach
  "Delete attachment"
  [id fname & {cb :cb :or {:cb identity}}]
  (go (let [old (-> (<! (qpost urlq (merge {:json-params {:selector {"_id" id}}}
                                           db-auth)))
                    :body (get "docs") first)
            rev (get old "_rev")
            meta-old (get old "meta")
            meta-new (dissoc meta-old fname)
            result (<! (http/delete (str urld "/" id "/" (url-encode fname))
                                    (merge {:query-params {"rev" rev}} db-auth)))]
        (if (:success result)
          (doc-update id {"meta" meta-new} identity)
          (reset! error (:body result)))
        (cb))))

(defn doc-list-attach
  "List attachments"
  [id cl]
  (go (let [result (-> (<! (qpost urlq (merge {:json-params {:selector {"_id" id}}}
                                              db-auth)))
                       :body (get "docs") first (get "_attachments") keys vec)]
        (reset! cl result))))

;;; DELETE
(defn doc-delete
  "Delete document in CouchDB and run callback for refresh."
  [id & {cb :cb :or {:cb identity}}]
  (go (let [rev (-> (<! (qpost urlq (merge {:json-params {"selector" {"_id" id}}}
                                               db-auth)))
                    :body (get "docs") first (get "_rev"))
            result (<! (http/delete (str urld "/" id "?rev=" rev) db-auth))]
        (when-not (:success result)
          (reset! error (:body result)))
        (cb))))

;; segmented state + lenses
;; reduces load due to state modifications and allows easier refactoring
(def state (cell {}))

(defc= file-data    (get-in state [:io :file-data]) #(swap! state assoc-in [:io :file-data] %))
(defc= people       (get-in state [:io :people]) #(swap! state assoc-in [:io :people] %))
(defc= people-pages (get-in state [:ui :people-pages]) #(swap! state assoc-in [:ui :people-pages] %))

;; RPC to backend
;; Cell data is overwritten, not merged
(defn get-file [] (launch-fn 'get-file file-data))

;; Database

(defn get-people
  [& [page]]
  (query {"selector"
          {"type" "person"}
          "sort" [{"name" "asc"}]}
         people
         :page-size 4
         :pages people-pages
         :page  page))

(defn add-db [name age] (doc-add {"type" "person"
                                  "name" name
                                  "age" age}
                                 :cb (fn []
                                       (reset! people-pages {})
                                       (get-people))))

(defn del-db [id] (doc-delete id :cb (fn []
                                       (reset! people-pages {})
                                       (get-people))))

