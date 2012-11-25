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
  (:require [configgity.core :as configgity]
            [metis.core      :as metis]))


;; ----------------- Defaults ------------------------

(def ^:const defaults-file "res://defaults.yml")

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
  [:duration :numericality {:allow-nil true :only-integer true :greater-than 0 :message "must be an integer > 0"}])

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


;; -------------- Load using Configgity --------------------------

(def config
  "Load our config file,
   validate it, add defaults"
  (configgity/config config-file defaults-file config-validator)