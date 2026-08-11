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

Feature: Consumer beneficiaries

  Background:
    Given a logged-in customer with a funded savings account
    And another client owns a savings account I can register as a beneficiary

  Scenario: Add a beneficiary and see it in my list
    When I register the target account as a beneficiary with a transfer limit
    Then the beneficiary appears in my beneficiary list

  Scenario: Beneficiary transfer limit is enforced
    Given I register the target account as a beneficiary with a transfer limit
    When I transfer an amount within the limit to the target account
    Then the beneficiary transfer is accepted
    When I initiate a transfer above the limit to the target account
    Then the transfer is rejected for exceeding the beneficiary limit

  Scenario: Transfers to unregistered destinations are denied
    When I initiate a transfer to the target account
    Then the transfer is rejected as a forbidden destination

  Scenario: A deleted beneficiary is no longer a valid destination
    Given I register the target account as a beneficiary with a transfer limit
    When I delete the beneficiary
    Then my beneficiary list is empty
    When I initiate a transfer to the target account
    Then the transfer is rejected as a forbidden destination
