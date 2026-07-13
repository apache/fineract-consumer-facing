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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class JwtDenylist {

    private static final Duration CLOCK_SKEW_PAD = Duration.ofSeconds(60);

    private final Cache<String, Instant> deniedTokenIds;

    public JwtDenylist() {
        this(Ticker.systemTicker());
    }

    JwtDenylist(Ticker ticker) {
        this.deniedTokenIds = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfter(new Expiry<String, Instant>() {

                    @Override
                    public long expireAfterCreate(String tokenId, Instant tokenExpiresAt, long currentTime) {
                        return remainingLifetimeNanos(tokenExpiresAt);
                    }

                    @Override
                    public long expireAfterUpdate(String tokenId, Instant tokenExpiresAt, long currentTime,
                            long currentDuration) {
                        return remainingLifetimeNanos(tokenExpiresAt);
                    }

                    @Override
                    public long expireAfterRead(String tokenId, Instant tokenExpiresAt, long currentTime,
                            long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    public void deny(String tokenId, Instant tokenExpiresAt) {
        deniedTokenIds.put(tokenId, tokenExpiresAt);
    }

    public boolean isDenied(String tokenId) {
        return tokenId != null && deniedTokenIds.getIfPresent(tokenId) != null;
    }

    private static long remainingLifetimeNanos(Instant tokenExpiresAt) {
        return Math.max(0, Duration.between(Instant.now(), tokenExpiresAt.plus(CLOCK_SKEW_PAD)).toNanos());
    }
}
