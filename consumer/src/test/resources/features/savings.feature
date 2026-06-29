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

Feature: Consumer savings reads

  Background:
    Given a logged-in savings customer with seeded accounts

  Scenario: List my savings accounts
    When I list my savings accounts
    Then the savings list contains my seeded savings account

  Scenario: Get my savings account by id
    When I get my seeded savings account
    Then my savings account details are returned

  Scenario: Listing savings without a session is rejected
    When I list savings accounts without a session
    Then the savings request is rejected as unauthorized

  Scenario: Reading another client's savings account is denied
    Given another client owns a savings account
    When I get the other client's savings account
    Then the savings request is denied as forbidden

  Scenario: Initiating a charge payment without a session is rejected
    When I initiate a charge payment without a session
    Then the savings request is rejected as unauthorized

  Scenario: Initiating a charge payment on another client's savings account is denied
    Given another client owns a savings account
    When I initiate a charge payment on the other client's savings account
    Then the savings request is denied as forbidden
