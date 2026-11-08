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

Feature: Consumer scheduled transfers

  Background:
    Given a logged-in customer setting up scheduled transfers

  Scenario: Scheduling a weekly transfer between my own savings accounts creates a standing instruction
    Given I own a second savings account for scheduled transfers
    When I schedule a weekly transfer to my second savings account with an end date
    Then the scheduled transfer is accepted with a public id
    And Fineract holds one weekly standing instruction for the scheduled transfer that stops after my end date

  Scenario: Scheduling a monthly transfer to a registered beneficiary creates a standing instruction
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    When I schedule a monthly transfer within the limit to that beneficiary
    Then the scheduled transfer is accepted with a public id
    And Fineract holds one monthly standing instruction for the scheduled transfer with no end date

  Scenario: Scheduling a transfer above the beneficiary's transfer limit is rejected
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    When I initiate a scheduled transfer above the limit to that beneficiary
    Then the scheduled transfer request is rejected because it exceeds the beneficiary limit

  Scenario: Scheduling a transfer to an account that is neither mine nor a beneficiary is denied
    Given another client owns a savings account that is not my scheduled transfer beneficiary
    When I initiate a scheduled transfer to that unregistered account
    Then the scheduled transfer request is denied as forbidden

  Scenario: Scheduling a transfer from another client's savings account is denied
    Given another client owns a savings account that is not my scheduled transfer beneficiary
    When I initiate a scheduled transfer from that unregistered account to my savings account
    Then the scheduled transfer request is denied as forbidden

  Scenario: Scheduling a transfer that starts in the past is rejected
    Given I own a second savings account for scheduled transfers
    When I initiate a scheduled transfer to my second savings account starting yesterday
    Then the scheduled transfer request is rejected as invalid

  Scenario: Retrying a scheduled transfer confirmation with the same idempotency key creates one standing instruction
    Given I own a second savings account for scheduled transfers
    When I confirm the same scheduled transfer twice with the same idempotency key
    Then both scheduled transfer confirmations return the same public id
    And Fineract holds exactly one standing instruction from my savings account

  Scenario: Initiating a scheduled transfer without a session is rejected
    When I initiate a scheduled transfer without a session
    Then the scheduled transfer request is rejected as unauthorized
