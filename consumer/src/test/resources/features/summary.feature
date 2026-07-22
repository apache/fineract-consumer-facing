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

Feature: Consumer accounts summary

  Background:
    Given a logged-in summary customer with seeded accounts

  Scenario: Get my accounts summary in one call
    When I get my accounts summary
    Then the summary contains my seeded savings account with a balance
    And the summary contains my seeded loan account with an outstanding balance

  Scenario: Getting the accounts summary without a session is rejected
    When I get the accounts summary without a session
    Then the summary request is rejected as unauthorized
