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

Feature: Consumer transfers

  Background:
    Given a logged-in customer with a funded savings account and a loan

  Scenario: Transfer from my savings to my loan
    When I transfer money from my savings account to my loan
    Then the transfer is accepted with a transfer id

  Scenario: Initiating a transfer without a session is rejected
    When I initiate a transfer without a session
    Then the transfer request is rejected as unauthorized

  Scenario: Initiating a transfer from another client's account is denied
    Given another client owns a savings account I can target
    When I initiate a transfer from the other client's savings account
    Then the transfer request is denied as forbidden
