(ns populator.keywords
  (:use clojure.java.io clojure.contrib.json clojure.set)
  (:import java.text.SimpleDateFormat
	   [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]))

; (defrecord keyword :last-updated :hits :cached-rating)

(defonce dt-format (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss Z"))
(defonce high-thres (* 1000 60 60 12))
(defonce low-thres (* 1000 60 60 24))

(defonce *keywords* (agent {}))

(defn- now
  [offset]
  (+ (. System currentTimeMillis) offset))

(defn- expire-old-hits
  "Returns a hit list without any hits older than the low
   threshold."
  [hit-set]
  (set
   (filter #(> % (now (* -1 low-thres))) hit-set)))

(defn- rate-hits
  "Weights hits more recent than the high threshold twice
   that of those only more recent than the low threshold."
  [hit-set]
  (let [rate-fn #(cond
		  (> % (now (* -1 high-thres))) 2
		  (> % (now (* -1 low-thres)))  1
		  :otherwise                    0)]
    (reduce + (map rate-fn hit-set))))

(defn- query-recent-hits
  "Query twitter for hits since the last update"
  [query-param]
  (let [results (:results
		 (read-json
		  (reader
		   (str "http://search.twitter.com/search.json?q="
			query-param
			"&rpp=100"))))]
    (set (map #(.getTime (.parse dt-format (:created_at %))) results))))

(defn- update-keyword
  [keyword]
  (let [recent-hits (query-recent-hits (:word keyword))
        combined-hits (union recent-hits (:hits keyword))]
    (assoc keyword
      :last-hit-id (first (sort recent-hits))
      :hits (expire-old-hits combined-hits)
      :cached-rating (rate-hits combined-hits))))

(defn keywords-add
  [word]
  (let [keyword {:word word
		 :last-hit-id :unknown
		 :hits #{}
		 :cached-rating :unknown}]
    (if (not (contains? @*keywords* word))
      (do
	(send *keywords* assoc word keyword)
	(send-off *keywords* #(assoc % word (update-keyword keyword)))))))

(defn keywords-wait-for-add
  []
  (await *keywords*))

(defn keywords-rating
  "Returns the current rating of a keyword."
  [word]
  (:cached-rating (@*keywords* word)))

; todo broken
(defn keywords-refresh-one
  [word]
  (let [update-fn #(assoc % word (update-keyword (get-in % word)))]
    (send-off *keywords* update-fn)))

(defn keywords-refresh
  "Refreshes all keywords by retreiving recent hits from
   Twitter and then caching a keywords rating and last hit
   id."
  []
  (doseq [k (keys @*keywords*)]
    (let [update-fn #(assoc % k (update-keyword (get-in % k)))]
      (send-off *keywords* update-fn))))

(defn keywords-refresh-pmap
  []
  (doseq [updated-kw (pmap update-keyword (vals @*keywords*))]
    (send-off *keywords* assoc (:word updated-kw) updated-kw)))

(defn keywords-ranking
  "Returns the ranking of a set of keywords."
  [word-list]
  {:unranked (filter #(= :unknown (keywords-rating %)) word-list)
   :ranked   (reverse
	      (sort-by keywords-rating
		       (filter #(not= :unknown (keywords-rating %))
			       word-list)))})

(defn keywords-run
  []
  (def executor (ScheduledThreadPoolExecutor. 1))
  (doto executor
    (.scheduleWithFixedDelay keywords-refresh-pmap
			     0
			     low-thres
			     TimeUnit/MILLISECONDS)))

(defn keywords-stop
  []
  (.shutdown executor))
