(ns reddit-tv.reddit.api
  (:require
   [cheshire.core :refer [parse-string]]
   [reddit-tv.spec :as rs]
   [slingshot.slingshot :refer [try+ throw+]]
   [taoensso.timbre :refer [debug warn]]
   [clojure.java.io :as io]
   [taoensso.timbre.appenders.core :as appenders]
   [clj-http.client :as http]
   [robert.bruce :refer [try-try-again *last-try* *try*]]
   [clojure.spec :as s]))


(def fake-user-agent {"User-Agent" "Mozilla/5.0 (Windows NT 6.1;) Gecko/20100101 Firefox/13.0.1"})

(defn- reddit-params-for-page
  [p]
  {:query-params {:limit 100 :sort "top" :t "all" :after p}
   :headers (merge fake-user-agent)})

(defn- parse-post-response
  [p]
  (->  p
       :body
       (parse-string true)
       :data
       (select-keys [:after :children])))

(def top-videos-today "https://www.reddit.com/r/all/top.json")

(defn- fetch-posts-page
  [p]
  (http/get top-videos-today (reddit-params-for-page p)))

(s/fdef get-posts-page
        :args (s/cat :after (s/nilable string?))
        :ret ::rs/post-page)

(defmulti handle-http-error (fn [ex] (:status (ex-data ex))))

(defmethod handle-http-error 429
  [ex]
  (warn "too many requests, retrying. times: " *try*)
  (not *last-try*))

(defmethod handle-http-error 503 [ex]
  (warn "too much load, retrying. times: " *try*)
  (not *last-try*))

(defmethod handle-http-error :default [ex] (throw+ ex))

(defn with-error-handling
  [f & args]
  (try-try-again {:sleep 1000 :tries 10 :decay :exponential :error-hook handle-http-error} f args))

(defn get-posts-page
  ([] (get-posts-page nil))
  ([p]
   (debug "getting posts page " p)
   (parse-post-response (with-error-handling fetch-posts-page p))))
