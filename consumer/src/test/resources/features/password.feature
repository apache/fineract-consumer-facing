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

Feature: Logged-in password change

  Background:
    Given a logged-in customer wanting to change their password

  Scenario: Changing the password reissues a fresh session and revokes all previous sessions
    When I initiate a password change with my current password
    And I retrieve the password change OTP from Mailpit
    And I confirm the password change with the OTP and a new password
    Then I receive fresh session cookies and stay logged in
    And my old access token is rejected
    And my old refresh cookie is rejected
    And I can log in with my new password

  Scenario: Initiating a password change with a wrong current password is rejected
    When I initiate a password change with a wrong current password
    Then the password change is rejected as invalid credentials

  Scenario: Resetting a forgotten password revokes old sessions and allows login with the new password
    When I request a password reset for my email
    And I retrieve the password reset OTP from Mailpit
    And I reset my password with the OTP and a new password
    Then my old access token is rejected
    And my old refresh cookie is rejected
    And I can log in with my new password

  Scenario: A revoked access cookie left over after a password reset does not block logging in
    When I request a password reset for my email
    And I retrieve the password reset OTP from Mailpit
    And I reset my password with the OTP and a new password
    Then I can log in with my new password while still sending the revoked access cookie

  Scenario: Requesting a password reset for an unknown email is accepted without revealing anything
    When I request a password reset for an unknown email
    Then the reset request is accepted

  Scenario: Resetting a password with a wrong OTP is rejected
    When I request a password reset for my email
    And I retrieve the password reset OTP from Mailpit
    And I reset my password with a wrong OTP
    Then the password reset is rejected as invalid
