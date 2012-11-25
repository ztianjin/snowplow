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

(ns configgity.core
  "Library for working with
   configuration files"
  (:use [clojure.java.io :only [reader]])
  (:require [clojure.tools.logging :as log]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json]))

;; -------------- File access --------------------------

  (defn resources
    "Search the classpath for resources
     matching the given path. Extracted
     from Carica, https://github.com/sonian/carica"
    [path]
    (when path
      (reverse
       (enumeration-seq
        (.getResources
         (.getContextClassLoader
          (Thread/currentThread))
         path)))))



; TODO: make this support /local, s3(n):// and res://
(defn- file->map
  "Loads and parses a map
   file in YAML format"
  [resource]
  (-> resource slurp yaml/parse-string))



  (let [config (load-config config-file)
        defaults (-> "defaults.yml" resources)] ; first load-config)]
    (-> config (validate-config config-validator)))) ;; (merge-defaults defaults)

;; -------------- Default handling --------------------------

  (defn merge-defaults
    "Merges `defaults` into `map`. Works with
     nested maps. A default is only set if a
     value is nil in `map`. Adapted from
     merge-nested in Carica"
    [map defaults]
    (if (and (map? map) (map? defaults))
      (merge-with merge-defaults map defaults)
      (if (nil? map) defaults map)))

;; -------------- Validation --------------------------

  (defn validate
    "Validates a `config` using `validator` - either a
     Metis validator OR a Validateur validation-set.
     Returns the `config` for further threading"
    [config validator]
    (let [errors (validator config)]
      (if (seq errors)
        (throw
          (Exception. (str "Error(s) validating config: " errors)))
        config)))