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
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.NonTransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.exception.ConsumerApiError;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class DeviceFingerprintFilter extends OncePerRequestFilter {

    public static final String CODE = "error.msg.consumer.auth.device.fingerprint.mismatch";
    public static final String MISMATCH_CODE = "error.msg.consumer.auth.device.fingerprint.forbidden";
    public static final String INVALID_CODE = "error.msg.consumer.auth.device.fingerprint.invalid";
    private static final String DEFAULT_MESSAGE = "device fingerprint does not match the session";
    private static final String MISMATCH_MESSAGE = "device fingerprint is not accepted for this session";
    private static final String INVALID_MESSAGE = "device fingerprint is not valid";
    private static final String DETAIL_USER_PUBLIC_ID = "userPublicId";
    private static final int MAX_FINGERPRINT_LENGTH = 100;

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private enum BindingFailure {
        NONE,
        MISSING_PROOF,
        FORBIDDEN
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String headerFingerprint = request.getHeader(ConsumerHeaders.DEVICE_FINGERPRINT);
        if (headerFingerprint != null && headerFingerprint.length() > MAX_FINGERPRINT_LENGTH) {
            reject(response, HttpStatus.BAD_REQUEST, INVALID_CODE, INVALID_MESSAGE);
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }
        BindingFailure failure = evaluateDeviceBinding(jwtAuthentication.getToken(), request);
        if (failure == BindingFailure.MISSING_PROOF) {
            reject(response, HttpStatus.UNAUTHORIZED, CODE, DEFAULT_MESSAGE);
            return;
        }
        if (failure == BindingFailure.FORBIDDEN) {
            publishMismatch(jwtAuthentication.getToken(), request);
            reject(response, HttpStatus.FORBIDDEN, MISMATCH_CODE, MISMATCH_MESSAGE);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static BindingFailure evaluateDeviceBinding(Jwt jwt, HttpServletRequest request) {
        String headerFingerprint = request.getHeader(ConsumerHeaders.DEVICE_FINGERPRINT);
        if (headerFingerprint == null || headerFingerprint.isBlank()) {
            return BindingFailure.MISSING_PROOF;
        }
        String claimFingerprint = jwt.getClaimAsString(JwtClaims.DEVICE_FINGERPRINT);
        if (claimFingerprint == null || claimFingerprint.isBlank()) {
            return BindingFailure.MISSING_PROOF;
        }
        if (!AuthenticationConstants.SCOPE_CONSUMER_FULL.equals(jwt.getClaimAsString(JwtClaims.SCOPE))) {
            return BindingFailure.FORBIDDEN;
        }
        if (!claimFingerprint.equals(headerFingerprint)) {
            return BindingFailure.FORBIDDEN;
        }
        return BindingFailure.NONE;
    }

    private void publishMismatch(Jwt jwt, HttpServletRequest request) {
        eventPublisher.publishEvent(NonTransactionalAuditEvent.of(AuditEventType.DEVICE_FINGERPRINT_MISMATCH,
                null, false, request.getHeader(ConsumerHeaders.DEVICE_FINGERPRINT),
                Map.of(DETAIL_USER_PUBLIC_ID, jwt.getSubject())));
    }

    private void reject(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ConsumerApiError.builder().code(code).defaultMessage(message).build());
    }
}
