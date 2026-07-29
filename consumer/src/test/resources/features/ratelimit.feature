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

Feature: Per-user rate limiting

  Scenario: Requests over the per-user ceiling are rejected with 429
    Given a registered and logged-in consumer
    When the consumer sends 300 authenticated beneficiary list requests
    When the consumer sends one more beneficiary list request
    Then the request is rejected with status 429
    And the response carries the rate-limit error code and a Retry-After header
