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

  Scenario: A monthly transfer can start on the 31st
    Given I own a second savings account for scheduled transfers
    When I schedule a monthly transfer to my second savings account starting on the 31st
    Then the scheduled transfer is accepted with a public id
    And Fineract holds one monthly standing instruction to my second savings account from the 31st

  Scenario: Retrying a scheduled transfer confirmation with the same idempotency key creates one standing instruction
    Given I own a second savings account for scheduled transfers
    When I confirm the same scheduled transfer twice with the same idempotency key
    Then both scheduled transfer confirmations return the same public id
    And Fineract holds exactly one standing instruction from my savings account

  Scenario: Initiating a scheduled transfer without a session is rejected
    When I initiate a scheduled transfer without a session
    Then the scheduled transfer request is rejected as unauthorized

  Scenario: My scheduled transfers list shows a transfer I set up
    Given I own a second savings account for scheduled transfers
    When I schedule a weekly transfer to my second savings account with an end date
    Then my scheduled transfers list shows the weekly transfer to my second savings account until my end date

  Scenario: My scheduled transfers list leaves out standing instructions the bank set up
    Given I own a second savings account for scheduled transfers
    And the bank set up a standing instruction between my savings accounts
    When I schedule a weekly transfer to my second savings account with an end date
    Then my scheduled transfers list shows the weekly transfer to my second savings account until my end date

  Scenario: A transfer from a savings account I no longer own is left out of my list
    Given I own a second savings account for scheduled transfers
    And another client owns a savings account that is not my scheduled transfer beneficiary
    And I have a scheduled transfer on record from that account
    When I schedule a weekly transfer to my second savings account with an end date
    Then my scheduled transfers list shows the weekly transfer to my second savings account until my end date
    When I request the run history of the transfer from that account
    Then the scheduled transfer history request is denied as forbidden

  Scenario: Run history shows a completed run, and a failed run that moved nothing
    Given I own a second savings account for scheduled transfers
    And my savings account is funded for scheduled transfers
    When I schedule a weekly transfer I can afford and one I cannot, both to my second savings account soon
    And Fineract runs the standing instructions due on their start date
    Then the run history of the transfer I can afford shows one successful run
    And the run history of the transfer I cannot afford shows one failed run that moved nothing
    And my second savings account received only the amount of the transfer I can afford

  Scenario: Another customer's scheduled transfer is neither listed nor readable
    Given another customer has set up a scheduled transfer
    When I request the run history of the other customer's scheduled transfer
    Then the scheduled transfer request is rejected as not found
    And my scheduled transfers list is empty

  Scenario: Listing scheduled transfers without a session is rejected
    When I list scheduled transfers without a session
    Then the scheduled transfer request is rejected as unauthorized
