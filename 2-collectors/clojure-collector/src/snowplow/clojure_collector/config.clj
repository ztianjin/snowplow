;;;; Copyright (c) 2012 SnowPlow Analytics Ltd. All rights reserved.
;;;;
;;;; This program is licensed to you under the Apache License Version 2.0,
;;;; and you may not use this file except in compliance with the Apache License Version 2.0.
;;;; You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
;;;;
;;;; Unless required by applicable law or agreed to in writing,
;;;; software distributed under the Apache License Version 2.0 is distributed on an
;;;; "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;;;; See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.

;;;; Author:    Alex Dean (mailto:support@snowplowanalytics.com)
;;;; Copyright: Copyright (c) 2012 SnowPlow Analytics Ltd
;;;; License:   Apache License Version 2.0

(ns snowplow.clojure-collector.config
  "Gets environment variables, using
   sensible defaults where necessary"
  (:require [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            [metis.core    :as metis]))

;; -------------------- Constants ------------------------------

;; Note Beanstalk only has 4 'slots' in the UI for environment variables
(def ^:const env-varname "SP_ENV")
(def ^:const cfg-varname "SP_CFG")

;; Defaults
(def ^:const default-p3p-header "policyref=\"/w3c/p3p.xml\", CP=\"NOI DSP COR NID PSA OUR IND COM NAV STA\"")
(def ^:const default-duration 31556900) ; A year


;; ----------------- System environment ------------------------

(def production?
  "Running in production?"
  (= "production" (get (System/getenv) env-varname)))

(def development?
  "Running in development environment?"
  (not production?))

(def config
  "Get the configuration file to load"
  (get (System/getenv)
    cfg-varname
    '(throw (IllegalStateException. (str cfg-varname " environment variable not set")))))


;; ----------------- Metis validation ---------------------------

(defn- redirect-sink [attrs]
  "Identify sink is set to redirect
   for conditional Metis validation"
  (= (:sink :out attrs) "redirect"))

(defn- raw-url
  "Check that we have a raw URL (e.g.
   fg362f.cloudfront.net), not a full URL
   (e.g. http://fg362f.cloudfront.net)."
  [map key _]
  (let [url (get map key)] 
    (when (or (.startsWith url "http")
              (.startsWith url "://")))
      "don't include http(s)://"))

; Validation for the cookie fields
(metis/defvalidator cookie-validator
  [[:domain :duration :p3p_header] :presence {:allow-nil true}]
  [:duration :numericality {:allow-nil true :only-integer true :greater-than 0}])

; Validation for redirect fields
(metis/defvalidator redirect-validator
  [[:url :attach_uid :attach_ip] :presence]
  [:url :raw-url]
  [:attach_uid :inclusion {:in ["true" "false"] :allow-nil true}]
  [:attach_ip  :inclusion {:in ["true" "false"] :allow-nil true}])

; Validation for the sink fields, including
; conditional validation for redirect sink
(metis/defvalidator sink-validator :if-conditional
  [:out :inclusion {:in ["none" "redirect"]}]
  [:redirect :redirect-validator {:if redirect-sink}])

; Overall validation of config map
(metis/defvalidator config-validator
  [:cookie :cookie-validator]
  [:sink   :sink-validator])


;; ---------------- Load with Configgity ------------------------


; To decide: add defaults then validate, or validate then add defaults

;; -------------- Extracts from Carica --------------------------

(defmulti load-config (comp second
                            (partial re-find #"\.([^..]*?)$")
                            (memfn getPath)))

(defmethod load-config "json" [resource]
  (with-open [s (.openStream resource)]
    (-> s reader (json/parse-stream true))))

(defmethod load-config "clj" [resource]
  (try
    (eval
     (try
       (read-string (slurp resource))
       (catch Throwable t
         (log/warn t "error reading config" resource)
         (throw
          (Exception. (str "error reading config " resource) t)))))
    (catch Throwable t
      (log/warn t "error evaling config" resource)
      (throw
       (Exception. (str "error evaling config " resource) t)))))

;; -------------------- Noodling on Configgity ----------------------------

(defn- load-config-yaml
  "Explanation to come"
  [resource]
  (-> resource slurp parse-string))

(defmethod load-config "yml"  [resource] (load-config-yaml resource))
(defmethod load-config "yaml" [resource] (load-config-yaml resource))

(defn validate
  "Validates a map using a Metis validator.
   Returns the map for threading"
  [map validator]
  (let [errors (validator map)]
    (if (seq errors)
      (throw
        (Exception. (str "Error(s) validating map: " errors)))
      map)))

(defn set-defaults
  "Function to merge defaults into a
   map. Replaces any nil values"
  [map defaults]
  merge-with #(if (nil? %1) %2 %1) map defaults)

; Function to convert to record


;; -------------------- Legacy until deleted ------------------------------

(def ^:const p3p-varname "SP_P3P")
(def ^:const domain-varname "SP_DOMAIN")
(def ^:const duration-varname "SP_DURATION")
(def ^:const redirect-varname "SP_REDIRECT")

(def duration
  "Get the duration (in seconds) the
   cookie should last for"
  (get (System/getenv) duration-varname default-duration))

(def p3p-header
  "Get the P3P header.
   Return a default P3P policy if not set"
  (get (System/getenv) p3p-varname default-p3p-header))

(def redirect-url
  "Get the redirect URL. Can be nil"
  (do (println "Checking redirect URL")
      (get (System/getenv) redirect-varname nil)))

(def domain
  "Get the domain the name cookies will be set on.
   Can be a wildcard e.g. '.foo.com'.
   If undefined we'll just use the FQDN of the host"
  (get (System/getenv) domain-varname))