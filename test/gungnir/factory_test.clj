(ns gungnir.factory-test
  (:require
   [gungnir.test.util.database :as database :refer [datasource-opts-2]]
   [clojure.test :refer :all]
   [gungnir.query :as q]
   [gungnir.database :refer [*database*]]
   [gungnir.factory]
   [gungnir.test.util :as util]
   [gungnir.changeset :refer [changeset]]
   [gungnir.test.util.migrations :as migrations]))

(use-fixtures :once util/once-fixture)
(use-fixtures :each util/each-fixture)

(def user-1-email "user@test.com")

(def user-1-password "123456")

(def user-1
  {:user/email user-1-email
   :user/password user-1-password})

(def user-2-email "user-2@test.com")

(def user-2-password "123456")

(def user-2
  {:user/email user-2-email
   :user/password user-2-password})

(deftest local-datasource
  (let [{:keys [datasource all!-fn find!-fn save!-fn delete!-fn find-by!-fn]} (gungnir.factory/make-datasource-map! datasource-opts-2)]
    (migrations/init! datasource)
    (database/clear! datasource)
    (testing "creating datasource map"
      (is (not= datasource *database*) )
      (is (instance? javax.sql.DataSource datasource))
      (is (instance? javax.sql.DataSource *database*)))

    (testing "create user with local datasource map"
      (let [user (-> user-1 changeset save!-fn)]
        (is (nil? (:changeset/errors user)))
        (is (uuid? (:user/id user)))
        (is (some? (find!-fn :user (:user/id user))))
        ;; Should not be findable in global datasource
        (is (nil? (q/find! :user (:user/id user))))))

    (testing "deleting user locally"
      (let [user-1-2 (-> user-2 changeset q/save!)
            user-2-2 (-> user-2 changeset save!-fn)]
        (delete!-fn user-1-2)
        (delete!-fn user-2-2)
        (is (some? (q/find! :user (:user/id user-1-2))))))

    (testing "all posts locally"
      (let [{:user/keys [id]} (-> user-2 (assoc :user/email "foo@bar.baz") changeset save!-fn)]
        (save!-fn (changeset {:post/user-id id :post/title "" :post/content ""}))
        (save!-fn (changeset {:post/user-id id :post/title "" :post/content ""}))
        (is (seq (all!-fn :post)))
        (is  (not (seq (q/all! :post))))))

    (testing "find-by user locally"
      (let [{:user/keys [email]} (-> user-2 (assoc :user/email "foo@bar2.baz") changeset save!-fn)]
        (is (some? (find-by!-fn :user/email email)))
        (is (nil? (q/find-by! :user/email email)))))
    (.close datasource)))
