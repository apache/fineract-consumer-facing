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

package org.apache.fineract.consumer.otp.command.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class OtpCommandRepositoryTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String EXPECTED_KEY = "otp:pending:" + PUBLIC_ID;
    private static final String TOKEN_HASH = "a1b2c3d4e5f6";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private OtpCommandRepository repository;

    @BeforeEach
    void setUp() {
        repository = new OtpCommandRepository(redisTemplate);
    }

    @Test
    void saveWritesNamespacedKeyWithOtpTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        repository.savePendingOtp(PUBLIC_ID, TOKEN_HASH);

        verify(valueOperations).set(EXPECTED_KEY, TOKEN_HASH, Duration.ofSeconds(OtpConstants.OTP_TTL_SECONDS));
    }

    @Test
    void getReturnsStoredHash() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(TOKEN_HASH);

        assertThat(repository.getPendingTokenHash(PUBLIC_ID)).isEqualTo(TOKEN_HASH);
    }

    @Test
    void getReturnsNullWhenKeyMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);

        assertThat(repository.getPendingTokenHash(PUBLIC_ID)).isNull();
    }

    @Test
    void deleteRemovesNamespacedKey() {
        repository.deletePendingOtp(PUBLIC_ID);

        verify(redisTemplate).delete(EXPECTED_KEY);
    }
}
