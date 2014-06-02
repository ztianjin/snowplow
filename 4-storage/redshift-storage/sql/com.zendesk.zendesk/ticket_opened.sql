-- Copyright (c) 2014 Snowplow Analytics Ltd. All rights reserved.
--
-- This program is licensed to you under the Apache License Version 2.0,
-- and you may not use this file except in compliance with the Apache License Version 2.0.
-- You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
--
-- Unless required by applicable law or agreed to in writing,
-- software distributed under the Apache License Version 2.0 is distributed on an
-- "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
--
-- Authors:     Alex Dean
-- Copyright:   Copyright (c) 2014 Snowplow Analytics Ltd
-- License:     Apache License Version 2.0
--
-- Version:     1-0-0

CREATE TABLE atomic.com_snowplowanalytics_website_page_context_1 (
CREATE TABLE atomic.com_zendesk_zendesk_ticket_opened_1 (
    -- Schema of this type
    schema_vendor              varchar(128)  encode runlength not null,
    schema_name                varchar(128)  encode runlength not null,
    schema_format              varchar(128)  encode runlength not null,
    schema_version             varchar(128)  encode runlength not null,
  -- Parentage of this type
    root_id                    char(36)      encode raw not null,
    root_tstamp                timestamp     encode raw not null,
    ref_root                   varchar(255)  encode runlength not null,
    ref_tree                   varchar(1500) encode runlength not null,
    ref_parent                 varchar(255)  encode runlength not null,
    -- Properties of this type
    account                    varchar(255)  encode runlength,
    cc_names                   timestamp     encode raw,
    created_at                 timestamp     encode raw,
    created_at_with_timestamp  timestamp     encode raw,
    due_date                   timestamp     encode raw,
    due_date_with_timestamp    timestamp     encode raw,
    external_id                varchar(255)  encode raw,
    group_name                 varchar(255)  encode raw
    id                         varchar(10)   encode raw,
    in_business_hours          boolean,
    organization_name          varchar(255)  encode text255,
    priority                   varchar(6)    encode text255,
    score                      smallint      encode raw,
    status                     varchar(7)    encode text255,
    tags                       varchar(2000) encode text32k,
    ticket_type                varchar(8)    encode text255,
    title                      varchar(200)  encode raw,
    updated_at                 timestamp     encode raw,
    updated_at_with_timestamp  timestamp     encode raw,
    url                        varchar(1016) encode raw,
    url_with_protocol          varchar(1024) encode raw,
    via                        varchar(50)   encode text255,
    assignee                   varchar(2048) encode text32k,
    requester                  varchar(2048) encode text32k,
    current_user               varchar(2048) encode text32k
)
DISTSTYLE KEY
-- Optimized join to atomic.events
DISTKEY (root_id)
SORTKEY (root_tstamp);
