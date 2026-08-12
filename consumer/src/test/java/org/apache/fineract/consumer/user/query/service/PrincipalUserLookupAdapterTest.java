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
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.PrincipalUserData;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.data.UserStatus;
import org.apache.fineract.consumer.user.query.repository.UserQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrincipalUserLookupAdapterTest {

    private static final Long USER_ID = 7L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long CLIENT_ID = 11L;

    @Mock
    private UserQueryRepository userQueryRepository;

    @InjectMocks
    private PrincipalUserLookupAdapter adapter;

    @Test
    void findByPublicIdMapsBoundUserToPrincipalData() {
        when(userQueryRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(user(UserStatus.BOUND)));

        Optional<PrincipalUserData> result = adapter.findByPublicId(PUBLIC_ID);

        assertThat(result).hasValue(new PrincipalUserData(USER_ID, CLIENT_ID, true));
    }

    @Test
    void findByPublicIdMapsNotYetBoundUserAsUnbound() {
        when(userQueryRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(user(UserStatus.PENDING_OTP)));

        Optional<PrincipalUserData> result = adapter.findByPublicId(PUBLIC_ID);

        assertThat(result).hasValue(new PrincipalUserData(USER_ID, CLIENT_ID, false));
    }

    @Test
    void findByPublicIdReturnsEmptyWhenUserMissing() {
        when(userQueryRepository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.empty());

        assertThat(adapter.findByPublicId(PUBLIC_ID)).isEmpty();
    }

    private static UserQueryData user(UserStatus status) {
        return UserQueryData.builder()
                .id(USER_ID)
                .publicId(PUBLIC_ID)
                .fineractClientId(CLIENT_ID)
                .email("user@test.com")
                .status(status)
                .build();
    }
}
