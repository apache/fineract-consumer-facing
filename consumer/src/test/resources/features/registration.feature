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

Feature: Consumer registration

  Background:
    Given a fresh Fineract client exists with a Passport identifier

  Scenario: Successful registration through OTP verification
    When I submit registration with the matching Passport
    Then registration is accepted in PENDING_OTP state
    When I request an email OTP
    Then an OTP is delivered to my email
    When I retrieve the OTP from Mailpit
    And I verify the OTP
    Then my registration advances to PENDING_2FA

  Scenario: Submit with mismatched identifier is rejected
    When I submit registration with a non-matching Passport value
    Then registration is rejected
    And the rejection does not reveal which field failed

  Scenario: Replaying a verified OTP is rejected
    When I complete an OTP verification successfully
    And I verify the same OTP a second time
    Then the OTP is rejected as invalid

  Scenario: Verifying a wrong OTP is rejected
    When I submit registration with the matching Passport
    Then registration is accepted in PENDING_OTP state
    And I request an email OTP
    And I verify a wrong OTP
    Then the OTP is rejected as invalid
