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

Feature: Open banking consent and account access

  Scenario: A TPP obtains consent-scoped account access end to end
    Given a logged-in open banking customer with accounts
    And the TPP holds a client-credentials token
    When the TPP creates an account access consent with permissions "ReadAccountsBasic,ReadBalances"
    Then the consent is created as "AWAITING_AUTHORISATION"
    When the user opens the authorize link without a login session
    Then the browser is redirected to the login page with a return url
    When the user opens the authorize link with their login session
    Then the browser is redirected to the consent page
    When the user approves the consent on the consent page
    Then the TPP callback receives an authorization code
    When the TPP exchanges the code using the correct PKCE verifier
    Then the TPP receives an open banking access token
    When the TPP lists the open banking accounts
    Then the account list contains the user's savings and loan accounts
    When the TPP reads the balances of the user's savings account
    Then a balance entry is returned for the savings account
    And the TPP sees the consent status as "AUTHORISED"

  Scenario: An open banking token is rejected on consumer endpoints
    Given an authorised open banking consent with an access token
    When the open banking access token is used on the savings endpoint
    Then the consumer request is rejected as unauthenticated

  Scenario: A consumer login token is rejected on open banking endpoints
    Given a logged-in open banking customer with accounts
    When the login token is used as a bearer token on the open banking accounts endpoint
    Then the open banking request is rejected as unauthenticated

  Scenario: The user denies the consent at the consent page
    Given a logged-in open banking customer with accounts
    And the TPP holds a client-credentials token
    And the TPP creates an account access consent with permissions "ReadAccountsBasic,ReadBalances"
    When the user opens the authorize link with their login session
    And the browser is redirected to the consent page
    And the user denies the consent on the consent page
    Then the TPP callback receives an access denied error
    And the TPP sees the consent status as "REJECTED"
    And a server-side "CONSENT_DENIED" audit event is recorded for the consent

  Scenario: Token exchange with a wrong PKCE verifier is rejected
    Given a granted open banking consent with a pending authorization code
    When the TPP exchanges the code using a wrong PKCE verifier
    Then the token exchange is rejected with "invalid_grant"

  Scenario: Consent creation and grant are recorded as server audit events
    Given an authorised open banking consent with an access token
    Then a server-side "CONSENT_CREATED" audit event is recorded for the consent
    And a server-side "CONSENT_GRANTED" audit event is recorded for the consent

  Scenario: Authorize requests with unusable consent ids are rejected without a code
    Given an authorised open banking consent with an access token
    When the user opens an authorize link with an unknown consent id
    Then the TPP callback receives an invalid request error and no code
    When the user opens an authorize link with the already authorised consent id
    Then the TPP callback receives an invalid request error and no code

  Scenario: Consent creation with an unknown permission is rejected
    Given the TPP holds a client-credentials token
    When the TPP creates an account access consent with an unknown permission
    Then the consent creation is rejected as a bad request

  Scenario: A client-credentials token cannot read accounts directly
    Given the TPP holds a client-credentials token
    When the client-credentials token is used on the open banking accounts endpoint
    Then the open banking read is forbidden by the consent check

  Scenario: The user revokes an authorised consent
    Given an authorised open banking consent with an access token
    When the user lists their connected app consents
    Then the consent list shows the consent as "AUTHORISED"
    When the user revokes the consent
    And the TPP lists the open banking accounts
    Then the open banking read is forbidden by the consent check
    When the user lists their connected app consents
    Then the consent list shows the consent as "REVOKED"
    And a server-side "CONSENT_REVOKED" audit event is recorded for the consent
    When the user revokes the consent again
    Then the revoke is rejected as not revocable

  Scenario: Consent permissions gate each read endpoint
    Given an authorised open banking consent limited to permission "ReadAccountsBasic"
    When the TPP lists the open banking accounts
    Then the account list contains the user's savings and loan accounts
    When the TPP reads the balances of the user's savings account
    Then the open banking read is forbidden by the consent check

  Scenario: The TPP refreshes the open banking access token
    Given an authorised open banking consent with an access token
    When the TPP refreshes the open banking token
    Then the TPP receives a new open banking access token and a rotated refresh token
    When the TPP lists the open banking accounts
    Then the account list contains the user's savings and loan accounts
    When the TPP replays the previous refresh token
    Then the token exchange is rejected with "invalid_grant"

  Scenario: Refresh is rejected after the user revokes the consent
    Given an authorised open banking consent with an access token
    When the user revokes the consent
    And the TPP refreshes the open banking token
    Then the token exchange is rejected with "invalid_grant"
