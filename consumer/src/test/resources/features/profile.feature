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

Feature: Consumer user profile

  Background:
    Given a logged-in profile customer with seeded accounts

  Scenario: Get my profile with masked contact details
    When I get my profile
    Then the profile shows my client details with a masked email

  Scenario: Get my client charges
    When I get my charges
    Then the charges response is returned

  Scenario: Get my obligee details
    When I get my obligees
    Then the obligees list is returned

  Scenario: Get my profile image when none exists
    When I get my profile image
    Then the profile image request is rejected as not found

  Scenario: Profile endpoints without a session are rejected
    When I request the profile endpoints without a session
    Then each profile request is rejected as unauthorized
