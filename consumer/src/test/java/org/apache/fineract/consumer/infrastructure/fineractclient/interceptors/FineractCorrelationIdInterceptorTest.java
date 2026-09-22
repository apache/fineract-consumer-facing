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

package org.apache.fineract.consumer.infrastructure.fineractclient.interceptors;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Request;
import feign.RequestTemplate;
import org.apache.fineract.consumer.infrastructure.correlation.service.CorrelationIdHolder;
import org.apache.fineract.consumer.infrastructure.fineractclient.data.FineractHeaders;
import org.junit.jupiter.api.Test;

class FineractCorrelationIdInterceptorTest {

    private static final String TEST_CORRELATION_ID = "c6b8c9d1-0000-4000-8000-000000000001";

    private final CorrelationIdHolder holder = new CorrelationIdHolder();
    private final FineractCorrelationIdInterceptor interceptor = new FineractCorrelationIdInterceptor(holder);

    private static RequestTemplate template(Request.HttpMethod method) {
        RequestTemplate template = new RequestTemplate();
        template.method(method);
        return template;
    }

    @Test
    void setsHeaderWhenHolderPopulated() {
        holder.set(TEST_CORRELATION_ID);
        RequestTemplate template = template(Request.HttpMethod.POST);

        interceptor.apply(template);

        assertThat(template.headers().get(FineractHeaders.CORRELATION_ID)).containsExactly(TEST_CORRELATION_ID);
    }

    @Test
    void setsHeaderForGetMethodWhenHolderPopulated() {
        holder.set(TEST_CORRELATION_ID);
        RequestTemplate template = template(Request.HttpMethod.GET);

        interceptor.apply(template);

        assertThat(template.headers().get(FineractHeaders.CORRELATION_ID)).containsExactly(TEST_CORRELATION_ID);
    }

    @Test
    void doesNotSetHeaderWhenHolderIsEmpty() {
        RequestTemplate template = template(Request.HttpMethod.POST);

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(FineractHeaders.CORRELATION_ID);
    }

    @Test
    void doesNotSetHeaderAfterHolderCleared() {
        holder.set(TEST_CORRELATION_ID);
        holder.clear();
        RequestTemplate template = template(Request.HttpMethod.PUT);

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(FineractHeaders.CORRELATION_ID);
    }
}
