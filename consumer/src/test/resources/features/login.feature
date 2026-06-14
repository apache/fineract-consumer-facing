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

Feature: Consumer login

  Background:
    Given a registered and bound user exists

  Scenario: Successful login through OTP verification
    When I log in with my correct password
    Then I receive a login challenge sent to my masked email
    When I retrieve the login OTP from Mailpit
    And I verify the login OTP
    Then I receive a session with an access token and refresh cookie
    And a protected endpoint accepts the access token

  Scenario: Login with a wrong password is rejected generically
    When I log in with a wrong password
    Then the login is rejected with a generic credentials error

  Scenario: Login with an unknown email is rejected generically
    When I log in with an unknown email
    Then the login is rejected with a generic credentials error

  Scenario: Verifying a wrong login OTP is rejected
    When I log in with my correct password
    And I verify a wrong login OTP
    Then the two-factor verification is rejected

  Scenario: Replaying a verified login OTP is rejected
    When I complete a login successfully
    And I verify the same login OTP again
    Then the two-factor verification is rejected

  Scenario: A challenge token cannot be used as an access token
    When I log in with my correct password
    Then a protected endpoint rejects the challenge token as a bearer token

  Scenario: Refreshing rotates the refresh token and replay revokes the chain
    When I complete a login successfully
    And I refresh my session
    Then I receive a session with an access token and refresh cookie
    When I refresh using the previous refresh cookie
    Then the refresh is rejected
    When I refresh using the latest refresh cookie
    Then the refresh is rejected

  Scenario: Refreshing from a different device is rejected
    When I complete a login successfully
    And I refresh my session from a different device
    Then the refresh is rejected
