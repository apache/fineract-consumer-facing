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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.correlation.service.CorrelationIdHolder;
import org.apache.fineract.consumer.infrastructure.web.data.ConsumerHeaders;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String MDC_CORRELATION_ID_KEY = "correlationId";
    private static final Pattern VALID_CORRELATION_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_.-]{1,64}$");

    private final CorrelationIdHolder correlationIdHolder;

    private static boolean isValidCorrelationId(String id) {
        return id != null && !id.isBlank() && VALID_CORRELATION_ID_PATTERN.matcher(id.trim()).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String rawCorrelationId = request.getHeader(ConsumerHeaders.CORRELATION_ID);
        String correlationId = isValidCorrelationId(rawCorrelationId)
                ? rawCorrelationId.trim()
                : UUID.randomUUID().toString();

        correlationIdHolder.set(correlationId);
        response.setHeader(ConsumerHeaders.CORRELATION_ID, correlationId);
        MDC.put(MDC_CORRELATION_ID_KEY, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            correlationIdHolder.clear();
            MDC.remove(MDC_CORRELATION_ID_KEY);
        }
    }
}
