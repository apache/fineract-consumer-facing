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

# These scenarios need the second BFF from compose.second-bff.yaml, which shares the first's database and has
# scheduled transfers turned off. The default Cucumber run skips them; the "Beneficiary guard on two BFFs" job in
# build-tests-backend.yml runs only them, with ./gradlew cucumber --tags @second-bff.
@second-bff
Feature: Beneficiary guard on a second BFF

  Background:
    Given a logged-in customer setting up scheduled transfers
    And the second BFF has scheduled transfers turned off

  # Fineract keeps running instructions set up while the feature was on, so turning it off must not let the
  # beneficiary go.
  Scenario: A beneficiary with an active scheduled transfer cannot be deleted after scheduled transfers are turned off
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    When I schedule a monthly transfer within the limit to that beneficiary
    And I try to delete that beneficiary through the second BFF
    Then the beneficiary change is rejected because a scheduled transfer to it is active

  Scenario: A beneficiary's limit cannot go below an active transfer's amount after scheduled transfers are turned off
    Given another client's savings account is my scheduled transfer beneficiary with a transfer limit
    When I schedule a monthly transfer within the limit to that beneficiary
    And I try to lower that beneficiary's transfer limit below the scheduled amount through the second BFF
    Then the beneficiary change is rejected because its limit is below a scheduled transfer
    And no verification code was emailed to me

  # Unlike the two lock scenarios in scheduledtransfers.feature, both sides are the BFF's own requests, on two
  # instances that share only Postgres. Each round uses a new beneficiary and starts the delete at a different offset
  # from the confirm.
  Scenario: A beneficiary delete on one BFF and a scheduled transfer confirm on another never both succeed
    When I race confirming a scheduled transfer on the first BFF against deleting its beneficiary on the second
    Then in every round either the confirm or the delete succeeded, and the other was refused
    And no round left a scheduled transfer to a deleted beneficiary, in the BFF or in Fineract
    And the confirm won some rounds and the delete others
