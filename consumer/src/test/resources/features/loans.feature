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

Feature: Consumer loan reads

  Background:
    Given a logged-in loans customer with seeded accounts

  Scenario: List my loan accounts
    When I list my loan accounts
    Then the loan list contains my seeded loan account

  Scenario: Get my loan account by id
    When I get my seeded loan account
    Then my loan account details are returned

  Scenario: Listing loans without a session is rejected
    When I list loan accounts without a session
    Then the loan request is rejected as unauthorized

  Scenario: Reading another client's loan account is denied
    Given another client owns a loan account
    When I get the other client's loan account
    Then the loan request is denied as forbidden

  Scenario: Initiating a loan charge payment without a session is rejected
    When I initiate a loan charge payment without a session
    Then the loan request is rejected as unauthorized

  Scenario: Initiating a loan charge payment on another client's loan account is denied
    Given another client owns a loan account
    When I initiate a loan charge payment on the other client's loan account
    Then the loan request is denied as forbidden
