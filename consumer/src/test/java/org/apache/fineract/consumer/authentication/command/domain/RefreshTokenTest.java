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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.authentication.command.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Long USER_ID = 7L;
    private static final String TOKEN_HASH = "a".repeat(64);
    private static final String DEVICE_FINGERPRINT = "test-device";

    @Test
    void issueSetsAllFieldsAndIsNeitherRevokedNorRotated() {
        Instant expiresAt = Instant.now().plusSeconds(3600);

        RefreshToken token = RefreshToken.issue(USER_ID, TOKEN_HASH, DEVICE_FINGERPRINT, expiresAt);

        assertThat(token.getUserId()).isEqualTo(USER_ID);
        assertThat(token.getTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(token.getDeviceFingerprint()).isEqualTo(DEVICE_FINGERPRINT);
        assertThat(token.getIssuedAt()).isNotNull();
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.getRotatedTo()).isNull();
    }

    @Test
    void revokeSetsRevokedAt() {
        RefreshToken token = RefreshToken.issue(USER_ID, TOKEN_HASH, DEVICE_FINGERPRINT, Instant.now().plusSeconds(60));

        token.revoke();

        assertThat(token.getRevokedAt()).isNotNull();
    }

    @Test
    void revokeIsIdempotent() throws InterruptedException {
        RefreshToken token = RefreshToken.issue(USER_ID, TOKEN_HASH, DEVICE_FINGERPRINT, Instant.now().plusSeconds(60));
        token.revoke();
        Instant firstRevocation = token.getRevokedAt();

        Thread.sleep(5);
        token.revoke();

        assertThat(token.getRevokedAt()).isEqualTo(firstRevocation);
    }

    @Test
    void rotateToRecordsSuccessorAndRevokes() {
        RefreshToken token = RefreshToken.issue(USER_ID, TOKEN_HASH, DEVICE_FINGERPRINT, Instant.now().plusSeconds(60));

        token.rotateTo(42L);

        assertThat(token.getRotatedTo()).isEqualTo(42L);
        assertThat(token.getRevokedAt()).isNotNull();
    }
}
