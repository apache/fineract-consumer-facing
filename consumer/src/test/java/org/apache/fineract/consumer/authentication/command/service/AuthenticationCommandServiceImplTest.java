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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.authentication.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthenticationCommandServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final UUID EXTERNAL_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String EMAIL = "user@test.com";
    private static final String RAW_PASSWORD = "Correct-password1";
    private static final String PASSWORD_HASH = "{bcrypt}$2a$10$hash";
    private static final String DEVICE_FINGERPRINT = "test-device";
    private static final String OTHER_DEVICE_FINGERPRINT = "other-device";
    private static final String TENANT_ID = "default";
    private static final String OTP_TOKEN = "ABC123";
    private static final String CHALLENGE_TOKEN = "challenge-token";
    private static final String PRESENTED_REFRESH_TOKEN = "presented-refresh-token";
    private static final Long NEW_TOKEN_ID = 42L;
    private static final Long SUCCESSOR_ID = 43L;

    private static final AuthenticationProperties PROPERTIES = new AuthenticationProperties(
            Duration.ofMinutes(15), Duration.ofMinutes(5), Duration.ofDays(1), false);
    private static final FineractClientProperties FINERACT_PROPERTIES = new FineractClientProperties(
            null, null, null, TENANT_ID);

    @Mock
    private UserQueryService userQueryService;
    @Mock
    private OtpCommandService otpCommandService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtIssuer jwtIssuer;
    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private RefreshTokenCommandRepository refreshTokenCommandRepository;

    private AuthenticationCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationCommandServiceImpl(userQueryService, otpCommandService, passwordEncoder,
                jwtIssuer, jwtDecoder, refreshTokenCommandRepository, PROPERTIES, FINERACT_PROPERTIES);
    }

    private static UserCredentialsQueryData credentials(UserStatus status) {
        return UserCredentialsQueryData.builder()
                .id(USER_ID)
                .externalId(EXTERNAL_ID)
                .status(status)
                .passwordHash(PASSWORD_HASH)
                .build();
    }

    private static LoginCommand loginCommand() {
        return LoginCommand.builder()
                .email(EMAIL)
                .password(RAW_PASSWORD)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private static IssuedJwt issuedJwt(String tokenValue, Duration ttl) {
        return IssuedJwt.builder()
                .tokenValue(tokenValue)
                .expiresAt(Instant.now().plus(ttl))
                .build();
    }

    @Nested
    class Login {

        @Test
        void successSendsOtpAndIssuesDeviceBoundChallenge() {
            when(userQueryService.findCredentialsByEmail(EMAIL)).thenReturn(Optional.of(credentials(UserStatus.BOUND)));
            when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
            when(jwtIssuer.issue(eq(EXTERNAL_ID.toString()), anyMap(), eq(PROPERTIES.getChallengeTokenTtl())))
                    .thenReturn(issuedJwt(CHALLENGE_TOKEN, PROPERTIES.getChallengeTokenTtl()));

            LoginChallengeCommandData challenge = service.login(loginCommand());

            verify(otpCommandService).createOtp(EXTERNAL_ID, OtpDestination.builder()
                    .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                    .target(EMAIL)
                    .build());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> claims = ArgumentCaptor.forClass(Map.class);
            verify(jwtIssuer).issue(eq(EXTERNAL_ID.toString()), claims.capture(),
                    eq(PROPERTIES.getChallengeTokenTtl()));
            assertThat(claims.getValue())
                    .containsEntry(JwtClaims.PURPOSE, AuthenticationConstants.CHALLENGE_PURPOSE_VALUE)
                    .containsEntry(JwtClaims.DEVICE_FINGERPRINT, DEVICE_FINGERPRINT);
            assertThat(challenge.getChallengeToken()).isEqualTo(CHALLENGE_TOKEN);
            assertThat(challenge.getSentTo()).isEqualTo("u***@test.com");
        }

        @Test
        void unknownEmailIsRejectedWithoutSendingOtp() {
            when(userQueryService.findCredentialsByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.login(loginCommand()))
                    .isInstanceOf(InvalidCredentialsException.class);
            verify(otpCommandService, never()).createOtp(any(), any());
        }

        @Test
        void wrongPasswordIsRejectedWithoutSendingOtp() {
            when(userQueryService.findCredentialsByEmail(EMAIL)).thenReturn(Optional.of(credentials(UserStatus.BOUND)));
            when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(false);

            assertThatThrownBy(() -> service.login(loginCommand()))
                    .isInstanceOf(InvalidCredentialsException.class);
            verify(otpCommandService, never()).createOtp(any(), any());
        }

        @Test
        void notBoundUserIsRejectedBeforePasswordCheck() {
            when(userQueryService.findCredentialsByEmail(EMAIL))
                    .thenReturn(Optional.of(credentials(UserStatus.PENDING_OTP)));

            assertThatThrownBy(() -> service.login(loginCommand()))
                    .isInstanceOf(InvalidCredentialsException.class);
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }

    @Nested
    class VerifyTwoFactor {

        private VerifyTwoFactorCommand command() {
            return VerifyTwoFactorCommand.builder()
                    .challengeToken(CHALLENGE_TOKEN)
                    .token(OTP_TOKEN)
                    .deviceFingerprint(DEVICE_FINGERPRINT)
                    .build();
        }

        private Jwt challengeJwt(String purpose, String deviceFingerprint) {
            return Jwt.withTokenValue(CHALLENGE_TOKEN)
                    .header("alg", "ES256")
                    .subject(EXTERNAL_ID.toString())
                    .claim(JwtClaims.PURPOSE, purpose)
                    .claim(JwtClaims.DEVICE_FINGERPRINT, deviceFingerprint)
                    .build();
        }

        @Test
        void successEstablishesSessionWithHashedPersistedRefreshToken() {
            when(jwtDecoder.decode(CHALLENGE_TOKEN))
                    .thenReturn(challengeJwt(AuthenticationConstants.CHALLENGE_PURPOSE_VALUE, DEVICE_FINGERPRINT));
            when(userQueryService.findByExternalId(EXTERNAL_ID)).thenReturn(UserQueryData.builder()
                    .id(USER_ID)
                    .externalId(EXTERNAL_ID)
                    .email(EMAIL)
                    .status(UserStatus.BOUND)
                    .build());
            when(jwtIssuer.issue(eq(EXTERNAL_ID.toString()), anyMap(), eq(PROPERTIES.getAccessTokenTtl())))
                    .thenReturn(issuedJwt("access-token", PROPERTIES.getAccessTokenTtl()));
            when(refreshTokenCommandRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            EstablishedSessionCommandData session = service.verifyTwoFactor(command());

            verify(otpCommandService).validateOtp(EXTERNAL_ID, OTP_TOKEN);
            assertThat(session.getAccessToken()).isEqualTo("access-token");
            assertThat(session.getRefreshToken()).isNotBlank();

            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenCommandRepository).save(saved.capture());
            assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getValue().getDeviceFingerprint()).isEqualTo(DEVICE_FINGERPRINT);
            assertThat(saved.getValue().getTokenHash())
                    .isNotEqualTo(session.getRefreshToken())
                    .hasSize(64)
                    .matches("[0-9a-f]+");
        }

        @Test
        void missingChallengePurposeIsRejected() {
            when(jwtDecoder.decode(CHALLENGE_TOKEN)).thenReturn(challengeJwt("other-purpose", DEVICE_FINGERPRINT));

            assertThatThrownBy(() -> service.verifyTwoFactor(command()))
                    .isInstanceOf(TwoFactorInvalidException.class);
            verify(otpCommandService, never()).validateOtp(any(), any());
        }

        @Test
        void deviceFingerprintMismatchIsRejected() {
            when(jwtDecoder.decode(CHALLENGE_TOKEN)).thenReturn(
                    challengeJwt(AuthenticationConstants.CHALLENGE_PURPOSE_VALUE, OTHER_DEVICE_FINGERPRINT));

            assertThatThrownBy(() -> service.verifyTwoFactor(command()))
                    .isInstanceOf(TwoFactorInvalidException.class);
            verify(otpCommandService, never()).validateOtp(any(), any());
        }

        @Test
        void invalidChallengeTokenIsRejected() {
            when(jwtDecoder.decode(CHALLENGE_TOKEN)).thenThrow(new JwtException("bad token"));

            assertThatThrownBy(() -> service.verifyTwoFactor(command()))
                    .isInstanceOf(TwoFactorInvalidException.class);
        }

        @Test
        void wrongOtpIsWrappedAsTwoFactorFailure() {
            when(jwtDecoder.decode(CHALLENGE_TOKEN))
                    .thenReturn(challengeJwt(AuthenticationConstants.CHALLENGE_PURPOSE_VALUE, DEVICE_FINGERPRINT));
            org.mockito.Mockito.doThrow(new OtpTokenInvalidException())
                    .when(otpCommandService).validateOtp(EXTERNAL_ID, OTP_TOKEN);

            assertThatThrownBy(() -> service.verifyTwoFactor(command()))
                    .isInstanceOf(TwoFactorInvalidException.class);
            verify(refreshTokenCommandRepository, never()).save(any());
        }
    }

    @Nested
    class Refresh {

        private RefreshSessionCommand command(String deviceFingerprint) {
            return RefreshSessionCommand.builder()
                    .refreshToken(PRESENTED_REFRESH_TOKEN)
                    .deviceFingerprint(deviceFingerprint)
                    .build();
        }

        private RefreshToken activeToken() {
            return RefreshToken.issue(USER_ID, "hash-of-presented-token", DEVICE_FINGERPRINT,
                    Instant.now().plusSeconds(3600));
        }

        @Test
        void successRotatesAndRevokesPredecessor() {
            RefreshToken predecessor = activeToken();
            when(refreshTokenCommandRepository.findByTokenHash(anyString())).thenReturn(Optional.of(predecessor));
            when(userQueryService.findById(USER_ID)).thenReturn(UserQueryData.builder()
                    .id(USER_ID)
                    .externalId(EXTERNAL_ID)
                    .email(EMAIL)
                    .status(UserStatus.BOUND)
                    .build());
            when(jwtIssuer.issue(eq(EXTERNAL_ID.toString()), anyMap(), eq(PROPERTIES.getAccessTokenTtl())))
                    .thenReturn(issuedJwt("new-access-token", PROPERTIES.getAccessTokenTtl()));
            when(refreshTokenCommandRepository.save(any())).thenAnswer(invocation -> {
                RefreshToken saved = invocation.getArgument(0);
                if (saved.getId() == null) {
                    ReflectionTestUtils.setField(saved, "id", NEW_TOKEN_ID);
                }
                return saved;
            });

            EstablishedSessionCommandData session = service.refresh(command(DEVICE_FINGERPRINT));

            assertThat(session.getAccessToken()).isEqualTo("new-access-token");
            assertThat(session.getRefreshToken()).isNotEqualTo(PRESENTED_REFRESH_TOKEN);
            assertThat(predecessor.getRotatedTo()).isEqualTo(NEW_TOKEN_ID);
            assertThat(predecessor.getRevokedAt()).isNotNull();
        }

        @Test
        void replayedTokenRevokesSuccessorChain() {
            RefreshToken replayed = activeToken();
            replayed.rotateTo(SUCCESSOR_ID);
            RefreshToken successor = activeToken();
            when(refreshTokenCommandRepository.findByTokenHash(anyString())).thenReturn(Optional.of(replayed));
            when(refreshTokenCommandRepository.findById(SUCCESSOR_ID)).thenReturn(Optional.of(successor));
            when(refreshTokenCommandRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertThatThrownBy(() -> service.refresh(command(DEVICE_FINGERPRINT)))
                    .isInstanceOf(RefreshTokenInvalidException.class);
            assertThat(successor.getRevokedAt()).isNotNull();
            verify(jwtIssuer, never()).issue(anyString(), anyMap(), any());
        }

        @Test
        void unknownTokenIsRejected() {
            when(refreshTokenCommandRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.refresh(command(DEVICE_FINGERPRINT)))
                    .isInstanceOf(RefreshTokenInvalidException.class);
        }

        @Test
        void revokedTokenIsRejected() {
            RefreshToken revoked = activeToken();
            revoked.revoke();
            when(refreshTokenCommandRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

            assertThatThrownBy(() -> service.refresh(command(DEVICE_FINGERPRINT)))
                    .isInstanceOf(RefreshTokenInvalidException.class);
        }

        @Test
        void expiredTokenIsRejected() {
            RefreshToken expired = RefreshToken.issue(USER_ID, "hash-of-presented-token", DEVICE_FINGERPRINT,
                    Instant.now().minusSeconds(1));
            when(refreshTokenCommandRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> service.refresh(command(DEVICE_FINGERPRINT)))
                    .isInstanceOf(RefreshTokenInvalidException.class);
        }

        @Test
        void deviceFingerprintMismatchIsRejected() {
            when(refreshTokenCommandRepository.findByTokenHash(anyString())).thenReturn(Optional.of(activeToken()));

            assertThatThrownBy(() -> service.refresh(command(OTHER_DEVICE_FINGERPRINT)))
                    .isInstanceOf(RefreshTokenInvalidException.class);
        }
    }

    @Nested
    class Logout {

        @Test
        void knownTokenIsRevoked() {
            RefreshToken token = RefreshToken.issue(USER_ID, "hash-of-presented-token", DEVICE_FINGERPRINT,
                    Instant.now().plusSeconds(3600));
            when(refreshTokenCommandRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
            when(refreshTokenCommandRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.logout(LogoutCommand.builder().refreshToken(PRESENTED_REFRESH_TOKEN).build());

            assertThat(token.getRevokedAt()).isNotNull();
            verify(refreshTokenCommandRepository).save(token);
        }

        @Test
        void unknownTokenIsIgnored() {
            when(refreshTokenCommandRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            service.logout(LogoutCommand.builder().refreshToken(PRESENTED_REFRESH_TOKEN).build());

            verify(refreshTokenCommandRepository, never()).save(any());
        }
    }
}
