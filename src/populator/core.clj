(ns populator.core
  (:use compojure.core populator.keywords populator.grams hiccup.core
	ring.adapter.jetty clojure.contrib.json hiccup.form-helpers
	hiccup.page-helpers ring.middleware.file)
  (:require [compojure.route :as route]))

(defmacro render-page
  [title & contents]
  `(html5
    [:head
     [:title ~title]
     (include-css "/main.css")]
    [:body
     [:div#left
      (link-to "/" [:h1.title "Populator"])
      [:h2.tag (str "Current popularity of a set of"
		    " people, places or anything else.")]]
     [:div#right ~@contents]]))

(defmacro render-json
  [contents]
  `{:headers {"Content-Type" "application/json"}
    :body (json-str ~contents)})

(defn render-hello
  []
  (render-page "Popugram"
   [:p (link-to "/gram" "Create a new Popugram")]
   [:h2 "Recent Popugrams"]
   (for [gram (take 10 (grams-get-all))]
     [:p
      (link-to (str "/gram/" (:id gram)) (:name gram))])))

(defn render-refresh
  []
  (keywords-refresh-pmap)
  (html5
   (link-to "/" "Home")
   [:p "Aaaahhhhhhhh... That hit the spot."]))
     
(defn render-not-found
  []
  (html5 [:p "Route not found"]))

(defn render-gram
  [id]
  (let [gram (grams-get (Integer/parseInt id))
	ranks (keywords-ranking (:words gram))]
    (render-page
     (str "Popugram - " (:name gram))
     [:h1 (:name gram)]
     (ordered-list
      (map #(str % " with a rating of " (keywords-rating %))
	   (:ranked ranks)))
     (unordered-list
      (map #(str % " not yet ranked")
	   (:unranked ranks))))))

(defn render-gram-form
  []
  (render-page
   "New Popugram"
   [:p "Create a Popugram with this handy form."]
   (form-to [:post "/gram"]
	    (label "name" "Give this Popugram a name:")
	    (text-field "name")
	    [:p "Provide some terms:"]
	    (for [x (range 1 11)]
	      [:div
	       (label (str x) (str x ". "))
	       (text-field (str x))])
	    (submit-button "Create"))))

(defn render-create-gram
  [name word-list]
  (let [gram-id (grams-new name word-list)]
    (render-page
     "Popugram"
     [:p
      (str "Your new Popugram, '" name "', can be found ")
      (link-to (str "/gram/" gram-id) "here")])))

(defn json-gram
  [id]
  (render-json (keywords-ranking (grams-get-words (Integer/parseInt id)))))

(defn json-rating
  [term]
  (do
    (keywords-add term)
    (keywords-wait-for-add)
    (render-json {:term term :rating (keywords-rating term)})))

(defn json-create-gram
  [name words]
  (render-json {:id (grams-new name words)}))

(defn collect-words
  [count params]
  (filter #(not= "" %)
	  (for [x (range 1 (inc count))]
	    (params (str x)))))

(defroutes popugram-user-routes
  (GET "/gram/:id" [id] (render-gram id))
  (GET "/gram" [] (render-gram-form))
  (POST "/gram" req (render-create-gram
		     (get-in req [:form-params "name"])
		     (collect-words 10 (:form-params req))))
  (GET "/rating/:word" [word] (keywords-add word))
  (GET "/" [] (render-hello))
  (route/not-found (render-not-found)))

(defroutes popugram-api-routes
  (GET "/api/gram/:id" [id] (json-gram id))
  (POST "/api/gram" req (json-create-gram))
  (GET "/api/rating/:term" [term] (json-rating term)))

(defroutes app
  popugram-api-routes
  popugram-user-routes)
