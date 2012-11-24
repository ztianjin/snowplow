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
  (:use [clojure.java.io :only [reader]])
  (:require [clj-yaml.core :as yaml]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [metis.core    :as metis]))


;; ----------------- Defaults ------------------------

(def ^:const defaults-filename "defaults.yml")

;; Note Beanstalk only has 4 'slots' in the UI for environment variables
(def ^:const env-varname "SP_ENV")
(def ^:const cfg-varname "SP_CFG")


;; ----------------- System environment ------------------------

(def production?
  "Running in production?"
  (= "production" (get (System/getenv) env-varname)))

(def development?
  "Running in development environment?"
  (not production?))

(def config-file
  "Get the configuration file to load"
  (let [config (get (System/getenv) cfg-varname)]
    (if (nil? config)
      (throw 
        (IllegalStateException. (str cfg-varname " environment variable not set")))
      config)))


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
  [:attach_uid :inclusion {:in ["true" "false"] :allow-nil true :message "must be true or false"}]
  [:attach_ip  :inclusion {:in ["true" "false"] :allow-nil true :message "must be true or false"}])

; Validation for the sink fields, including
; conditional validation for redirect sink
(metis/defvalidator sink-validator
  [:out :inclusion {:in ["none" "redirect"] :message "must be none or redirect"}]
  [:redirect :redirect-validator {:if redirect-sink}])

; Overall validation of config map
(metis/defvalidator config-validator
  [:cookie :cookie-validator]
  [:sink   :sink-validator])


;; -------------- Extracts from Carica --------------------------

(defn resources
  "Search the classpath for resources matching the given path"
  [path]
  (when path
    (reverse
     (enumeration-seq
      (.getResources
       (.getContextClassLoader
        (Thread/currentThread))
       path)))))

;; -------------------- Noodling on Configgity ----------------------------

(defn merge-defaults
  "Merges `defaults` into `map`.
   Works with nested maps.
   A default is only set if a
   value is nil in `map`"
  [map defaults]
  (if (and (map? map) (map? defaults))
    (merge-with merge-defaults map defaults)
    (if (nil? map) defaults map)))

; TODO: make this support /local, s3(n):// and res://
(defn- load-config
  "Loads and parses a config
   file in YAML format"
  [resource]
  (-> resource slurp yaml/parse-string))

(defn validate-config
  "Validates a `config` using a Metis validator.
   Returns the `config` for threading"
  [config validator]
  (let [errors (validator config)]
    (if (seq errors)
      (throw
        (Exception. (str "Error(s) validating config: " errors)))
      config)))


;; ---------------- Load with Configgity ------------------------

; TODO: make this generic, so it supports any config file with
; any default. Then move up to Configgity section
(def config
  "Load our config file,
   validate it, add defaults"
  (let [config (load-config config-file)
        defaults (-> "defaults.yml" resources)] ; first load-config)]
    (-> config (validate-config config-validator)))) ;; (merge-defaults defaults)


;; -------------------- Legacy until deleted ------------------------------

;; Defaults
(def ^:const default-p3p-header "policyref=\"/w3c/p3p.xml\", CP=\"NOI DSP COR NID PSA OUR IND COM NAV STA\"")
(def ^:const default-duration 31556900) ; A year

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