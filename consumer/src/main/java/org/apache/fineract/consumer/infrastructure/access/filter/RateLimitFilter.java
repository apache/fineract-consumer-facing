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

package org.apache.fineract.consumer.infrastructure.access.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.RateLimitWindow;
import org.apache.fineract.consumer.infrastructure.access.exception.RateLimitExceededException;
import org.apache.fineract.consumer.infrastructure.access.service.RateLimitCounter;
import org.apache.fineract.consumer.infrastructure.configs.RateLimitProperties;
import org.apache.fineract.consumer.infrastructure.exception.ConsumerApiError;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final RateLimitCounter counter;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<RateLimitWindow> window = counter.increment(jwtAuthentication.getToken().getSubject());
        if (window.isEmpty() || window.get().getCount() <= properties.getPerUserPerMinute()) {
            filterChain.doFilter(request, response);
            return;
        }
        reject(response, window.get().getSecondsToReset());
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        RateLimitExceededException exception = new RateLimitExceededException();
        response.setStatus(exception.getHttpStatus().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ConsumerApiError.builder().code(exception.getCode()).defaultMessage(exception.getErrorMessage()).build());
    }
}
