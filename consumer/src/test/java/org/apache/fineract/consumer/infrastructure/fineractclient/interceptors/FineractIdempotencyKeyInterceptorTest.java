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

package org.apache.fineract.consumer.infrastructure.fineractclient.interceptors;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.RequestTemplate;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.fineractclient.data.FineractHeaders;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyDeriver;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyHolder;
import org.junit.jupiter.api.Test;

class FineractIdempotencyKeyInterceptorTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String RAW_KEY = "client-chosen-key-123";

    private final IdempotencyKeyHolder holder = new IdempotencyKeyHolder();
    private final FineractIdempotencyKeyInterceptor interceptor = new FineractIdempotencyKeyInterceptor(holder);

    private static RequestTemplate template(Request.HttpMethod method) {
        RequestTemplate template = new RequestTemplate();
        template.method(method);
        return template;
    }

    @Test
    void setsHeaderWhenHolderPopulatedAndMethodIsNotGet() {
        holder.set(IdempotencyKeyDeriver.derive(USER_PUBLIC_ID, RAW_KEY));
        RequestTemplate template = template(Request.HttpMethod.POST);

        interceptor.apply(template);

        assertThat(template.headers().get(FineractHeaders.IDEMPOTENCY_KEY)).containsExactly(holder.get().getValue());
    }

    @Test
    void doesNotSetHeaderWhenHolderIsEmpty() {
        RequestTemplate template = template(Request.HttpMethod.POST);

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(FineractHeaders.IDEMPOTENCY_KEY);
    }

    @Test
    void doesNotSetHeaderOnGetEvenWhenHolderPopulated() {
        holder.set(IdempotencyKeyDeriver.derive(USER_PUBLIC_ID, RAW_KEY));
        RequestTemplate template = template(Request.HttpMethod.GET);

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(FineractHeaders.IDEMPOTENCY_KEY);
    }

    @Test
    void doesNotSetHeaderAfterHolderCleared() {
        holder.set(IdempotencyKeyDeriver.derive(USER_PUBLIC_ID, RAW_KEY));
        holder.clear();
        RequestTemplate template = template(Request.HttpMethod.PUT);

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(FineractHeaders.IDEMPOTENCY_KEY);
    }
}
