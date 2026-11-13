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

  Scenario: A monthly transfer from the 31st runs on the last day of a shorter month
    Given I own a second savings account for scheduled transfers
    And my savings account is funded for scheduled transfers
    When I schedule a monthly transfer I can afford on the 31st of a month before a shorter one
    And Fineract runs the standing instructions due on the last day of the next month
    Then the run history of the transfer I can afford shows one successful run
    And my second savings account received only the amount of the transfer I can afford

  Scenario: Another customer's scheduled transfer is neither listed nor readable
    Given another customer has set up a scheduled transfer
    When I request the run history of the other customer's scheduled transfer
    Then the scheduled transfer request is rejected as not found
    And my scheduled transfers list is empty

  Scenario: Listing scheduled transfers without a session is rejected
    When I list scheduled transfers without a session
    Then the scheduled transfer request is rejected as unauthorized

  Scenario: Cancelling a scheduled transfer deletes its standing instruction and leaves it out of my list
    Given I own a second savings account for scheduled transfers
    When I schedule a weekly transfer to my second savings account with an end date
    And I cancel the scheduled transfer
    Then Fineract holds the scheduled transfer's standing instruction as deleted
    And my scheduled transfers list is empty
    And the run history of the cancelled transfer is still readable

  Scenario: Cancelling a scheduled transfer that is already cancelled succeeds without deleting it again
    Given I own a second savings account for scheduled transfers
    When I schedule a weekly transfer to my second savings account with an end date
    And I cancel the scheduled transfer
    And I cancel the scheduled transfer
    Then Fineract holds the scheduled transfer's standing instruction as deleted

  Scenario: Another customer's scheduled transfer cannot be cancelled
    Given another customer has set up a scheduled transfer
    When I initiate cancelling the other customer's scheduled transfer
    Then the scheduled transfer cancellation is rejected as not found

  Scenario: Cancelling a scheduled transfer without a session is rejected
    When I initiate cancelling a scheduled transfer without a session
    Then the scheduled transfer request is rejected as unauthorized

  Scenario: A beneficiary with an active scheduled transfer cannot be deleted until the transfer is cancelled
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    When I schedule a monthly transfer within the limit to that beneficiary
    And I try to delete that beneficiary
    Then the beneficiary change is rejected because a scheduled transfer to it is active
    When I cancel the scheduled transfer
    And I delete that beneficiary
    Then that beneficiary is no longer in my beneficiary list

  Scenario: A beneficiary's transfer limit cannot be lowered below an active scheduled transfer's amount
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    When I schedule a monthly transfer within the limit to that beneficiary
    And I try to lower that beneficiary's transfer limit below the scheduled amount
    Then the beneficiary change is rejected because its limit is below a scheduled transfer
    And no verification code was emailed to me
    When I lower that beneficiary's transfer limit to the scheduled amount
    Then that beneficiary's transfer limit is the scheduled amount

  Scenario: A beneficiary's transfer limit can be raised or removed while a scheduled transfer to it is active
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    When I schedule a monthly transfer within the limit to that beneficiary
    And I raise that beneficiary's transfer limit
    Then that beneficiary's transfer limit is the raised limit
    When I remove that beneficiary's transfer limit
    Then that beneficiary has no transfer limit

  Scenario: A limit set below an active scheduled transfer's amount on a beneficiary that had none is rejected
    Given another client's savings account is my scheduled transfer beneficiary with no transfer limit
    When I schedule a monthly transfer to that beneficiary
    And I try to set that beneficiary's transfer limit below the scheduled amount
    Then the beneficiary change is rejected because its limit is below a scheduled transfer

  Scenario: A new beneficiary limit is checked against the amount Fineract holds, not the one first set up
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    When I schedule a monthly transfer within the limit to that beneficiary
    And bank staff raise the scheduled transfer's amount in Fineract above the beneficiary's limit
    And I try to lower that beneficiary's transfer limit to the scheduled amount
    Then the beneficiary change is rejected because its limit is below a scheduled transfer

  # Fineract's validTill is exclusive, so a transfer still runs on its end date and stops the day after. The
  # transfer to my second savings account ends a day earlier, which lets the steps see when the BFF has caught up
  # with each business date; the BFF caches Fineract's today for 30 seconds.
  Scenario: A scheduled transfer stops guarding its beneficiary the day after its end date by Fineract's date
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    And I own a second savings account for scheduled transfers
    When I schedule a weekly transfer to that beneficiary with an end date
    And I schedule a weekly transfer to my second savings account ending the day before
    And Fineract's business date is my end date
    And I try to delete that beneficiary
    Then the beneficiary change is rejected because a scheduled transfer to it is active
    When Fineract's business date is the day after my end date
    And I lower that beneficiary's transfer limit below the scheduled amount
    Then that beneficiary's transfer limit is below the scheduled amount
    When I delete that beneficiary
    Then that beneficiary is no longer in my beneficiary list

  # The cancelled transfer keeps its row, so the guard reads the client's instructions from Fineract and meets the
  # bank's one, which pays the same account from the same source account.
  Scenario: A standing instruction the bank set up to a beneficiary does not stop it being deleted
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    When I schedule a monthly transfer within the limit to that beneficiary
    And I cancel the scheduled transfer
    And the bank sets up a standing instruction from my savings account to that beneficiary
    And I delete that beneficiary
    Then that beneficiary is no longer in my beneficiary list

  # In the next two scenarios the test's own database connection holds the beneficiary row lock, the way a delete or
  # a confirm's re-check would, and lets go once Postgres reports the BFF's request waiting behind it.
  Scenario: A scheduled transfer confirmed while a delete holds its beneficiary is refused once the delete commits
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    And I have initiated a monthly transfer within the limit to that beneficiary
    When I confirm the transfer while a beneficiary delete holds that beneficiary
    Then my confirm waits with the transfer reserved
    When the delete marks that beneficiary deleted and commits
    Then the scheduled transfer request is denied as forbidden
    And Fineract holds no standing instruction from my savings account
    And I have no scheduled transfer on record

  Scenario: Deleting a beneficiary waits while a scheduled transfer confirm checks it
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    When I delete that beneficiary while a scheduled transfer confirm holds it for its check
    Then my delete waits for the confirm's check
    When the confirm's check commits
    Then my delete succeeds
    And that beneficiary is no longer in my beneficiary list
