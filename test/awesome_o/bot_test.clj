(ns awesome-o.bot-test
  (:require
   [clojure.test :refer :all]
   [awesome-o.slack :as slack]
   [awesome-o.http :as http]
   [awesome-o.state :as state]
   [awesome-o.time :as time]))

(def test-user "jean-louis")
(def today (time/format-date (time/today)))

(defn- setup-redis [f]
  (state/flushdb)
  (state/reset-state)
  (state/add-person "magnus")
  (state/add-person test-user)
  (state/add-person "patrik")
  (state/set-birthday "patrik" today)
  (state/set-persons-job "patrik" "dev")
  (state/set-persons-job test-user "dev")
  (state/add-person "kristoffer")
  (state/add-location "stockholm")
  (state/add-location "göteborg")
  (state/set-persons-location test-user "göteborg")
  (state/set-persons-location "kristoffer" "stockholm")
  (f))

(def sent-to-slack (atom []))

(defn- rebind-post [f]
  (reset! sent-to-slack [])
  (with-redefs
   [http/post (fn [token payload] (swap! sent-to-slack conj (:text payload)))
    shuffle identity
    rand-nth first]
    (f)))

(defn- mention [text]
  (:text (slack/mention test-user (str "@awesome-o: " text))))

(use-fixtures :each setup-redis rebind-post)

(deftest mention-test
  (is (= (mention "anders is a puggle")
         "OK, nice to meet you @anders!"))

  (is (= (mention "who is part of the dev team?")
         "The dev team: jean-louis, patrik"))

  (is (= (mention "who is anders?")
         "anders is a puggle"))

  (is (= (mention "anders is a developer")
         "OK, now I know anders is part of the dev team"))

  (is (= (mention "who is part of the dev team?")
         "The dev team: jean-louis, patrik, anders"))

  (is (= (mention "who is anders?")
         "anders is a puggle part of the dev team"))

  (is (= (mention "barcelona is a location")
         "OK, now I now that barcelona is a location"))

  (is (= (mention "anders is in barcelona")
         "OK, now I know that anders is in barcelona"))

  (is (= (mention "who is anders?")
         "anders is a puggle part of the dev team located at the barcelona office"))

  (is (= (mention "anders is born on 1980-01-01")
         "OK, now I know anders is born on 1980-01-01"))

  (is (= (mention "patrik was born on 1987-10-09")
         "OK, now I know patrik is born on 1987-10-09"))

  (is (= (mention "I was born on 1987-10-09")
         "OK, now I know jean-louis is born on 1987-10-09"))

  (is (= (mention "when is anders's birthday?")
         "anders was born on 1980-01-01"))

  (is (= (mention "who is slackmaster?")
         "@jean-louis is today's slackmaster"))

  (is (= (mention "I'm away today")
         (str "OK, now I know jean-louis will be away from " today " to " today)))

  (is (= (mention "patrik is away today")
         (str "OK, now I know patrik will be away from " today " to " today)))

  (is (= (mention "anders is away today")
         (str "OK, now I know anders will be away from " today " to " today)))

  (is (= @sent-to-slack
         ["jean-louis was slackmaster but is away, therefore:\n@patrik is today's slackmaster"
          "patrik was slackmaster but is away, therefore:\n@anders is today's slackmaster"
          "anders was slackmaster but is away, therefore:\nTHERE IS NO DEV! OMG RUN FOR YOUR LIFE!!"]))

  (is (= (mention "clear my schedule")
         "OK, I've cleared jean-louis's schedule"))

  (is (= (mention "clear anders's schedule")
         "OK, I've cleared anders's schedule"))

  (is (= (mention "select next slackmaster")
         "@anders is today's slackmaster"))

  (is (= (mention "what is the meaning of life?")
         "forty-two"))

  (is (= (mention "lisa is a puggle")
         "OK, nice to meet you @lisa!"))

  (is (= (mention "lisa is a ux designer")
         "OK, now I know lisa is part of the ux team"))

  (is (= (mention "who is lisa?")
         "lisa is a puggle part of the ux team"))

  (is (= (mention "johan is a puggle")
         "OK, nice to meet you @johan!"))

  (is (= (mention "johan is a salesman")
         "OK, now I know johan is part of the sales team"))

  (is (= (mention "who is part of the sales team?")
         "The sales team: johan"))

  (is (= (mention "who is johan?")
         "johan is a puggle part of the sales team"))

  (is (= (mention "forget about anders")
         "OK, I've forgotten everything about anders"))

  (is (= (mention "who is part of the dev team?")
         "The dev team: jean-louis, patrik")))

(deftest ping-test-non-working
  (testing "non-working hours - does nothing"
    (with-redefs
     [time/working-hour? (constantly false)]
      (slack/ping))
    (is (= [] @sent-to-slack))))

(deftest ping-test-monday
  (testing "a working hour monday - sends all announcements"
    (with-redefs
     [time/working-hour? (constantly true)
      time/monday-today? (constantly true)
      time/wednesday-today? (constantly false)
      time/friday-today? (constantly false)
      state/acquire-daily-announcement (constantly true)]
      (slack/ping))
    (is (= @sent-to-slack
           ["@patrik is today's slackmaster",
            "Today is @patrik's birthday! Happy birthday!"
            "Honeydager monday! ping: @jean-louis, @patrik"
            "Todays meeting master for dev this week is @jean-louis"]))))

(deftest ping-test-tuesday-thursday
  (testing "tuesday and thursday - does daily announcements"
    (with-redefs
     [time/working-hour? (constantly true)
      time/monday-today? (constantly false)
      time/wednesday-today? (constantly false)
      time/friday-today? (constantly false)
      state/acquire-daily-announcement (constantly true)]
      (slack/ping))
    (is (= @sent-to-slack
           ["@patrik is today's slackmaster"
            "Today is @patrik's birthday! Happy birthday!"]))))

(deftest ping-test-wednesday
  (testing "wednesday - does random meeting and daily announcements"
    (with-redefs
     [time/working-hour? (constantly true)
      time/monday-today? (constantly false)
      time/wednesday-today? (constantly true)
      time/friday-today? (constantly false)
      state/acquire-daily-announcement (constantly true)]
      (slack/ping))
    (is (= @sent-to-slack
           ["@patrik is today's slackmaster"
            "Today is @patrik's birthday! Happy birthday!"
            "Today's random meeting is between @jean-louis and @kristoffer"]))))

(deftest ping-test-friday
  (testing "friday - does random meeting and daily announcements"
    (with-redefs
     [time/working-hour? (constantly true)
      time/monday-today? (constantly false)
      time/wednesday-today? (constantly false)
      time/friday-today? (constantly true)
      state/acquire-daily-announcement (constantly true)]
      (slack/ping))
    (is (= @sent-to-slack
           ["@patrik is today's slackmaster"
            "Today is @patrik's birthday! Happy birthday!"
            "Today's random meeting is between @jean-louis and @kristoffer"]))))
