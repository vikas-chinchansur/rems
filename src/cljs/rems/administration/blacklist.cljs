(ns rems.administration.blacklist
  "Implements both a blacklist component and the blacklist-page"
  (:require [re-frame.core :as rf]
            [rems.administration.administration :refer [administration-navigator-container]]
            [rems.application-util]
            [rems.atoms :as atoms]
            [rems.flash-message :as flash-message]
            [rems.spinner :as spinner]
            [rems.table :as table]
            [rems.text :refer [text localize-time]]
            [rems.util :refer [fetch]]))

(rf/reg-event-fx
 ::enter-page
 (fn [{:keys [db]}]
   {:dispatch-n [[::fetch {}]
                 [:rems.table/reset]]}))

(rf/reg-event-fx
 ::fetch
 (fn [{:keys [db]} [_ params]]
   (let [description [text :t.administration/blacklist]]
     (fetch "/api/blacklist"
            {:url-params params
             :handler #(rf/dispatch [::fetch-result %])
             :error-handler (flash-message/default-error-handler :top description)}))
   {:db (assoc db ::loading? true)}))

(defn- format-rows [rows]
  (doall
   (for [{resource :blacklist/resource
          user :blacklist/user
          added-by :blacklist/added-by
          added-at :blacklist/added-at
          comment :blacklist/comment} rows]
     {:key (str "blacklist-" resource (:userid user))
      :resource {:value (:resource/ext-id resource)}
      :user {:value (rems.application-util/get-member-name user)}
      :userid {:value (:userid user)}
      :email {:value (:email user)}
      :added-by {:value (rems.application-util/get-member-name added-by)}
      :added-at {:value added-at :display-value (localize-time added-at)}
      :comment {:value comment}})))

(rf/reg-event-db
 ::fetch-result
 (fn [db [_ rows]]
   (-> db
       (assoc ::blacklist (format-rows rows))
       (dissoc ::loading?))))

(rf/reg-sub ::blacklist (fn [db _] (::blacklist db)))
(rf/reg-sub ::loading? (fn [db _] (::loading? db)))

(defn blacklist []
  (let [table-spec {:id ::blacklist
                    :columns [{:key :resource
                               :title (text :t.administration/resource)}
                              {:key :user
                               :title (text :t.administration/user)}
                              {:key :userid
                               :title (text :t.administration/userid)}
                              {:key :email
                               :title (text :t.applicant-info/email)}
                              {:key :added-at
                               :title (text :t.administration/added-at)}
                              {:key :added-by
                               :title (text :t.administration/added-by)}
                              {:key :comment
                               :title (text :t.administration/comment)}]
                    :rows [::blacklist]
                    :default-sort-column :resource}]
    [:div.mt-3
     [table/search table-spec]
     [table/table table-spec]]))

(defn blacklist-page []
  [:div
   [administration-navigator-container]
   [atoms/document-title (text :t.administration/blacklist)]
   [flash-message/component :top]
   (if @(rf/subscribe [::loading?])
     [spinner/big]
     [blacklist])])
