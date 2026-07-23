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

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
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
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.apache.fineract.consumer.infrastructure.web.AuthCookieFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/authentication", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuthenticationCommandController {

    private final AuthenticationCommandService authenticationCommandService;
    private final AuthCookieFactory authCookieFactory;

    @Operation(operationId = "login")
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

    @Operation(operationId = "verifyTwoFactor")
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

    @Operation(operationId = "refreshSession")
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

    @Operation(operationId = "logout")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = AuthenticationConstants.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            @AuthenticationPrincipal Jwt accessToken) {
        authenticationCommandService.logout(LogoutCommand.builder()
                .refreshToken(refreshToken)
                .accessTokenId(accessToken.getId())
                .accessTokenExpiresAt(accessToken.getExpiresAt())
                .build());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookieFactory.expiredAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, authCookieFactory.expiredRefreshCookie().toString())
                .build();
    }

    private ResponseEntity<SessionCommandData> sessionResponse(EstablishedSessionCommandData session) {
        ResponseCookie accessCookie = authCookieFactory.accessCookie(session);
        ResponseCookie refreshCookie = authCookieFactory.refreshCookie(session);
        SessionCommandData body = SessionCommandData.builder()
                .expiresAt(session.getAccessTokenExpiresAt())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(body);
    }
}
