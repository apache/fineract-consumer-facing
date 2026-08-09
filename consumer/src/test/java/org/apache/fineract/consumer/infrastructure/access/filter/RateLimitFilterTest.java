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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import org.apache.fineract.consumer.infrastructure.access.data.RateLimitWindow;
import org.apache.fineract.consumer.infrastructure.access.exception.RateLimitExceededException;
import org.apache.fineract.consumer.infrastructure.access.service.RateLimitCounter;
import org.apache.fineract.consumer.infrastructure.configs.RateLimitProperties;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {

    private static final int USER_CEILING = 300;
    private static final int TPP_CLIENT_CEILING = 60;
    private static final long SECONDS_TO_RESET = 42L;
    private static final String SUBJECT = "user-public-id";
    private static final String TPP_CLIENT_ID = "demo-tpp";
    private static final String TPP_CLIENT_SECRET = "secret";
    private static final String TPP_CLIENT_BUCKET_KEY = RateLimitFilter.TPP_CLIENT_KEY_PREFIX + TPP_CLIENT_ID;

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

        perUserFilter(false).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(counter);
    }

    @Test
    void passesThroughWhenRequestIsUnauthenticated() throws Exception {
        perUserFilter(true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(counter);
    }

    @Test
    void passesThroughWhenCountIsAtTheCeiling() throws Exception {
        authenticate();
        when(counter.increment(SUBJECT)).thenReturn(Optional.of(new RateLimitWindow(USER_CEILING, SECONDS_TO_RESET)));

        perUserFilter(true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void rejectsWhenCountIsOverTheCeiling() throws Exception {
        authenticate();
        when(counter.increment(SUBJECT))
                .thenReturn(Optional.of(new RateLimitWindow(USER_CEILING + 1, SECONDS_TO_RESET)));

        perUserFilter(true).doFilter(request, response, chain);

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

        perUserFilter(true).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void tppBucketsOnTheBasicAuthClientId() throws Exception {
        tokenEndpointRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, basicAuth(TPP_CLIENT_ID, TPP_CLIENT_SECRET));
        when(counter.increment(TPP_CLIENT_BUCKET_KEY))
                .thenReturn(Optional.of(new RateLimitWindow(1, SECONDS_TO_RESET)));

        perTppClientFilter().doFilter(request, response, chain);

        verify(counter).increment(TPP_CLIENT_BUCKET_KEY);
        verify(chain).doFilter(request, response);
    }

    @Test
    void tppDecodesFormUrlencodedBasicAuthClientId() throws Exception {
        tokenEndpointRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, basicAuth("demo%2Btpp", TPP_CLIENT_SECRET));
        String expectedKey = RateLimitFilter.TPP_CLIENT_KEY_PREFIX + "demo+tpp";
        when(counter.increment(expectedKey)).thenReturn(Optional.of(new RateLimitWindow(1, SECONDS_TO_RESET)));

        perTppClientFilter().doFilter(request, response, chain);

        verify(counter).increment(expectedKey);
        verify(chain).doFilter(request, response);
    }

    @Test
    void tppFallsBackToTheClientIdParameter() throws Exception {
        tokenEndpointRequest();
        request.setParameter(OAuth2ParameterNames.CLIENT_ID, TPP_CLIENT_ID);
        when(counter.increment(TPP_CLIENT_BUCKET_KEY))
                .thenReturn(Optional.of(new RateLimitWindow(1, SECONDS_TO_RESET)));

        perTppClientFilter().doFilter(request, response, chain);

        verify(counter).increment(TPP_CLIENT_BUCKET_KEY);
        verify(chain).doFilter(request, response);
    }

    @Test
    void tppRejectsWhenCountIsOverTheCeiling() throws Exception {
        tokenEndpointRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, basicAuth(TPP_CLIENT_ID, TPP_CLIENT_SECRET));
        when(counter.increment(TPP_CLIENT_BUCKET_KEY))
                .thenReturn(Optional.of(new RateLimitWindow(TPP_CLIENT_CEILING + 1, SECONDS_TO_RESET)));

        perTppClientFilter().doFilter(request, response, chain);

        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo(Long.toString(SECONDS_TO_RESET));
        assertThat(JSON.readTree(response.getContentAsString()).path("code").asString())
                .isEqualTo(RateLimitExceededException.CODE);
    }

    @Test
    void tppPassesThroughWhenTheRequestCarriesNoClientId() throws Exception {
        tokenEndpointRequest();

        perTppClientFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(counter);
    }

    @Test
    void tppPassesThroughOffTheTokenEndpoint() throws Exception {
        request.setMethod(HttpMethod.POST.name());
        request.setRequestURI(OAuth2Constants.AUTHORIZATION_ENDPOINT);
        request.addHeader(HttpHeaders.AUTHORIZATION, basicAuth(TPP_CLIENT_ID, TPP_CLIENT_SECRET));

        perTppClientFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(counter);
    }

    @Test
    void tppIgnoresAMalformedBasicHeader() throws Exception {
        tokenEndpointRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic not-base64!!!");

        perTppClientFilter().doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(counter);
    }

    private RateLimitFilter perUserFilter(boolean enabled) {
        return RateLimitFilter.perUser(properties(enabled), counter, JSON);
    }

    private RateLimitFilter perTppClientFilter() {
        return RateLimitFilter.perTppClient(properties(true), counter, JSON);
    }

    private static RateLimitProperties properties(boolean enabled) {
        return new RateLimitProperties(enabled, USER_CEILING, TPP_CLIENT_CEILING);
    }

    private void tokenEndpointRequest() {
        request.setMethod(HttpMethod.POST.name());
        request.setRequestURI(OAuth2Constants.TOKEN_ENDPOINT);
    }

    private static String basicAuth(String encodedClientId, String secret) {
        String credentials = encodedClientId + ":" + secret;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static void authenticate() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
