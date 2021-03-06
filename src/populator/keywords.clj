(ns populator.keywords
  (:use clojure.java.io clojure.contrib.json clojure.set)
  (:import java.text.SimpleDateFormat
	   [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]))

; (defrecord keyword :last-updated :hits :cached-rating)

(defonce dt-format (SimpleDateFormat. "EEE, dd MMM yyyy HH:mm:ss Z"))
(defonce high-thres (* 1000 60 60))
(defonce low-thres (* 1000 60 60 4))

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

(defn- twitter-search-query
  [path]
  (read-json (reader (str "http://search.twitter.com/search.json" path))))

(defn- twitter-term-query
  [term]
  (twitter-search-query (str "?q=" term "&rpp=100")))

(defn- twitter-continue-query
  [json-result]
  (if-let [next-url (:next_page json-result)]
    (twitter-search-query next-url)
    {:results []}))

(defn- timed-query-results
  [json-result]
  (set map #(.getTime (.parse dt-format (:created_at %))) (:results json-result)))

(defn- update-keyword
  [keyword result]
  (let [recent-hits (timed-query-results result)
	combined-hits (union recent-hits (:hits keyword))]

    ; todo move the send-off higher up?
    ; could have :continue in assoc keyword below. later keywords-refresh
    ; runs through keywords after refresh, following any :continue until no more.
    (when-let [next-url (:next_page result)]
      (let [update-fn #(update-keyword % (twitter-continue-query % result))]
	(send-off *keywords* #(update-in % [(:word keyword)] update-fn))))
    
    (assoc keyword
      :last-hit-id (first (sort recent-hits))
      :hits (expire-old-hits combined-hits)
      :cached-rating (rate-hits combined-hits))))

(defn- update-keyword-new-query
  [keyword]
  (update-keyword keyword (twitter-search-query (:word keyword))))

(defn keywords-add
  [word]
  (let [keyword {:word word
		 :last-hit-id :unknown
		 :hits #{}
		 :cached-rating :unknown}]
    (if (not (contains? @*keywords* word))
      (do
	(send *keywords* assoc word keyword)
	(send-off *keywords* #(update-in % [word] update-keyword-new-query))))))

(defn keywords-rating
  "Returns the current rating of a keyword."
  [word]
  (:cached-rating (@*keywords* word)))

(defn keywords-refresh-one
  [word]
  (send-off *keywords* #(update-in % [word] update-keyword-new-query)))

(defn keywords-refresh
  "Refreshes all keywords by retreiving recent hits from
   Twitter and then caching a keywords rating and last hit
   id."
  []
  (doseq [k (keys @*keywords*)]
    (send-off *keywords* #(update-in % [k] update-keyword-new-query))))

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
