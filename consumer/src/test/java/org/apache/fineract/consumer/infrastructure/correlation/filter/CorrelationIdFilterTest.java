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

package org.apache.fineract.consumer.infrastructure.correlation.filter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.consumer.infrastructure.correlation.service.CorrelationIdHolder;
import org.apache.fineract.consumer.infrastructure.web.data.ConsumerHeaders;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdHolder holder = new CorrelationIdHolder();
    private final CorrelationIdFilter filter = new CorrelationIdFilter(holder);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void usesExistingCorrelationIdWhenHeaderPresent() throws ServletException, IOException {
        String inputId = "incoming-correlation-id-12345";
        request.addHeader(ConsumerHeaders.CORRELATION_ID, inputId);

        AtomicReference<String> holderValueDuringChain = new AtomicReference<>();
        AtomicReference<String> mdcValueDuringChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            holderValueDuringChain.set(holder.get());
            mdcValueDuringChain.set(MDC.get("correlationId"));
        };

        filter.doFilter(request, response, chain);

        assertThat(holderValueDuringChain.get()).isEqualTo(inputId);
        assertThat(mdcValueDuringChain.get()).isEqualTo(inputId);
        assertThat(response.getHeader(ConsumerHeaders.CORRELATION_ID)).isEqualTo(inputId);
        assertThat(holder.get()).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void generatesCorrelationIdWhenHeaderMissing() throws ServletException, IOException {
        AtomicReference<String> holderValueDuringChain = new AtomicReference<>();
        AtomicReference<String> mdcValueDuringChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            holderValueDuringChain.set(holder.get());
            mdcValueDuringChain.set(MDC.get("correlationId"));
        };

        filter.doFilter(request, response, chain);

        assertThat(holderValueDuringChain.get()).isNotBlank();
        assertThat(mdcValueDuringChain.get()).isEqualTo(holderValueDuringChain.get());
        assertThat(response.getHeader(ConsumerHeaders.CORRELATION_ID)).isEqualTo(holderValueDuringChain.get());
        assertThat(holder.get()).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void generatesCorrelationIdWhenHeaderBlank() throws ServletException, IOException {
        request.addHeader(ConsumerHeaders.CORRELATION_ID, "   ");

        AtomicReference<String> holderValueDuringChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            holderValueDuringChain.set(holder.get());
        };

        filter.doFilter(request, response, chain);

        assertThat(holderValueDuringChain.get()).isNotBlank();
        assertThat(response.getHeader(ConsumerHeaders.CORRELATION_ID)).isEqualTo(holderValueDuringChain.get());
    }

    @Test
    void cleansUpHolderAndMdcEvenWhenChainThrows() {
        request.addHeader(ConsumerHeaders.CORRELATION_ID, "error-test-id");

        FilterChain chain = (req, res) -> {
            throw new RuntimeException("Simulated filter failure");
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception expected) {
            // expected
        }

        assertThat(holder.get()).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void generatesNewCorrelationIdWhenHeaderExceedsMaxLength() throws ServletException, IOException {
        String overlongId = "a".repeat(65);
        request.addHeader(ConsumerHeaders.CORRELATION_ID, overlongId);

        AtomicReference<String> holderValue = new AtomicReference<>();
        FilterChain chain = (req, res) -> holderValue.set(holder.get());

        filter.doFilter(request, response, chain);

        assertThat(holderValue.get()).isNotEqualTo(overlongId);
        assertThat(holderValue.get()).hasSizeLessThanOrEqualTo(64);
        assertThat(response.getHeader(ConsumerHeaders.CORRELATION_ID)).isEqualTo(holderValue.get());
    }

    @Test
    void generatesNewCorrelationIdWhenHeaderContainsInvalidCharacters() throws ServletException, IOException {
        String invalidId = "invalid\r\nheader: injection";
        request.addHeader(ConsumerHeaders.CORRELATION_ID, invalidId);

        AtomicReference<String> holderValue = new AtomicReference<>();
        FilterChain chain = (req, res) -> holderValue.set(holder.get());

        filter.doFilter(request, response, chain);

        assertThat(holderValue.get()).isNotEqualTo(invalidId);
        assertThat(holderValue.get()).hasSizeLessThanOrEqualTo(64);
    }
}
