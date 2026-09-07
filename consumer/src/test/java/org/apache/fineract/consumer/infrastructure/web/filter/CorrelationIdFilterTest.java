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

package org.apache.fineract.consumer.infrastructure.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.apache.fineract.consumer.infrastructure.web.data.ConsumerHeaders;
import org.apache.fineract.consumer.infrastructure.web.data.CorrelationConstants;
import org.apache.fineract.consumer.infrastructure.web.service.CorrelationIdHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdHolder holder = new CorrelationIdHolder();
    private final CorrelationIdFilter filter = new CorrelationIdFilter(holder);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearMdc() {
        MDC.clear();
        holder.clear();
    }

    @Test
    void generatesIdWhenHeaderMissing() throws Exception {
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader(ConsumerHeaders.CORRELATION_ID)).isNotBlank();
        assertThat(holder.get()).isNull();
        assertThat(MDC.get(CorrelationConstants.MDC_KEY)).isNull();
    }

    @Test
    void propagatesValidInboundHeader() throws Exception {
        String inbound = "client-corr-abc_123.OK";
        request.addHeader(ConsumerHeaders.CORRELATION_ID, inbound);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getHeader(ConsumerHeaders.CORRELATION_ID)).isEqualTo(inbound);
    }

    @Test
    void replacesBlankInboundHeader() throws Exception {
        request.addHeader(ConsumerHeaders.CORRELATION_ID, "   ");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(ConsumerHeaders.CORRELATION_ID)).isNotBlank();
        assertThat(response.getHeader(ConsumerHeaders.CORRELATION_ID).trim()).isNotEmpty();
    }

    @Test
    void replacesOversizedInboundHeader() throws Exception {
        request.addHeader(ConsumerHeaders.CORRELATION_ID, "a".repeat(CorrelationConstants.MAX_LENGTH + 1));

        filter.doFilter(request, response, chain);

        String outbound = response.getHeader(ConsumerHeaders.CORRELATION_ID);
        assertThat(outbound).isNotBlank();
        assertThat(outbound.length()).isLessThanOrEqualTo(CorrelationConstants.MAX_LENGTH);
        assertThat(outbound).doesNotStartWith("aaa");
    }

    @Test
    void replacesMalformedInboundHeader() throws Exception {
        request.addHeader(ConsumerHeaders.CORRELATION_ID, "bad id with spaces!");

        filter.doFilter(request, response, chain);

        String outbound = response.getHeader(ConsumerHeaders.CORRELATION_ID);
        assertThat(outbound).isNotBlank();
        assertThat(outbound).doesNotContain(" ");
        assertThat(CorrelationConstants.ALLOWED.matcher(outbound).matches()).isTrue();
    }

    @Test
    void placesIdInMdcDuringChainAndCleansUp() throws Exception {
        request.addHeader(ConsumerHeaders.CORRELATION_ID, "during-chain");

        FilterChain observing = (req, res) -> {
            assertThat(MDC.get(CorrelationConstants.MDC_KEY)).isEqualTo("during-chain");
            assertThat(holder.get()).isEqualTo("during-chain");
        };

        filter.doFilter(request, response, observing);

        assertThat(MDC.get(CorrelationConstants.MDC_KEY)).isNull();
        assertThat(holder.get()).isNull();
    }

    @Test
    void cleansUpMdcAndHolderWhenChainThrows() throws Exception {
        request.addHeader(ConsumerHeaders.CORRELATION_ID, "boom");
        doThrow(new IllegalStateException("failure")).when(chain).doFilter(request, response);

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("failure");

        assertThat(MDC.get(CorrelationConstants.MDC_KEY)).isNull();
        assertThat(holder.get()).isNull();
        assertThat(response.getHeader(ConsumerHeaders.CORRELATION_ID)).isEqualTo("boom");
    }

    @Test
    void resolveCorrelationIdHelpers() {
        assertThat(CorrelationIdFilter.resolveCorrelationId(null)).isNotBlank();
        assertThat(CorrelationIdFilter.resolveCorrelationId("  ok-id  ")).isEqualTo("ok-id");
        assertThat(CorrelationIdFilter.resolveCorrelationId("bad value")).isNotEqualTo("bad value");
    }
}
