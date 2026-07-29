/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.consumer.audit.command.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuditPiiScreenTest {

    private final AuditPiiScreen screen = new AuditPiiScreen();

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"contact\":\"user@example.com\"}",
            "{\"note\":\"123456789\"}",
            "{\"note\":\"123456789012\"}",
            "{\"note\":\"1234567890123\"}",
            "{\"note\":\"4111111111111111\"}",
            "{\"note\":\"1234567890123456789\"}",
            "{\"password\":\"x\"}",
            "{\"OTP\":\"x\"}",
            "{\"Ssn\":\"masked\"}",
            "{\"token\":\"abc\"}"
    })
    void piiPayloadsAreRejected(String serializedDetails) {
        assertThat(screen.containsPii(serializedDetails)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"view\":\"BALANCE\",\"accountId\":\"6b1d4f2a-9d2b-4c47-8a1e-b6f2d9c4e7aa\"}",
            "{\"path\":\"/api/v1/savings\",\"status\":404}",
            "{\"note\":\"12345678\"}",
            "{\"note\":\"1234567890\"}",
            "{\"note\":\"12345678901234567890\"}",
            "{\"accessTokenAge\":5}",
            "{\"passwordChanged\":true}"
    })
    void cleanPayloadsPass(String serializedDetails) {
        assertThat(screen.containsPii(serializedDetails)).isFalse();
    }

    @Test
    void nullAndEmptyPayloadsPass() {
        assertThat(screen.containsPii(null)).isFalse();
        assertThat(screen.containsPii("")).isFalse();
    }
}
