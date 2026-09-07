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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.web.data.ConsumerHeaders;
import org.apache.fineract.consumer.infrastructure.web.data.CorrelationConstants;
import org.apache.fineract.consumer.infrastructure.web.service.CorrelationIdHolder;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes a request correlation identifier for every inbound HTTP request.
 *
 * <p>
 * Accepts a valid {@code X-Correlation-ID} header or generates a UUID when the
 * header is missing or malformed. Places the value in the request-scoped
 * {@link CorrelationIdHolder}, the SLF4J MDC, and the response header. Always
 * clears MDC and the holder after the chain completes, including on exceptions.
 * </p>
 *
 * <p>
 * This filter is intentionally narrow: it does not propagate to Feign, OpenTelemetry,
 * or audit persistence. Those are follow-up changes on top of this primitive.
 * </p>
 */
@RequiredArgsConstructor
public class CorrelationIdFilter extends OncePerRequestFilter {

    private final CorrelationIdHolder correlationIdHolder;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(ConsumerHeaders.CORRELATION_ID));
        correlationIdHolder.set(correlationId);
        MDC.put(CorrelationConstants.MDC_KEY, correlationId);
        response.setHeader(ConsumerHeaders.CORRELATION_ID, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationConstants.MDC_KEY);
            correlationIdHolder.clear();
        }
    }

    static String resolveCorrelationId(String inbound) {
        if (inbound == null) {
            return generate();
        }
        String trimmed = inbound.trim();
        if (trimmed.isEmpty() || trimmed.length() > CorrelationConstants.MAX_LENGTH
                || !CorrelationConstants.ALLOWED.matcher(trimmed).matches()) {
            return generate();
        }
        return trimmed;
    }

    private static String generate() {
        return UUID.randomUUID().toString();
    }
}
