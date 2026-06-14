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

package org.apache.fineract.consumer.user.command.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.apache.fineract.consumer.user.command.exception.InvalidBindingStateException;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final UUID EXTERNAL_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String EMAIL = "user@test.com";
    private static final String PASSWORD_HASH = "{bcrypt}$2a$10$hash";
    private static final Long FINERACT_CLIENT_ID = 42L;

    private static User pendingOtpUser() {
        return User.createPendingOtp(EXTERNAL_ID, EMAIL, PASSWORD_HASH, FINERACT_CLIENT_ID);
    }

    @Test
    void createPendingOtpSetsAllFieldsAndStartsUnbound() {
        User user = pendingOtpUser();

        assertThat(user.getExternalId()).isEqualTo(EXTERNAL_ID);
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(user.getFineractClientId()).isEqualTo(FINERACT_CLIENT_ID);
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_OTP);
        assertThat(user.getBoundAt()).isNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    void markOtpVerifiedTransitionsToBound() {
        User user = pendingOtpUser();

        user.markOtpVerified();

        assertThat(user.getStatus()).isEqualTo(UserStatus.BOUND);
        assertThat(user.getBoundAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isEqualTo(user.getBoundAt());
    }

    @Test
    void markOtpVerifiedWhenAlreadyBoundIsRejected() {
        User user = pendingOtpUser();
        user.markOtpVerified();

        assertThatThrownBy(user::markOtpVerified)
                .isInstanceOf(InvalidBindingStateException.class);
    }
}
