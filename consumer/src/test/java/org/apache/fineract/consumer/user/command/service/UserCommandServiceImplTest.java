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

package org.apache.fineract.consumer.user.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.authentication.command.data.EstablishedSessionCommandData;
import org.apache.fineract.consumer.authentication.command.service.AuthenticationCommandService;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.jwt.data.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpConstants;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpTokenService;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.service.OtpCommandService;
import org.apache.fineract.consumer.user.command.data.ConfirmPasswordChangeCommand;
import org.apache.fineract.consumer.user.command.data.CreateUserCommand;
import org.apache.fineract.consumer.user.command.data.ForgotPasswordCommand;
import org.apache.fineract.consumer.user.command.data.InitiatePasswordChangeCommand;
import org.apache.fineract.consumer.user.command.data.ResetPasswordCommand;
import org.apache.fineract.consumer.user.command.data.UserCommandConstants;
import org.apache.fineract.consumer.user.command.data.UserCreatedCommandData;
import org.apache.fineract.consumer.user.command.data.UserPasswordChangeChallengeCommandData;
import org.apache.fineract.consumer.user.command.domain.User;
import org.apache.fineract.consumer.user.command.domain.UserStatus;
import org.apache.fineract.consumer.user.command.exception.UserAlreadyExistsException;
import org.apache.fineract.consumer.user.command.exception.UserNotFoundException;
import org.apache.fineract.consumer.user.command.exception.UserPasswordInvalidException;
import org.apache.fineract.consumer.user.command.exception.UserPasswordResetInvalidException;
import org.apache.fineract.consumer.user.command.exception.UserStepUpInvalidException;
import org.apache.fineract.consumer.user.command.repository.UserCommandRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String EMAIL = "user@test.com";
    private static final String MASKED_EMAIL = "u***@test.com";
    private static final String PASSWORD_HASH = "{bcrypt}$2a$10$hash";
    private static final Long FINERACT_CLIENT_ID = 42L;
    private static final String DEVICE_FINGERPRINT = "device-abc";
    private static final String STEP_UP_TOKEN = "step-up-token";
    private static final String OTP = "123456";
    private static final String ACTION_FINGERPRINT = "action-fingerprint";
    private static final String CURRENT_PASSWORD = "Current-password1!";
    private static final String NEW_PASSWORD = "New-password-longer1!";
    private static final String NEW_PASSWORD_HASH = "{bcrypt}$2a$10$newhash";

    @Mock
    private UserCommandRepository repository;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OtpCommandService otpCommandService;

    @Mock
    private StepUpTokenService stepUpTokenService;

    @Mock
    private AuthenticationCommandService authenticationCommandService;

    @Mock
    private AsyncTaskExecutor taskExecutor;

    @InjectMocks
    private UserCommandServiceImpl service;

    private static CreateUserCommand createCommand() {
        return CreateUserCommand.builder()
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .fineractClientId(FINERACT_CLIENT_ID)
                .build();
    }

    private static User existingUser() {
        return User.createPendingOtp(PUBLIC_ID, EMAIL, PASSWORD_HASH, FINERACT_CLIENT_ID);
    }

    @Nested
    class Create {

        @Test
        void successPersistsPendingOtpUserAndReturnsIdentifiers() {
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(repository.findByFineractClientId(FINERACT_CLIENT_ID)).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", USER_ID);
                return saved;
            });

            UserCreatedCommandData created = service.create(createCommand());

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(repository).save(saved.capture());
            assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getValue().getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(saved.getValue().getFineractClientId()).isEqualTo(FINERACT_CLIENT_ID);
            assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.PENDING_OTP);
            assertThat(saved.getValue().getPublicId()).isNotNull();

            assertThat(created.getUserId()).isEqualTo(USER_ID);
            assertThat(created.getPublicId()).isEqualTo(saved.getValue().getPublicId());
        }

        @Test
        void existingEmailIsRejectedWithoutSaving() {
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));

            assertThatThrownBy(() -> service.create(createCommand()))
                    .isInstanceOf(UserAlreadyExistsException.class);
            verify(repository, never()).save(any());
        }

        @Test
        void existingFineractClientIsRejectedWithoutSaving() {
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(repository.findByFineractClientId(FINERACT_CLIENT_ID)).thenReturn(Optional.of(existingUser()));

            assertThatThrownBy(() -> service.create(createCommand()))
                    .isInstanceOf(UserAlreadyExistsException.class);
            verify(repository, never()).save(any());
        }

        @Test
        void uniqueConstraintRaceIsTranslatedToAlreadyExists() {
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(repository.findByFineractClientId(FINERACT_CLIENT_ID)).thenReturn(Optional.empty());
            when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> service.create(createCommand()))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }
    }

    @Nested
    class MarkOtpVerified {

        @Test
        void successTransitionsUserToBoundAndSaves() {
            User user = existingUser();
            when(repository.findById(USER_ID)).thenReturn(Optional.of(user));

            service.markOtpVerified(USER_ID);

            verify(repository).save(user);
            assertThat(user.getStatus()).isEqualTo(UserStatus.BOUND);
            assertThat(user.getBoundAt()).isNotNull();
        }

        @Test
        void unknownUserIsRejected() {
            when(repository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markOtpVerified(USER_ID))
                    .isInstanceOf(UserNotFoundException.class);
            verify(repository, never()).save(any());
        }
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .build();
    }

    private static InitiatePasswordChangeCommand initiateCommand() {
        return InitiatePasswordChangeCommand.builder()
                .currentPassword(CURRENT_PASSWORD)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private static ConfirmPasswordChangeCommand confirmCommand() {
        return ConfirmPasswordChangeCommand.builder()
                .stepUpToken(STEP_UP_TOKEN)
                .otp(OTP)
                .newPassword(NEW_PASSWORD)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private void stubActionFingerprint() {
        when(stepUpTokenService.actionFingerprint(
                UserCommandConstants.PASSWORD_CHANGE_ENDPOINT, PUBLIC_ID.toString()))
                .thenReturn(ACTION_FINGERPRINT);
    }

    @Nested
    class InitiatePasswordChange {

        @Test
        void successSendsOtpIssuesStepUpTokenAndMasksEmail() {
            Instant expiresAt = Instant.now().plusSeconds(300);
            when(repository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(existingUser()));
            when(passwordEncoder.matches(CURRENT_PASSWORD, PASSWORD_HASH)).thenReturn(true);
            stubActionFingerprint();
            when(stepUpTokenService.issue(PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT,
                    StepUpConstants.STEPUP_TTL))
                    .thenReturn(IssuedJwt.builder().tokenValue(STEP_UP_TOKEN).expiresAt(expiresAt).build());

            UserPasswordChangeChallengeCommandData challenge =
                    service.initiatePasswordChange(jwt(), initiateCommand());

            assertThat(challenge.getStepUpToken()).isEqualTo(STEP_UP_TOKEN);
            assertThat(challenge.getExpiresAt()).isEqualTo(expiresAt);
            assertThat(challenge.getSentTo()).isEqualTo(MASKED_EMAIL);

            ArgumentCaptor<OtpDestination> destination = ArgumentCaptor.forClass(OtpDestination.class);
            verify(otpCommandService).createOtp(eq(PUBLIC_ID), destination.capture());
            assertThat(destination.getValue().getDeliveryMethod())
                    .isEqualTo(OtpConstants.EMAIL_DELIVERY_METHOD_NAME);
            assertThat(destination.getValue().getTarget()).isEqualTo(EMAIL);
        }

        @Test
        void wrongCurrentPasswordIsRejectedWithoutSendingOtp() {
            when(repository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(existingUser()));
            when(passwordEncoder.matches(CURRENT_PASSWORD, PASSWORD_HASH)).thenReturn(false);

            assertThatThrownBy(() -> service.initiatePasswordChange(jwt(), initiateCommand()))
                    .isInstanceOf(UserPasswordInvalidException.class)
                    .hasFieldOrPropertyWithValue("code", UserPasswordInvalidException.CODE);

            verify(otpCommandService, never()).createOtp(any(), any());
        }

        @Test
        void deniedByPolicyPropagatesAndSendsNoOtp() {
            doThrow(new AccessScopeInsufficientException())
                    .when(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.USER_PASSWORD_CHANGE));

            assertThatThrownBy(() -> service.initiatePasswordChange(jwt(), initiateCommand()))
                    .isInstanceOf(AccessScopeInsufficientException.class);

            verify(otpCommandService, never()).createOtp(any(), any());
        }
    }

    @Nested
    class ConfirmPasswordChange {

        @Test
        void successStoresNewHashRevokesAllSessionsAndReissuesSession() {
            User user = existingUser();
            ReflectionTestUtils.setField(user, "id", USER_ID);
            stubActionFingerprint();
            when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT))
                    .thenReturn(true);
            when(repository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_PASSWORD_HASH);
            EstablishedSessionCommandData reissued = EstablishedSessionCommandData.builder()
                    .accessToken("new-access-token")
                    .accessTokenExpiresAt(Instant.now().plusSeconds(900))
                    .refreshToken("new-refresh-token")
                    .refreshTokenExpiresAt(Instant.now().plusSeconds(86400))
                    .build();
            when(authenticationCommandService.revokeAllSessionsAndReissue(USER_ID, PUBLIC_ID, DEVICE_FINGERPRINT))
                    .thenReturn(reissued);

            EstablishedSessionCommandData session = service.confirmPasswordChange(jwt(), confirmCommand());

            verify(otpCommandService).validateOtp(PUBLIC_ID, OTP);
            verify(repository).save(user);
            assertThat(user.getPasswordHash()).isEqualTo(NEW_PASSWORD_HASH);
            verify(authenticationCommandService).revokeAllSessionsAndReissue(USER_ID, PUBLIC_ID, DEVICE_FINGERPRINT);
            assertThat(session).isSameAs(reissued);
        }

        @Test
        void invalidStepUpIsRejectedWithoutSaving() {
            stubActionFingerprint();
            when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT))
                    .thenReturn(false);

            assertThatThrownBy(() -> service.confirmPasswordChange(jwt(), confirmCommand()))
                    .isInstanceOf(UserStepUpInvalidException.class)
                    .hasFieldOrPropertyWithValue("code", UserStepUpInvalidException.CODE);

            verify(repository, never()).save(any());
        }

        @Test
        void invalidOtpIsRejectedWithoutChangingPasswordOrRevokingSessions() {
            stubActionFingerprint();
            when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT))
                    .thenReturn(true);
            doThrow(new OtpTokenInvalidException()).when(otpCommandService).validateOtp(PUBLIC_ID, OTP);

            assertThatThrownBy(() -> service.confirmPasswordChange(jwt(), confirmCommand()))
                    .isInstanceOf(UserStepUpInvalidException.class);

            verify(repository, never()).save(any());
            verify(authenticationCommandService, never()).revokeAllSessionsAndReissue(any(), any(), any());
        }

        @Test
        void deniedByPolicyPropagatesAndPersistsNothing() {
            doThrow(new AccessScopeInsufficientException())
                    .when(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.USER_PASSWORD_CHANGE));

            assertThatThrownBy(() -> service.confirmPasswordChange(jwt(), confirmCommand()))
                    .isInstanceOf(AccessScopeInsufficientException.class);

            verify(repository, never()).save(any());
            verify(authenticationCommandService, never()).revokeAllSessionsAndReissue(any(), any(), any());
        }
    }

    @Nested
    class ForgotPassword {

        @Test
        void knownEmailSendsOtpToTheUsersEmail() {
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));
            doAnswer(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            }).when(taskExecutor).execute(any());

            service.forgotPassword(ForgotPasswordCommand.builder().email(EMAIL).build());

            ArgumentCaptor<OtpDestination> destination = ArgumentCaptor.forClass(OtpDestination.class);
            verify(otpCommandService).createOtp(eq(PUBLIC_ID), destination.capture());
            assertThat(destination.getValue().getDeliveryMethod())
                    .isEqualTo(OtpConstants.EMAIL_DELIVERY_METHOD_NAME);
            assertThat(destination.getValue().getTarget()).isEqualTo(EMAIL);
        }

        @Test
        void unknownEmailSendsNothingAndReturnsNormally() {
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            service.forgotPassword(ForgotPasswordCommand.builder().email(EMAIL).build());

            verify(taskExecutor, never()).execute(any());
            verify(otpCommandService, never()).createOtp(any(), any());
        }
    }

    private static ResetPasswordCommand resetCommand() {
        return ResetPasswordCommand.builder()
                .email(EMAIL)
                .otp(OTP)
                .newPassword(NEW_PASSWORD)
                .build();
    }

    @Nested
    class ResetPassword {

        @Test
        void successStoresNewHashAndRevokesAllSessions() {
            User user = existingUser();
            ReflectionTestUtils.setField(user, "id", USER_ID);
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn(NEW_PASSWORD_HASH);

            service.resetPassword(resetCommand());

            verify(otpCommandService).validateOtp(PUBLIC_ID, OTP);
            verify(repository).save(user);
            assertThat(user.getPasswordHash()).isEqualTo(NEW_PASSWORD_HASH);
            verify(authenticationCommandService).revokeAllSessions(USER_ID, PUBLIC_ID);
        }

        @Test
        void unknownEmailIsRejectedWithTheGenericError() {
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resetPassword(resetCommand()))
                    .isInstanceOf(UserPasswordResetInvalidException.class)
                    .hasFieldOrPropertyWithValue("code", UserPasswordResetInvalidException.CODE);

            verify(otpCommandService, never()).validateOtp(any(), any());
            verify(repository, never()).save(any());
            verify(authenticationCommandService, never()).revokeAllSessions(any(), any());
        }

        @Test
        void invalidOtpIsRejectedWithTheGenericErrorAndPasswordUnchanged() {
            User user = existingUser();
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
            doThrow(new OtpTokenInvalidException()).when(otpCommandService).validateOtp(PUBLIC_ID, OTP);

            assertThatThrownBy(() -> service.resetPassword(resetCommand()))
                    .isInstanceOf(UserPasswordResetInvalidException.class)
                    .hasFieldOrPropertyWithValue("code", UserPasswordResetInvalidException.CODE);

            assertThat(user.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            verify(repository, never()).save(any());
            verify(authenticationCommandService, never()).revokeAllSessions(any(), any());
        }
    }
}
