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
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.exception.ConsumerApiError;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
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
    private static final String DEFAULT_MESSAGE = "device fingerprint does not match the session";

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!satisfiesDeviceBinding(jwtAuthentication.getToken(), request)) {
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static boolean satisfiesDeviceBinding(Jwt jwt, HttpServletRequest request) {
        if (!AuthenticationConstants.SCOPE_CONSUMER_FULL.equals(jwt.getClaimAsString(JwtClaims.SCOPE))) {
            return false;
        }
        String claimFingerprint = jwt.getClaimAsString(JwtClaims.DEVICE_FINGERPRINT);
        return claimFingerprint != null && !claimFingerprint.isBlank()
                && claimFingerprint.equals(request.getHeader(ConsumerHeaders.DEVICE_FINGERPRINT));
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ConsumerApiError.builder().code(CODE).defaultMessage(DEFAULT_MESSAGE).build());
    }
}
