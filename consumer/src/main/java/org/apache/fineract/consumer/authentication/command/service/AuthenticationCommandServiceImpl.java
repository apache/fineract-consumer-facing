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
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.authentication.command.data.PrincipalUserAuthCredentialsData;
import org.apache.fineract.consumer.authentication.command.data.PrincipalUserAuthData;
import org.apache.fineract.consumer.authentication.command.data.EstablishedSessionCommandData;
import org.apache.fineract.consumer.authentication.command.data.LoginChallengeCommandData;
import org.apache.fineract.consumer.authentication.command.data.LoginCommand;
import org.apache.fineract.consumer.authentication.command.data.LogoutCommand;
import org.apache.fineract.consumer.authentication.command.data.RefreshSessionCommand;
import org.apache.fineract.consumer.authentication.command.data.VerifyTwoFactorCommand;
import org.apache.fineract.consumer.authentication.command.domain.RefreshToken;
import org.apache.fineract.consumer.authentication.command.exception.AuthenticationKycRevokedException;
import org.apache.fineract.consumer.authentication.command.exception.InvalidCredentialsException;
import org.apache.fineract.consumer.authentication.command.exception.RefreshTokenInvalidException;
import org.apache.fineract.consumer.authentication.command.exception.TwoFactorInvalidException;
import org.apache.fineract.consumer.authentication.command.repository.RefreshTokenCommandRepository;
import org.apache.fineract.consumer.infrastructure.access.service.JwtDenylist;
import org.apache.fineract.consumer.infrastructure.audit.data.TransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.NonTransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.configs.AuthenticationProperties;
import org.apache.fineract.consumer.infrastructure.fineractclient.configs.FineractClientProperties;
import org.apache.fineract.consumer.infrastructure.jwt.data.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.infrastructure.jwt.service.JwtIssuer;
import org.apache.fineract.consumer.infrastructure.kyc.service.ClientStandingChecker;
import org.apache.fineract.consumer.infrastructure.web.service.EmailMaskingService;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpConstants;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpDestination;
import org.apache.fineract.consumer.infrastructure.otp.service.OtpService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationCommandServiceImpl implements AuthenticationCommandService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTE_LENGTH = 32;
    private static final String REFRESH_TOKEN_HASH_ALGORITHM = "SHA-256";
    private static final String DETAIL_REASON = "reason";
    private static final String REFRESH_REASON_TOKEN_UNKNOWN = "token_unknown";
    private static final String REFRESH_REASON_ROTATED_REPLAY = "rotated_token_replayed";
    private static final String REFRESH_REASON_REVOKED = "revoked";
    private static final String REFRESH_REASON_EXPIRED = "expired";
    private static final String REFRESH_REASON_DEVICE_MISMATCH = "device_mismatch";

    private final PrincipalUserAuthLookupPort principalUserAuthLookup;
    private final OtpService otpService;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final JwtDecoder jwtDecoder;
    private final JwtDenylist jwtDenylist;
    private final RefreshTokenCommandRepository refreshTokenCommandRepository;
    private final ClientStandingChecker clientStandingChecker;
    private final AuthenticationProperties authenticationProperties;
    private final FineractClientProperties fineractClientProperties;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public LoginChallengeCommandData login(LoginCommand command) {
        Optional<PrincipalUserAuthCredentialsData> candidate = principalUserAuthLookup.findCredentialsByEmail(command.getEmail());
        PrincipalUserAuthCredentialsData user = candidate
                .filter(PrincipalUserAuthCredentialsData::isBound)
                .filter(match -> passwordEncoder.matches(command.getPassword(), match.getPasswordHash()))
                .orElseThrow(() -> {
                    publishLoginFailure(candidate.orElse(null), command.getDeviceFingerprint());
                    return new InvalidCredentialsException();
                });

        OtpDestination destination = OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(command.getEmail())
                .build();
        otpService.createOtp(user.getPublicId(), destination);

        IssuedJwt challenge = jwtIssuer.issue(
                user.getPublicId().toString(),
                Map.of(
                        JwtClaims.PURPOSE, AuthenticationConstants.CHALLENGE_PURPOSE_VALUE,
                        JwtClaims.DEVICE_FINGERPRINT, command.getDeviceFingerprint()),
                authenticationProperties.getChallengeTokenTtl());

        return LoginChallengeCommandData.builder()
                .challengeToken(challenge.getTokenValue())
                .expiresAt(challenge.getExpiresAt())
                .sentTo(EmailMaskingService.mask(command.getEmail()))
                .build();
    }

    @Override
    @Transactional
    public EstablishedSessionCommandData verifyTwoFactor(VerifyTwoFactorCommand command) {
        Jwt challenge = decodeChallengeToken(command.getChallengeToken());
        boolean purposeValid = AuthenticationConstants.CHALLENGE_PURPOSE_VALUE
                .equals(challenge.getClaimAsString(JwtClaims.PURPOSE));
        boolean deviceValid = command.getDeviceFingerprint()
                .equals(challenge.getClaimAsString(JwtClaims.DEVICE_FINGERPRINT));
        if (!purposeValid || !deviceValid) {
            throw new TwoFactorInvalidException();
        }

        UUID publicId = UUID.fromString(challenge.getSubject());
        otpService.validateOtp(publicId, command.getToken(), TwoFactorInvalidException::new);

        PrincipalUserAuthData user = principalUserAuthLookup.findByPublicId(publicId);
        EstablishedSessionCommandData session = establishSession(user.getId(), publicId, user.isBound(),
                user.getFineractClientId(), command.getDeviceFingerprint(), null);
        eventPublisher.publishEvent(TransactionalAuditEvent.of(AuditEventType.LOGIN_SUCCESS,
                user.getId(), false, command.getDeviceFingerprint(), null));
        return session;
    }

    @Override
    @Transactional(noRollbackFor = RefreshTokenInvalidException.class)
    public EstablishedSessionCommandData refresh(RefreshSessionCommand command) {
        RefreshToken current = refreshTokenCommandRepository.findByTokenHash(sha256Hex(command.getRefreshToken()))
                .orElseThrow(() -> {
                    publishRefreshRejected(null, command.getDeviceFingerprint(), REFRESH_REASON_TOKEN_UNKNOWN);
                    return new RefreshTokenInvalidException();
                });

        boolean expired = current.getExpiresAt().isBefore(Instant.now());
        boolean deviceMismatch = !current.getDeviceFingerprint().equals(command.getDeviceFingerprint());

        RefreshToken predecessor;
        if (current.getRotatedTo() != null) {
            if (expired || deviceMismatch || !withinRotationGrace(current)) {
                revokeAllActiveTokens(current.getUserId());
                publishRefreshRejected(current.getUserId(), command.getDeviceFingerprint(),
                        REFRESH_REASON_ROTATED_REPLAY);
                throw new RefreshTokenInvalidException();
            }
            predecessor = null;
        } else {
            boolean revoked = current.getRevokedAt() != null;
            if (revoked || expired || deviceMismatch) {
                String reason = revoked ? REFRESH_REASON_REVOKED
                        : expired ? REFRESH_REASON_EXPIRED : REFRESH_REASON_DEVICE_MISMATCH;
                publishRefreshRejected(current.getUserId(), command.getDeviceFingerprint(), reason);
                throw new RefreshTokenInvalidException();
            }
            predecessor = current;
        }

        PrincipalUserAuthData user = principalUserAuthLookup.findById(current.getUserId());
        return establishSession(user.getId(), user.getPublicId(), user.isBound(),
                user.getFineractClientId(), command.getDeviceFingerprint(), predecessor);
    }

    @Override
    @Transactional
    public void logout(LogoutCommand command) {
        if (command.getAccessTokenId() != null && command.getAccessTokenExpiresAt() != null) {
            jwtDenylist.deny(command.getAccessTokenId(), command.getAccessTokenExpiresAt());
        }
        if (command.getRefreshToken() != null) {
            refreshTokenCommandRepository.findByTokenHash(sha256Hex(command.getRefreshToken()))
                    .ifPresent(token -> {
                        token.revoke();
                        refreshTokenCommandRepository.save(token);
                    });
        }
    }

    @Override
    @Transactional
    public void revokeAllSessions(Long userId, UUID publicId) {
        revokeAllActiveTokens(userId);
        jwtDenylist.denyAllIssuedUpTo(publicId.toString(), Instant.now());
    }

    @Override
    @Transactional
    public EstablishedSessionCommandData revokeAllSessionsAndReissue(Long userId, UUID publicId,
            String deviceFingerprint) {
        revokeAllActiveTokens(userId);
        jwtDenylist.denyAllIssuedUpTo(publicId.toString(),
                Instant.now().truncatedTo(ChronoUnit.SECONDS).minusMillis(1));
        PrincipalUserAuthData user = principalUserAuthLookup.findById(userId);
        return establishSession(user.getId(), user.getPublicId(), user.isBound(),
                user.getFineractClientId(), deviceFingerprint, null);
    }

    private EstablishedSessionCommandData establishSession(Long userId, UUID publicId, boolean kycVerified,
            Long fineractClientId, String deviceFingerprint, RefreshToken predecessor) {
        if (kycVerified && !clientStandingChecker.isActive(fineractClientId)) {
            throw new AuthenticationKycRevokedException();
        }
        IssuedJwt accessToken = jwtIssuer.issue(
                publicId.toString(),
                Map.of(
                        JwtClaims.TENANT, fineractClientProperties.getTenantId(),
                        JwtClaims.KYC_VERIFIED, kycVerified,
                        JwtClaims.SCOPE, AuthenticationConstants.SCOPE_CONSUMER_FULL,
                        JwtClaims.DEVICE_FINGERPRINT, deviceFingerprint),
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

    private void publishLoginFailure(PrincipalUserAuthCredentialsData knownUser, String deviceFingerprint) {
        Long userId = null;
        if (knownUser != null) {
            try {
                userId = principalUserAuthLookup.findByPublicId(knownUser.getPublicId()).getId();
            } catch (RuntimeException e) {
                log.warn("login-failure audit could not resolve user id; recording without identity link", e);
            }
        }
        eventPublisher.publishEvent(NonTransactionalAuditEvent.of(AuditEventType.LOGIN_FAILURE,
                userId, knownUser == null, deviceFingerprint, null));
    }

    private void publishRefreshRejected(Long userId, String deviceFingerprint, String reason) {
        eventPublisher.publishEvent(NonTransactionalAuditEvent.of(AuditEventType.REFRESH_TOKEN_REJECTED,
                userId, userId == null, deviceFingerprint, Map.of(DETAIL_REASON, reason)));
    }

    private Jwt decodeChallengeToken(String challengeToken) {
        try {
            return jwtDecoder.decode(challengeToken);
        } catch (JwtException e) {
            throw new TwoFactorInvalidException(e);
        }
    }

    private boolean withinRotationGrace(RefreshToken token) {
        return token.getRevokedAt() != null
                && token.getRevokedAt().plus(authenticationProperties.getRotationGraceTtl()).isAfter(Instant.now());
    }

    private void revokeAllActiveTokens(Long userId) {
        refreshTokenCommandRepository.findByUserId(userId).forEach(token -> {
            token.revoke();
            refreshTokenCommandRepository.save(token);
        });
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

}
