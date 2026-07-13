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

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtDenylistTest {

    private static final String TOKEN_ID = "3f2c8a1e-0000-4000-8000-000000000001";
    private static final String OTHER_TOKEN_ID = "3f2c8a1e-0000-4000-8000-000000000002";
    private static final Duration TOKEN_LIFETIME = Duration.ofSeconds(900);

    private FakeTicker ticker;
    private JwtDenylist denylist;

    @BeforeEach
    void setUp() {
        ticker = new FakeTicker();
        denylist = new JwtDenylist(ticker);
    }

    @Test
    void deniedTokenIdIsDenied() {
        denylist.deny(TOKEN_ID, Instant.now().plus(TOKEN_LIFETIME));

        assertThat(denylist.isDenied(TOKEN_ID)).isTrue();
    }

    @Test
    void unknownTokenIdIsNotDenied() {
        assertThat(denylist.isDenied(TOKEN_ID)).isFalse();
    }

    @Test
    void nullTokenIdIsNotDenied() {
        assertThat(denylist.isDenied(null)).isFalse();
    }

    @Test
    void deniedTokenIdStaysDeniedThroughClockSkewWindow() {
        denylist.deny(TOKEN_ID, Instant.now().plus(TOKEN_LIFETIME));

        ticker.advance(TOKEN_LIFETIME.plusSeconds(30));

        assertThat(denylist.isDenied(TOKEN_ID)).isTrue();
    }

    @Test
    void deniedTokenIdExpiresAfterClockSkewPad() {
        denylist.deny(TOKEN_ID, Instant.now().plus(TOKEN_LIFETIME));
        denylist.deny(OTHER_TOKEN_ID, Instant.now().plus(TOKEN_LIFETIME.multipliedBy(2)));

        ticker.advance(TOKEN_LIFETIME.plusSeconds(90));

        assertThat(denylist.isDenied(TOKEN_ID)).isFalse();
        assertThat(denylist.isDenied(OTHER_TOKEN_ID)).isTrue();
    }

    private static final class FakeTicker implements Ticker {

        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
