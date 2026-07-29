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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.apache.fineract.consumer.infrastructure.access.data.RateLimitWindow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RateLimitCounterTest {

    private static final String USER_PUBLIC_ID = "user-public-id";
    private static final String KEY = RateLimitCounter.KEY_PREFIX + USER_PUBLIC_ID;
    private static final Duration WINDOW = RateLimitCounter.WINDOW;

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RateLimitCounter counter = new RateLimitCounter(redisTemplate);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void returnsCountAndSecondsToReset() {
        when(valueOperations.increment(KEY)).thenReturn(5L);
        when(redisTemplate.getExpire(KEY)).thenReturn(42L);

        Optional<RateLimitWindow> window = counter.increment(USER_PUBLIC_ID);

        assertThat(window).contains(new RateLimitWindow(5L, 42L));
        verify(redisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    void setsWindowExpiryWhenCounterHasNoTtl() {
        when(valueOperations.increment(KEY)).thenReturn(1L);
        when(redisTemplate.getExpire(KEY)).thenReturn(-1L);

        Optional<RateLimitWindow> window = counter.increment(USER_PUBLIC_ID);

        assertThat(window).contains(new RateLimitWindow(1L, WINDOW.getSeconds()));
        verify(redisTemplate).expire(KEY, WINDOW);
    }

    @Test
    void clampsSecondsToResetToAtLeastOne() {
        when(valueOperations.increment(KEY)).thenReturn(5L);
        when(redisTemplate.getExpire(KEY)).thenReturn(0L);

        Optional<RateLimitWindow> window = counter.increment(USER_PUBLIC_ID);

        assertThat(window).map(RateLimitWindow::getSecondsToReset).contains(1L);
    }

    @Test
    void failsOpenWhenTheStoreIsUnreachable() {
        when(valueOperations.increment(KEY)).thenThrow(new RedisConnectionFailureException("valkey down"));

        assertThat(counter.increment(USER_PUBLIC_ID)).isEmpty();
    }

    @Test
    void failsOpenOnAnyUnexpectedRuntimeException() {
        when(valueOperations.increment(KEY)).thenThrow(new IllegalStateException("unexpected"));

        assertThat(counter.increment(USER_PUBLIC_ID)).isEmpty();
    }
}
