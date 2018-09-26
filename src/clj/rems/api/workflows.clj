(ns rems.api.workflows
  (:require [compojure.api.sweet :refer :all]
            [rems.api.applications :refer [Reviewer get-reviewers]]
            [rems.api.util :refer [check-roles check-user]]
            [rems.db.core :as db]
            [rems.db.workflow :as workflow]
            [rems.db.workflow-actors :as actors]
            [rems.util :refer [get-user-id]]
            [ring.util.http-response :refer :all]
            [schema.core :as s])
  (:import [org.joda.time DateTime]))

(def Actor
  {:actoruserid s/Str
   :round s/Num
   :role (s/enum "approver" "reviewer")})

(def Workflow
  {:id s/Num
   :organization s/Str
   :owneruserid s/Str
   :modifieruserid s/Str
   :title s/Str
   :final-round s/Num
   :start DateTime
   :end (s/maybe DateTime)
   :active s/Bool
   :actors [Actor]})

(defn- format-workflow
  [{:keys [id organization owneruserid modifieruserid title fnlround start endt active?]}]
  {:id id
   :organization organization
   :owneruserid owneruserid
   :modifieruserid modifieruserid
   :title title
   :final-round fnlround
   :start start
   :end endt
   :active active?})

(def CreateWorkflowCommand
  {:organization s/Str
   :title s/Str
   :rounds [{:type (s/enum :approval :review)
             :actors [{:userid s/Str}]}]})

; TODO: deduplicate or decouple with /api/applications/reviewers API?
(def AvailableActor Reviewer)
(def get-available-actors get-reviewers)

(defn- get-workflows [filters]
  (doall
    (for [wf (workflow/get-workflows filters)]
      (assoc (format-workflow wf)
        :actors (db/get-workflow-actors {:wfid (:id wf)})))))

(defn create-workflow [{:keys [organization title rounds]}]
  (let [wfid (:id (db/create-workflow! {:organization organization,
                                        :owneruserid (get-user-id),
                                        :modifieruserid (get-user-id),
                                        :title title,
                                        ; workflows with no rounds are auto-approved
                                        :fnlround (max 0 (dec (count rounds)))}))]
    (doseq [[round-index round] (map-indexed vector rounds)]
      (doseq [actor (:actors round)]
        (case (:type round)
          :approval (actors/add-approver! wfid (:userid actor) round-index)
          :review (actors/add-reviewer! wfid (:userid actor) round-index))))
    {:id wfid}))

(def workflows-api
  (context "/workflows" []
    :tags ["workflows"]

    (GET "/" []
      :summary "Get workflows"
      :query-params [{active :- (describe s/Bool "filter active or inactive workflows") nil}]
      :return [Workflow]
      (check-user)
      (check-roles :owner)
      (ok (get-workflows (when-not (nil? active) {:active? active}))))

    (POST "/create" []
      :summary "Create workflow"
      :body [command CreateWorkflowCommand]
      (check-user)
      (check-roles :owner)
      (ok (create-workflow command)))

    (GET "/actors" []
      :summary "List of available actors"
      :return [AvailableActor]
      (check-user)
      (check-roles :owner)
      (ok (get-available-actors)))))