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

package org.apache.fineract.consumer.user.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.user.command.domain.UserStatus;
import org.apache.fineract.consumer.user.command.exception.UserNotFoundException;
import org.apache.fineract.consumer.user.query.data.UserCredentialsQueryData;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.repository.UserQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final UUID EXTERNAL_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String EMAIL = "user@test.com";
    private static final String PASSWORD_HASH = "{bcrypt}$2a$10$hash";

    @Mock
    private UserQueryRepository repository;

    @InjectMocks
    private UserQueryServiceImpl service;

    private static UserQueryData userQueryData() {
        return UserQueryData.builder()
                .id(USER_ID)
                .externalId(EXTERNAL_ID)
                .email(EMAIL)
                .status(UserStatus.BOUND)
                .build();
    }

    @Test
    void findByExternalIdReturnsUser() {
        UserQueryData user = userQueryData();
        when(repository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(user));

        assertThat(service.findByExternalId(EXTERNAL_ID)).isEqualTo(user);
    }

    @Test
    void findByExternalIdUnknownIsRejected() {
        when(repository.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByExternalId(EXTERNAL_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void findByIdReturnsUser() {
        UserQueryData user = userQueryData();
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThat(service.findById(USER_ID)).isEqualTo(user);
    }

    @Test
    void findByIdUnknownIsRejected() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void findCredentialsByEmailReturnsCredentialsWhenPresent() {
        UserCredentialsQueryData credentials = UserCredentialsQueryData.builder()
                .id(USER_ID)
                .externalId(EXTERNAL_ID)
                .status(UserStatus.BOUND)
                .passwordHash(PASSWORD_HASH)
                .build();
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(Optional.of(credentials));

        assertThat(service.findCredentialsByEmail(EMAIL)).contains(credentials);
    }

    @Test
    void findCredentialsByEmailReturnsEmptyWhenUnknown() {
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(service.findCredentialsByEmail(EMAIL)).isEmpty();
    }
}
