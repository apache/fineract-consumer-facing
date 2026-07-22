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
package org.apache.fineract.consumer.user.command.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.authentication.command.data.EstablishedSessionCommandData;
import org.apache.fineract.consumer.authentication.command.data.SessionCommandData;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.apache.fineract.consumer.infrastructure.web.AuthCookieFactory;
import org.apache.fineract.consumer.user.command.data.ConfirmPasswordChangeCommand;
import org.apache.fineract.consumer.user.command.data.ConfirmPasswordChangeCommandRequest;
import org.apache.fineract.consumer.user.command.data.ForgotPasswordCommand;
import org.apache.fineract.consumer.user.command.data.ForgotPasswordCommandRequest;
import org.apache.fineract.consumer.user.command.data.InitiatePasswordChangeCommand;
import org.apache.fineract.consumer.user.command.data.InitiatePasswordChangeCommandRequest;
import org.apache.fineract.consumer.user.command.data.ResetPasswordCommand;
import org.apache.fineract.consumer.user.command.data.ResetPasswordCommandRequest;
import org.apache.fineract.consumer.user.command.data.UserPasswordChangeChallengeCommandData;
import org.apache.fineract.consumer.user.command.service.UserCommandService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/user", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class UserCommandController {

    private final UserCommandService userCommandService;
    private final AuthCookieFactory authCookieFactory;

    @Operation(operationId = "initiatePasswordChange")
    @PostMapping("/password/change/initiate")
    public ResponseEntity<UserPasswordChangeChallengeCommandData> initiatePasswordChange(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @Valid @RequestBody InitiatePasswordChangeCommandRequest request) {
        InitiatePasswordChangeCommand command = InitiatePasswordChangeCommand.builder()
                .currentPassword(request.getCurrentPassword())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(userCommandService.initiatePasswordChange(jwt, command));
    }

    @Operation(operationId = "confirmPasswordChange")
    @PostMapping("/password/change/confirm")
    public ResponseEntity<SessionCommandData> confirmPasswordChange(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @Valid @RequestBody ConfirmPasswordChangeCommandRequest request) {
        ConfirmPasswordChangeCommand command = ConfirmPasswordChangeCommand.builder()
                .stepUpToken(request.getStepUpToken())
                .otp(request.getOtp())
                .newPassword(request.getNewPassword())
                .deviceFingerprint(deviceFingerprint)
                .build();
        EstablishedSessionCommandData session = userCommandService.confirmPasswordChange(jwt, command);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieFactory.accessCookie(session).toString())
                .header(HttpHeaders.SET_COOKIE, authCookieFactory.refreshCookie(session).toString())
                .body(SessionCommandData.builder()
                        .expiresAt(session.getAccessTokenExpiresAt())
                        .build());
    }

    @Operation(operationId = "forgotPassword")
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @Valid @RequestBody ForgotPasswordCommandRequest request) {
        userCommandService.forgotPassword(ForgotPasswordCommand.builder()
                .email(request.getEmail())
                .build());
        return ResponseEntity.accepted().build();
    }

    @Operation(operationId = "resetPassword")
    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @Valid @RequestBody ResetPasswordCommandRequest request) {
        userCommandService.resetPassword(ResetPasswordCommand.builder()
                .email(request.getEmail())
                .otp(request.getOtp())
                .newPassword(request.getNewPassword())
                .build());
        return ResponseEntity.noContent().build();
    }
}
