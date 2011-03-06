(ns populator.keywords-test
  (:use clojure.test populator.keywords))

(testing "Rate hits returns 2 for hits less than a minute ago, 1 for hits
          less than an hour ago and 0 for hits older than an hour."
  (is (= 2 (rate-hits #{(. System currentTimeMillis)})))
  (is (= 1 (rate-hits #{(now (* -1 30 60 1000))})))
  (is (= 0 (rate-hits #{(now (* -1 95 60 1000))}))))

(testing "Expire old hits removes hits that are older than an hour."
  (is
   1
   (count
    (expire-old-hits
     #{(now (* -1 95 60 1000))
       (now (* -1 30 60 1000))}))))
