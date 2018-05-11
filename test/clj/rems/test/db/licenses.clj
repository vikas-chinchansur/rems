(ns rems.test.db.licenses
  (:require [clj-time.core :as time]
            [clojure.test :refer :all]
            [rems.db.licenses :refer :all]
            [rems.db.core :as db]))

(deftest test-get-licenses
  (with-redefs [db/get-licenses (fn [_] [{:id 1 :type "text" :title "default title 1" :textcontent "default content 1"}
                                         {:id 2 :type "link" :title "default title 2" :textcontent "default content 2"}])
                db/get-license-localizations (fn [] [{:licid 1 :langcode :en :title "en title 1" :textcontent "en content 1"}
                                                     {:licid 1 :langcode :fi :title "fi title 1" :textcontent "fi content 1"}
                                                     {:licid 2 :langcode :fi :title "fi title 2" :textcontent "fi content 2"}])]
    (is (= [{:id 1 :licensetype "text" :title "default title 1" :textcontent "default content 1"
             :start nil :end nil
             :localizations {:en {:title "en title 1" :textcontent "en content 1"}
                             :fi {:title "fi title 1" :textcontent "fi content 1"}}}
            {:id 2 :licensetype "link" :title "default title 2" :textcontent "default content 2"
             :start nil :end nil
             :localizations {:fi {:title "fi title 2" :textcontent "fi content 2"}}}]
           (rems.db.licenses/get-licenses {})))))

(deftest test-get-active-licenses
  (let [today (time/now)
        yesterday (time/minus today (time/days 1))
        expired-license-end (time/plus yesterday (time/hours 1))
        just-created-license-start (time/minus today (time/hours 1))]
    (with-redefs [db/get-license-localizations (constantly [])
                  ;; test active license deduplication by having :always twice
                  db/get-licenses (fn [params] [{:id :always :start nil :endt nil}
                                                {:id :always :start nil :endt nil}
                                                {:id :expired :start nil :endt expired-license-end}
                                                {:id :just-created :start just-created-license-start :endt nil}])]
      (is (= [:always :just-created] (sort (map :id (get-active-licenses today nil)))))
      (is (= [:always :expired] (sort (map :id (get-active-licenses yesterday nil))))))))
