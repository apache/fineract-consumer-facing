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

package org.apache.fineract.consumer.authentication.command.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.authentication.command.data.EstablishedSessionCommandData;
import org.apache.fineract.consumer.authentication.command.data.LoginChallengeCommandData;
import org.apache.fineract.consumer.authentication.command.data.LoginCommand;
import org.apache.fineract.consumer.authentication.command.data.LogoutCommand;
import org.apache.fineract.consumer.authentication.command.data.RefreshSessionCommand;
import org.apache.fineract.consumer.authentication.command.data.VerifyTwoFactorCommand;
import org.apache.fineract.consumer.authentication.command.domain.RefreshToken;
import org.apache.fineract.consumer.authentication.command.exception.InvalidCredentialsException;
import org.apache.fineract.consumer.authentication.command.exception.RefreshTokenInvalidException;
import org.apache.fineract.consumer.authentication.command.exception.TwoFactorInvalidException;
import org.apache.fineract.consumer.authentication.command.repository.RefreshTokenCommandRepository;
import org.apache.fineract.consumer.infrastructure.command.Command;
import org.apache.fineract.consumer.infrastructure.configs.AuthenticationProperties;
import org.apache.fineract.consumer.infrastructure.fineractclient.configs.FineractClientProperties;
import org.apache.fineract.consumer.infrastructure.jwt.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.jwt.JwtClaims;
import org.apache.fineract.consumer.infrastructure.jwt.JwtIssuer;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.service.OtpCommandService;
import org.apache.fineract.consumer.user.command.domain.UserStatus;
import org.apache.fineract.consumer.user.query.data.UserCredentialsQueryData;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationCommandServiceImpl implements AuthenticationCommandService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTE_LENGTH = 32;
    private static final String REFRESH_TOKEN_HASH_ALGORITHM = "SHA-256";

    private final UserQueryService userQueryService;
    private final OtpCommandService otpCommandService;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final JwtDecoder jwtDecoder;
    private final RefreshTokenCommandRepository refreshTokenCommandRepository;
    private final AuthenticationProperties authenticationProperties;
    private final FineractClientProperties fineractClientProperties;

    @Override
    @Command
    public LoginChallengeCommandData login(LoginCommand command) {
        UserCredentialsQueryData user = userQueryService.findCredentialsByEmail(command.getEmail())
                .filter(candidate -> candidate.getStatus() == UserStatus.BOUND)
                .filter(candidate -> passwordEncoder.matches(command.getPassword(), candidate.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        OtpDestination destination = OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(command.getEmail())
                .build();
        otpCommandService.createOtp(user.getExternalId(), destination);

        IssuedJwt challenge = jwtIssuer.issue(
                user.getExternalId().toString(),
                Map.of(
                        JwtClaims.PURPOSE, AuthenticationConstants.CHALLENGE_PURPOSE_VALUE,
                        JwtClaims.DEVICE_FINGERPRINT, command.getDeviceFingerprint()),
                authenticationProperties.getChallengeTokenTtl());

        return LoginChallengeCommandData.builder()
                .challengeToken(challenge.getTokenValue())
                .expiresAt(challenge.getExpiresAt())
                .sentTo(maskEmail(command.getEmail()))
                .build();
    }

    @Override
    @Command
    public EstablishedSessionCommandData verifyTwoFactor(VerifyTwoFactorCommand command) {
        Jwt challenge = decodeChallengeToken(command.getChallengeToken());
        boolean purposeValid = AuthenticationConstants.CHALLENGE_PURPOSE_VALUE
                .equals(challenge.getClaimAsString(JwtClaims.PURPOSE));
        boolean deviceValid = command.getDeviceFingerprint()
                .equals(challenge.getClaimAsString(JwtClaims.DEVICE_FINGERPRINT));
        if (!purposeValid || !deviceValid) {
            throw new TwoFactorInvalidException();
        }

        UUID externalId = UUID.fromString(challenge.getSubject());
        try {
            otpCommandService.validateOtp(externalId, command.getToken());
        } catch (OtpTokenInvalidException e) {
            throw new TwoFactorInvalidException(e);
        }

        UserQueryData user = userQueryService.findByExternalId(externalId);
        return establishSession(user.getId(), externalId, command.getDeviceFingerprint(), null);
    }

    @Override
    @Command
    public EstablishedSessionCommandData refresh(RefreshSessionCommand command) {
        RefreshToken current = refreshTokenCommandRepository.findByTokenHash(sha256Hex(command.getRefreshToken()))
                .orElseThrow(RefreshTokenInvalidException::new);
        
        if (current.getRotatedTo() != null) {
            revokeSuccessorChain(current);
            throw new RefreshTokenInvalidException();
        }
        boolean revoked = current.getRevokedAt() != null;
        boolean expired = current.getExpiresAt().isBefore(Instant.now());
        boolean deviceMismatch = !current.getDeviceFingerprint().equals(command.getDeviceFingerprint());
        if (revoked || expired || deviceMismatch) {
            throw new RefreshTokenInvalidException();
        }

        UserQueryData user = userQueryService.findById(current.getUserId());
        return establishSession(user.getId(), user.getExternalId(), command.getDeviceFingerprint(), current);
    }

    @Override
    @Command
    public void logout(LogoutCommand command) {
        refreshTokenCommandRepository.findByTokenHash(sha256Hex(command.getRefreshToken()))
                .ifPresent(token -> {
                    token.revoke();
                    refreshTokenCommandRepository.save(token);
                });
    }

    private EstablishedSessionCommandData establishSession(Long userId, UUID externalId, String deviceFingerprint,
            RefreshToken predecessor) {
        IssuedJwt accessToken = jwtIssuer.issue(
                externalId.toString(),
                Map.of(JwtClaims.TENANT, fineractClientProperties.getTenantId()),
                authenticationProperties.getAccessTokenTtl());

        String refreshTokenValue = generateRefreshTokenValue();
        Instant refreshExpiresAt = Instant.now().plus(authenticationProperties.getRefreshTokenTtl());
        RefreshToken issued = refreshTokenCommandRepository.save(
                RefreshToken.issue(userId, sha256Hex(refreshTokenValue), deviceFingerprint, refreshExpiresAt));
        if (predecessor != null) {
            predecessor.rotateTo(issued.getId());
            refreshTokenCommandRepository.save(predecessor);
        }

        return EstablishedSessionCommandData.builder()
                .accessToken(accessToken.getTokenValue())
                .accessTokenExpiresAt(accessToken.getExpiresAt())
                .refreshToken(refreshTokenValue)
                .refreshTokenExpiresAt(refreshExpiresAt)
                .build();
    }

    private Jwt decodeChallengeToken(String challengeToken) {
        try {
            return jwtDecoder.decode(challengeToken);
        } catch (JwtException e) {
            throw new TwoFactorInvalidException(e);
        }
    }

    private void revokeSuccessorChain(RefreshToken start) {
        RefreshToken current = start;
        while (current != null) {
            current.revoke();
            refreshTokenCommandRepository.save(current);
            current = current.getRotatedTo() == null
                    ? null
                    : refreshTokenCommandRepository.findById(current.getRotatedTo()).orElse(null);
        }
    }

    private static String generateRefreshTokenValue() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(REFRESH_TOKEN_HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(REFRESH_TOKEN_HASH_ALGORITHM + " unavailable", e);
        }
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
