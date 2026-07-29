# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

Feature: Consumer audit event ingestion

  Background:
    Given a logged-in audit customer

  Scenario: A batch of client audit events is accepted and stored
    When I submit a batch of 3 valid audit events
    Then the audit batch is accepted with 3 accepted and 0 rejected
    And the submitted events are stored as CLIENT audit rows

  Scenario: A PII-bearing event is dropped but the rest of the batch is accepted
    When I submit a batch containing one event whose details include an email address
    Then the audit batch is accepted with 2 accepted and 1 rejected
    And the PII-bearing event is not stored

  Scenario: Replaying an event uuid counts as accepted but stores one row
    When I submit the same audit event twice in one batch
    Then the audit batch is accepted with 2 accepted and 0 rejected
    And only one audit row is stored for the replayed event uuid

  Scenario: Unauthenticated audit submissions are rejected
    When I submit an audit batch without being authenticated
    Then the audit request is rejected as unauthenticated

  Scenario: An oversized audit batch is rejected wholesale
    When I submit an audit batch that exceeds the maximum batch size
    Then the audit request is rejected as too large

  Scenario: Audit submissions from a mismatched device are denied
    When I submit an audit batch with a different device fingerprint
    Then the audit request is forbidden with the device mismatch code

  Scenario: A failed login attempt is recorded as a trusted server audit event
    When I attempt to log in with a wrong password
    Then a server-side login failure audit event is recorded for my user
