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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.apache.fineract.consumer.infrastructure.access.data.RateLimitWindow;
import org.apache.fineract.consumer.infrastructure.access.exception.RateLimitExceededException;
import org.apache.fineract.consumer.infrastructure.access.service.RateLimitCounter;
import org.apache.fineract.consumer.infrastructure.configs.RateLimitProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {

    private static final int CEILING = 300;
    private static final long SECONDS_TO_RESET = 42L;
    private static final String SUBJECT = "user-public-id";

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final RateLimitCounter counter = mock(RateLimitCounter.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesThroughWhenDisabled() throws Exception {
        authenticate();

        filter(false).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(counter);
    }

    @Test
    void passesThroughWhenRequestIsUnauthenticated() throws Exception {
        filter(true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(counter);
    }

    @Test
    void passesThroughWhenCountIsAtTheCeiling() throws Exception {
        authenticate();
        when(counter.increment(SUBJECT)).thenReturn(Optional.of(new RateLimitWindow(CEILING, SECONDS_TO_RESET)));

        filter(true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void rejectsWhenCountIsOverTheCeiling() throws Exception {
        authenticate();
        when(counter.increment(SUBJECT)).thenReturn(Optional.of(new RateLimitWindow(CEILING + 1, SECONDS_TO_RESET)));

        filter(true).doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo(Long.toString(SECONDS_TO_RESET));
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(JSON.readTree(response.getContentAsString()).path("code").asString())
                .isEqualTo(RateLimitExceededException.CODE);
    }

    @Test
    void failsOpenWhenTheStoreIsUnreachable() throws Exception {
        authenticate();
        when(counter.increment(anyString())).thenReturn(Optional.empty());

        filter(true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    private RateLimitFilter filter(boolean enabled) {
        return new RateLimitFilter(new RateLimitProperties(enabled, CEILING), counter, JSON);
    }

    private static void authenticate() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
