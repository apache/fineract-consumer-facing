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
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtDenylist {

    private static final Duration CLOCK_SKEW_PAD = Duration.ofSeconds(60);
    private static final String KEY_PREFIX = "jwt:denylist:";
    private static final String DENIED_MARKER = "denied";

    private final StringRedisTemplate redisTemplate;

    public void deny(String tokenId, Instant tokenExpiresAt) {
        Duration remainingLifetime = Duration.between(Instant.now(), tokenExpiresAt.plus(CLOCK_SKEW_PAD));
        if (remainingLifetime.isNegative() || remainingLifetime.isZero()) {
            return;
        }
        redisTemplate.opsForValue().set(key(tokenId), DENIED_MARKER, remainingLifetime);
    }

    public boolean isDenied(String tokenId) {
        if (tokenId == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key(tokenId)));
        } catch (DataAccessException e) {
            log.error("JWT denylist store unreachable; failing closed and treating token as denied", e);
            return true;
        }
    }

    private static String key(String tokenId) {
        return KEY_PREFIX + tokenId;
    }
}
