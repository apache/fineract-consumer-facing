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

package org.apache.fineract.consumer.authentication.command.api;

import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.authentication.command.data.EstablishedSessionCommandData;
import org.apache.fineract.consumer.authentication.command.data.LoginChallengeCommandData;
import org.apache.fineract.consumer.authentication.command.data.LoginCommand;
import org.apache.fineract.consumer.authentication.command.data.LoginCommandRequest;
import org.apache.fineract.consumer.authentication.command.data.LogoutCommand;
import org.apache.fineract.consumer.authentication.command.data.RefreshSessionCommand;
import org.apache.fineract.consumer.authentication.command.data.SessionCommandData;
import org.apache.fineract.consumer.authentication.command.data.VerifyTwoFactorCommand;
import org.apache.fineract.consumer.authentication.command.data.VerifyTwoFactorCommandRequest;
import org.apache.fineract.consumer.authentication.command.exception.RefreshTokenInvalidException;
import org.apache.fineract.consumer.authentication.command.service.AuthenticationCommandService;
import org.apache.fineract.consumer.infrastructure.configs.AuthenticationProperties;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/authentication")
@RequiredArgsConstructor
public class AuthenticationCommandController {

    private static final String REFRESH_COOKIE_PATH = "/api/v1/authentication";
    private static final String SAME_SITE_STRICT = "Strict";

    private final AuthenticationCommandService authenticationCommandService;
    private final AuthenticationProperties authenticationProperties;

    @PostMapping("/login")
    public ResponseEntity<LoginChallengeCommandData> login(
            @Valid @RequestBody LoginCommandRequest request,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint) {
        LoginCommand command = LoginCommand.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(authenticationCommandService.login(command));
    }

    @PostMapping("/2fa")
    public ResponseEntity<SessionCommandData> verifyTwoFactor(
            @Valid @RequestBody VerifyTwoFactorCommandRequest request,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint) {
        VerifyTwoFactorCommand command = VerifyTwoFactorCommand.builder()
                .challengeToken(request.getChallengeToken())
                .token(request.getToken())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return sessionResponse(authenticationCommandService.verifyTwoFactor(command));
    }

    @PostMapping("/refresh")
    public ResponseEntity<SessionCommandData> refresh(
            @CookieValue(value = AuthenticationConstants.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint) {
        if (refreshToken == null) {
            throw new RefreshTokenInvalidException();
        }
        RefreshSessionCommand command = RefreshSessionCommand.builder()
                .refreshToken(refreshToken)
                .deviceFingerprint(deviceFingerprint)
                .build();
        return sessionResponse(authenticationCommandService.refresh(command));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = AuthenticationConstants.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null) {
            authenticationCommandService.logout(LogoutCommand.builder()
                    .refreshToken(refreshToken)
                    .build());
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<SessionCommandData> sessionResponse(EstablishedSessionCommandData session) {
        ResponseCookie cookie = refreshCookie(
                session.getRefreshToken(),
                Duration.between(Instant.now(), session.getRefreshTokenExpiresAt()));
        SessionCommandData body = SessionCommandData.builder()
                .accessToken(session.getAccessToken())
                .expiresAt(session.getAccessTokenExpiresAt())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(AuthenticationConstants.REFRESH_TOKEN_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(authenticationProperties.isRefreshCookieSecure())
                .sameSite(SAME_SITE_STRICT)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
