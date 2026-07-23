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

package org.apache.fineract.consumer.infrastructure.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.apache.fineract.consumer.infrastructure.configs.AuthenticationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class JwtDenylistTest {

    private static final String KEY_PREFIX = "jwt:denylist:";
    private static final String SUBJECT_KEY_PREFIX = KEY_PREFIX + "subject:";
    private static final String TOKEN_ID = "3f2c8a1e-0000-4000-8000-000000000001";
    private static final String EXPECTED_KEY = KEY_PREFIX + TOKEN_ID;
    private static final String SUBJECT = "7d4b9c2f-0000-4000-8000-000000000002";
    private static final String OTHER_SUBJECT = "other-subject";
    private static final String EXPECTED_SUBJECT_KEY = SUBJECT_KEY_PREFIX + SUBJECT;
    private static final Duration TOKEN_LIFETIME = Duration.ofSeconds(900);
    private static final Duration CLOCK_SKEW_PAD = Duration.ofSeconds(60);
    private static final Instant CUTOFF = Instant.parse("2026-07-22T12:00:00Z");

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtDenylist denylist;

    @BeforeEach
    void setUp() {
        AuthenticationProperties properties = new AuthenticationProperties(TOKEN_LIFETIME, null, null, null, false);
        denylist = new JwtDenylist(redisTemplate, properties);
    }

    @Test
    void denyWritesNamespacedKeyWithRemainingLifetimePlusSkewPad() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        denylist.deny(TOKEN_ID, Instant.now().plus(TOKEN_LIFETIME));

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(eq(EXPECTED_KEY), eq("denied"), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue())
                .isGreaterThan(TOKEN_LIFETIME)
                .isLessThanOrEqualTo(TOKEN_LIFETIME.plus(CLOCK_SKEW_PAD));
    }

    @Test
    void denySkipsWriteForAlreadyExpiredToken() {
        denylist.deny(TOKEN_ID, Instant.now().minus(TOKEN_LIFETIME));

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void deniedTokenIdIsDenied() {
        when(redisTemplate.hasKey(EXPECTED_KEY)).thenReturn(true);

        assertThat(denylist.isDenied(TOKEN_ID)).isTrue();
    }

    @Test
    void unknownTokenIdIsNotDenied() {
        when(redisTemplate.hasKey(EXPECTED_KEY)).thenReturn(false);

        assertThat(denylist.isDenied(TOKEN_ID)).isFalse();
    }

    @Test
    void nullTokenIdIsNotDenied() {
        assertThat(denylist.isDenied(null)).isFalse();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void unreachableStoreFailsClosed() {
        when(redisTemplate.hasKey(EXPECTED_KEY)).thenThrow(new RedisConnectionFailureException("store down"));

        assertThat(denylist.isDenied(TOKEN_ID)).isTrue();
    }

    @Test
    void denyAllIssuedUpToWritesSubjectKeyWithAccessTokenTtlPlusSkewPad() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        denylist.denyAllIssuedUpTo(SUBJECT, CUTOFF);

        verify(valueOperations).set(EXPECTED_SUBJECT_KEY, CUTOFF.toString(), TOKEN_LIFETIME.plus(CLOCK_SKEW_PAD));
    }

    @Test
    void tokenIssuedBeforeCutoffIsDeniedForSubject() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_SUBJECT_KEY)).thenReturn(CUTOFF.toString());

        assertThat(denylist.isDeniedForSubject(SUBJECT, CUTOFF.minusSeconds(1))).isTrue();
    }

    @Test
    void tokenIssuedExactlyAtCutoffIsDeniedForSubject() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_SUBJECT_KEY)).thenReturn(CUTOFF.toString());

        assertThat(denylist.isDeniedForSubject(SUBJECT, CUTOFF)).isTrue();
    }

    @Test
    void tokenIssuedAfterCutoffIsNotDeniedForSubject() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_SUBJECT_KEY)).thenReturn(CUTOFF.toString());

        assertThat(denylist.isDeniedForSubject(SUBJECT, CUTOFF.plusSeconds(1))).isFalse();
    }

    @Test
    void subjectWithoutCutoffIsNotDenied() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(SUBJECT_KEY_PREFIX + OTHER_SUBJECT)).thenReturn(null);

        assertThat(denylist.isDeniedForSubject(OTHER_SUBJECT, CUTOFF)).isFalse();
    }

    @Test
    void nullSubjectIsNotDenied() {
        assertThat(denylist.isDeniedForSubject(null, CUTOFF)).isFalse();

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void nullIssuedAtIsNotDenied() {
        assertThat(denylist.isDeniedForSubject(SUBJECT, null)).isFalse();

        verifyNoInteractions(redisTemplate);
    }
}
