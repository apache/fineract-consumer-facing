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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class DeviceFingerprintFilterTest {

    private static final String SESSION_FINGERPRINT = "session-device-fingerprint";
    private static final String OTHER_FINGERPRINT = "other-device-fingerprint";

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final DeviceFingerprintFilter filter = new DeviceFingerprintFilter(JSON);
    private final FilterChain chain = mock(FilterChain.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void passesThroughWhenHeaderMatchesClaim() throws Exception {
        authenticate(jwt(AuthenticationConstants.SCOPE_CONSUMER_FULL, SESSION_FINGERPRINT));
        request.addHeader(ConsumerHeaders.DEVICE_FINGERPRINT, SESSION_FINGERPRINT);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void passesThroughWhenRequestIsUnauthenticated() throws Exception {
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsMissingHeader() throws Exception {
        authenticate(jwt(AuthenticationConstants.SCOPE_CONSUMER_FULL, SESSION_FINGERPRINT));

        filter.doFilter(request, response, chain);

        assertRejected(HttpStatus.UNAUTHORIZED, DeviceFingerprintFilter.CODE);
    }

    @Test
    void rejectsMismatchedHeader() throws Exception {
        authenticate(jwt(AuthenticationConstants.SCOPE_CONSUMER_FULL, SESSION_FINGERPRINT));
        request.addHeader(ConsumerHeaders.DEVICE_FINGERPRINT, OTHER_FINGERPRINT);

        filter.doFilter(request, response, chain);

        assertRejected(HttpStatus.FORBIDDEN, DeviceFingerprintFilter.MISMATCH_CODE);
    }

    @Test
    void rejectsTokenWithoutFingerprintClaim() throws Exception {
        authenticate(jwt(AuthenticationConstants.SCOPE_CONSUMER_FULL, null));
        request.addHeader(ConsumerHeaders.DEVICE_FINGERPRINT, SESSION_FINGERPRINT);

        filter.doFilter(request, response, chain);

        assertRejected(HttpStatus.UNAUTHORIZED, DeviceFingerprintFilter.CODE);
    }

    @Test
    void rejectsTokenWithoutScopeClaim() throws Exception {
        authenticate(jwt(null, SESSION_FINGERPRINT));
        request.addHeader(ConsumerHeaders.DEVICE_FINGERPRINT, SESSION_FINGERPRINT);

        filter.doFilter(request, response, chain);

        assertRejected(HttpStatus.FORBIDDEN, DeviceFingerprintFilter.MISMATCH_CODE);
    }

    @Test
    void rejectsTokenWithUnknownScope() throws Exception {
        authenticate(jwt("openbanking:read", SESSION_FINGERPRINT));
        request.addHeader(ConsumerHeaders.DEVICE_FINGERPRINT, SESSION_FINGERPRINT);

        filter.doFilter(request, response, chain);

        assertRejected(HttpStatus.FORBIDDEN, DeviceFingerprintFilter.MISMATCH_CODE);
    }

    private void assertRejected(HttpStatus status, String code) throws Exception {
        verifyNoInteractions(chain);
        assertThat(response.getStatus()).isEqualTo(status.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        String body = response.getContentAsString();
        assertThat(JSON.readTree(body).path("code").asString()).isEqualTo(code);
        assertThat(body)
                .doesNotContain(SESSION_FINGERPRINT)
                .doesNotContain(OTHER_FINGERPRINT);
    }

    private static void authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static Jwt jwt(String scope, String deviceFingerprint) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("subject");
        if (scope != null) {
            builder.claim(JwtClaims.SCOPE, scope);
        }
        if (deviceFingerprint != null) {
            builder.claim(JwtClaims.DEVICE_FINGERPRINT, deviceFingerprint);
        }
        return builder.build();
    }
}
