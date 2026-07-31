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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.cucumber.hooks;

import io.cucumber.java.Before;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.apache.fineract.consumer.infrastructure.access.service.RateLimitCounter;

public final class RateLimitReset {

    private static final String VALKEY_URI = System.getenv().getOrDefault("VALKEY_URL", "redis://localhost:6379");
    private static final String KEY_PATTERN = RateLimitCounter.KEY_PREFIX + "*";
    private static final int SCAN_LIMIT = 1000;
    private static final StatefulRedisConnection<String, String> CONNECTION =
            RedisClient.create(VALKEY_URI).connect();

    @Before
    public void clearRateLimitCounters() {
        RedisCommands<String, String> commands = CONNECTION.sync();
        KeyScanCursor<String> cursor = commands.scan(ScanArgs.Builder.matches(KEY_PATTERN).limit(SCAN_LIMIT));
        while (true) {
            if (!cursor.getKeys().isEmpty()) {
                commands.del(cursor.getKeys().toArray(new String[0]));
            }
            if (cursor.isFinished()) {
                return;
            }
            cursor = commands.scan(ScanCursor.of(cursor.getCursor()),
                    ScanArgs.Builder.matches(KEY_PATTERN).limit(SCAN_LIMIT));
        }
    }
}
