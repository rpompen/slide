(ns slide.core
  (:require [hoplon.core :as h :refer [defelem div h1 p text input with-init!
                                       button form for-tpl fieldset legend]]
            [hoplon.jquery]
            [javelin.core :as j :refer [cell cell= dosync]]
            [slide.rpc :as rpc]))

(with-init! (rpc/get-people))

(defelem main [_ _]
  (div :id "app"
       (h1 "Sample")
       "Gets data from file via JVM backend. "
       (button :type "button" :click #(rpc/get-file) "Get file")
       (p (text "~{rpc/file-data}"))
       (text "Gets items from CouchDB via REST interface, page ~{(inc (:curpage rpc/people-pages))}")
       (form
        (fieldset
         (legend "People " (for-tpl [[page data] (cell= (:bookmarks rpc/people-pages))]
                                    (button :type "button"
                                            :click #(rpc/get-people @page) (cell= (inc page)))))
         (for-tpl [{id "_id" :strs [name age]} rpc/people]
                  (div
                   (input :type "text" :value name :disabled true)
                   (input :type "text" :value age :disabled true)
                   (button :type "button" :click #(rpc/del-db @id) "Erase"))))
        (let [name (cell nil)
              age (cell nil)]
          (fieldset
           (legend "New person")
           (input :type "text" :placeholder "name"
                  :value name
                  :change #(reset! name @%))
           (input :type "number" :placeholder "age"
                  :value age
                  :change #(reset! age (int @%)))
           (button :type "button"
                   :click (fn []
                            (rpc/add-db @name @age)
                            (dosync (reset! name nil)
                                    (reset! age nil)))
                   "Add"))))))

(.replaceChild (.-body js/document)
               (main) (.getElementById js/document "app"))
