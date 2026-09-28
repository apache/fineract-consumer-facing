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

package org.apache.fineract.consumer.infrastructure.correlation.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorrelationIdHolderTest {

    private final CorrelationIdHolder holder = new CorrelationIdHolder();

    @Test
    void getReturnsNullBeforeSet() {
        assertThat(holder.get()).isNull();
    }

    @Test
    void getReturnsStoredCorrelationIdAfterSet() {
        String testId = "test-correlation-123";

        holder.set(testId);

        assertThat(holder.get()).isEqualTo(testId);
    }

    @Test
    void getReturnsNullAfterClear() {
        holder.set("test-correlation-123");

        holder.clear();

        assertThat(holder.get()).isNull();
    }

    @Test
    void staticGetCurrentCorrelationIdReturnsCurrentValue() {
        holder.set("static-corr-id-789");

        assertThat(CorrelationIdHolder.getCurrentCorrelationId()).isEqualTo("static-corr-id-789");

        holder.clear();
        assertThat(CorrelationIdHolder.getCurrentCorrelationId()).isNull();
    }
}
