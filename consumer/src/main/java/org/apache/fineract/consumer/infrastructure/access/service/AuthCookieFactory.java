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

package org.apache.fineract.consumer.infrastructure.access.service;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.AuthCookiePayload;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.configs.AuthenticationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthCookieFactory {

    private static final String ACCESS_COOKIE_PATH = "/api/v1";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/authentication";
    private static final String SAME_SITE_STRICT = "Strict";

    private final AuthenticationProperties authenticationProperties;

    public ResponseCookie accessCookie(AuthCookiePayload payload) {
        return accessCookie(payload.getAccessToken(),
                Duration.between(Instant.now(), payload.getAccessTokenExpiresAt()));
    }

    public ResponseCookie refreshCookie(AuthCookiePayload payload) {
        return refreshCookie(payload.getRefreshToken(),
                Duration.between(Instant.now(), payload.getRefreshTokenExpiresAt()));
    }

    public ResponseCookie expiredAccessCookie() {
        return accessCookie("", Duration.ZERO);
    }

    public ResponseCookie expiredRefreshCookie() {
        return refreshCookie("", Duration.ZERO);
    }

    private ResponseCookie accessCookie(String value, Duration maxAge) {
        return cookie(AuthenticationConstants.ACCESS_TOKEN_COOKIE_NAME, value, ACCESS_COOKIE_PATH, maxAge);
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return cookie(AuthenticationConstants.REFRESH_TOKEN_COOKIE_NAME, value, REFRESH_COOKIE_PATH, maxAge);
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(authenticationProperties.isCookieSecure())
                .sameSite(SAME_SITE_STRICT)
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
