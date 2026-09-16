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

package org.apache.fineract.consumer.infrastructure.stepup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import org.apache.fineract.consumer.infrastructure.jwt.service.JwtIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class StepUpTokenServiceTest {

    private final StepUpTokenService service = new StepUpTokenService(mock(JwtIssuer.class), mock(JwtDecoder.class));

    @Test
    void shouldPreserveFingerprintsProducedByAccountIdOverloads() {
        assertThat(service.actionFingerprint("endpoint", 10L, 20L))
                .isEqualTo("2554e958eadb1dccfc813fedcc1bd0fcbe74678c715cf372e4240ce9690060e7");
        assertThat(service.actionFingerprint("endpoint", 10L, 20L, new BigDecimal("12.34")))
                .isEqualTo("77bf08dfbadec0110a81b8f58c5b088e4676346e9501c6fe60b64252d3ea7a7d");
        assertThat(service.actionFingerprint("endpoint", 10L, 20L, "1", new BigDecimal("12.34")))
                .isEqualTo("8491e6473d00d7b57ae90a7940802171f01b0af9b21a0ce5ee5066fc07143c07");
    }

    @Test
    void shouldPreserveFingerprintProducedByStringPartsOverload() {
        assertThat(service.actionFingerprint("beneficiaries:add", "Alice", "Office", "123", "100"))
                .isEqualTo("2c8214c1091cb83a2a77e334bcf661672bfcc1b780e5456f1d9cb191d0ca1f00");
    }

    @Test
    void shouldNormalizeBigDecimalPartsRegardlessOfScale() {
        assertThat(service.actionFingerprint("endpoint", 10L, 20L, new BigDecimal("12.3400")))
                .isEqualTo(service.actionFingerprint("endpoint", 10L, 20L, new BigDecimal("12.34")));
    }

    @Test
    void shouldTreatNullEndpointLikeLiteralNullString() {
        assertThat(service.actionFingerprint(null, 10L, 20L))
                .isEqualTo(service.actionFingerprint("null", 10L, 20L));
    }

    @Test
    void shouldRejectUnsupportedFingerprintPartTypes() {
        assertThatThrownBy(() -> service.actionFingerprint("endpoint", new Object()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported action fingerprint part type");
    }

}
