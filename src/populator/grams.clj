(ns populator.grams
  (:use populator.keywords))

(defonce *grams* (ref {}))
(defonce *grams-ordered* (ref '()))
(defonce *high-id* (ref 1))

(defn grams-new
  [name word-list]
  (let [new-id @*high-id*
	new-gram {:id new-id :name name :words word-list}]
    (dosync
     (alter *grams* assoc new-id new-gram)
     (alter *grams-ordered* conj new-gram)
     (alter *high-id* inc)
     (doseq [w word-list] (keywords-add w)))
    new-id))

(defn grams-get
  [id]
  (get-in @*grams* [id]))

(defn grams-get-words
  [id]
  (get-in @*grams* [id :words]))

(defn grams-get-all
  []
  @*grams-ordered*)

