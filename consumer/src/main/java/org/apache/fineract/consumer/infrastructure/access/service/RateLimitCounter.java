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

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.consumer.infrastructure.access.data.RateLimitWindow;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitCounter {

    public static final String KEY_PREFIX = "ratelimit:";
    public static final Duration WINDOW = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public Optional<RateLimitWindow> increment(String userPublicId) {
        String key = key(userPublicId);
        try {
            Long incremented = redisTemplate.opsForValue().increment(key);
            long count = incremented == null ? 0L : incremented;
            Long ttl = redisTemplate.getExpire(key);
            long secondsToReset = ttl == null ? WINDOW.getSeconds() : ttl;
            if (secondsToReset < 0) {
                redisTemplate.expire(key, WINDOW);
                secondsToReset = WINDOW.getSeconds();
            }
            return Optional.of(new RateLimitWindow(count, Math.max(1, secondsToReset)));
        } catch (RuntimeException e) {
            log.error("Rate limit check failed; failing open and allowing the request", e);
            return Optional.empty();
        }
    }

    private static String key(String userPublicId) {
        return KEY_PREFIX + userPublicId;
    }
}
