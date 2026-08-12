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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.RateLimitWindow;
import org.apache.fineract.consumer.infrastructure.access.exception.RateLimitExceededException;
import org.apache.fineract.consumer.infrastructure.access.service.RateLimitCounter;
import org.apache.fineract.consumer.infrastructure.configs.RateLimitProperties;
import org.apache.fineract.consumer.infrastructure.exception.ConsumerApiError;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class RateLimitFilter extends OncePerRequestFilter {

    public static final String TPP_CLIENT_KEY_PREFIX = "tppclient:";

    private static final String BASIC_AUTH_SCHEME_PREFIX = "Basic ";

    private final RateLimitProperties properties;
    private final RateLimitCounter counter;
    private final ObjectMapper objectMapper;
    private final Function<HttpServletRequest, Optional<String>> bucketKeyResolver;
    private final int perMinuteCeiling;

    public static RateLimitFilter perUser(RateLimitProperties properties, RateLimitCounter counter,
            ObjectMapper objectMapper) {
        return new RateLimitFilter(properties, counter, objectMapper,
                RateLimitFilter::authenticatedUserBucketKey, properties.getPerUserPerMinute());
    }

    public static RateLimitFilter perTppClient(RateLimitProperties properties, RateLimitCounter counter,
            ObjectMapper objectMapper) {
        return new RateLimitFilter(properties, counter, objectMapper,
                RateLimitFilter::tppClientBucketKey, properties.getPerTppClientPerMinute());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<String> bucketKey = bucketKeyResolver.apply(request);
        if (bucketKey.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<RateLimitWindow> window = counter.increment(bucketKey.get());
        if (window.isEmpty() || window.get().getCount() <= perMinuteCeiling) {
            filterChain.doFilter(request, response);
            return;
        }
        reject(response, window.get().getSecondsToReset());
    }

    private static Optional<String> authenticatedUserBucketKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return Optional.empty();
        }
        return Optional.of(jwtAuthentication.getToken().getSubject());
    }

    private static Optional<String> tppClientBucketKey(HttpServletRequest request) {
        if (!OAuth2Constants.TOKEN_ENDPOINT.equals(request.getRequestURI())
                || !HttpMethod.POST.matches(request.getMethod())) {
            return Optional.empty();
        }
        return basicAuthTppClientId(request)
                .or(() -> Optional.ofNullable(request.getParameter(OAuth2ParameterNames.CLIENT_ID)))
                .map(tppClientId -> TPP_CLIENT_KEY_PREFIX + tppClientId);
    }

    private static Optional<String> basicAuthTppClientId(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BASIC_AUTH_SCHEME_PREFIX, 0,
                BASIC_AUTH_SCHEME_PREFIX.length())) {
            return Optional.empty();
        }
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(header.substring(BASIC_AUTH_SCHEME_PREFIX.length())),
                    StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator <= 0) {
                return Optional.empty();
            }
            return Optional.of(URLDecoder.decode(decoded.substring(0, separator), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
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
