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

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.consumer.authentication.command.data.EstablishedSessionCommandData;
import org.apache.fineract.consumer.authentication.command.service.AuthenticationCommandService;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.command.Command;
import org.apache.fineract.consumer.infrastructure.jwt.data.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpConstants;
import org.apache.fineract.consumer.infrastructure.web.EmailMasking;
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
import org.apache.fineract.consumer.user.command.exception.UserAlreadyExistsException;
import org.apache.fineract.consumer.user.command.exception.UserNotFoundException;
import org.apache.fineract.consumer.user.command.exception.UserPasswordInvalidException;
import org.apache.fineract.consumer.user.command.exception.UserPasswordResetInvalidException;
import org.apache.fineract.consumer.user.command.exception.UserStepUpInvalidException;
import org.apache.fineract.consumer.user.command.repository.UserCommandRepository;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserCommandServiceImpl implements UserCommandService {

    private final UserCommandRepository repository;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final PasswordEncoder passwordEncoder;
    private final OtpCommandService otpCommandService;
    private final StepUpTokenService stepUpTokenService;
    private final AuthenticationCommandService authenticationCommandService;
    private final AsyncTaskExecutor taskExecutor;

    @Override
    @Command
    public UserCreatedCommandData create(CreateUserCommand command) {
        repository.findByEmail(command.getEmail()).ifPresent(u -> {
            throw new UserAlreadyExistsException();
        });
        repository.findByFineractClientId(command.getFineractClientId()).ifPresent(u -> {
            throw new UserAlreadyExistsException();
        });
        User user = User.createPendingOtp(
                UUID.randomUUID(),
                command.getEmail(),
                command.getPasswordHash(),
                command.getFineractClientId());
        User saved;
        try {
            saved = repository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExistsException();
        }
        return UserCreatedCommandData.builder()
                .userId(saved.getId())
                .publicId(saved.getPublicId())
                .build();
    }

    @Override
    @Command
    public void markOtpVerified(Long userId) {
        User user = repository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.markOtpVerified();
        repository.save(user);
    }

    @Override
    @Command
    public UserPasswordChangeChallengeCommandData initiatePasswordChange(
            Jwt jwt, InitiatePasswordChangeCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.USER_PASSWORD_CHANGE);
        User user = repository.findByPublicId(publicId(jwt)).orElseThrow(UserNotFoundException::new);
        if (!passwordEncoder.matches(command.getCurrentPassword(), user.getPasswordHash())) {
            log.warn("password change initiate rejected: current password mismatch for user {}", user.getPublicId());
            throw new UserPasswordInvalidException();
        }

        sendOtp(user);
        IssuedJwt issued = stepUpTokenService.issue(user.getPublicId(), command.getDeviceFingerprint(),
                passwordChangeActionFingerprint(user.getPublicId()), StepUpConstants.STEPUP_TTL);

        return UserPasswordChangeChallengeCommandData.builder()
                .stepUpToken(issued.getTokenValue())
                .expiresAt(issued.getExpiresAt())
                .sentTo(EmailMasking.mask(user.getEmail()))
                .build();
    }

    @Override
    @Command
    @Transactional
    public EstablishedSessionCommandData confirmPasswordChange(Jwt jwt, ConfirmPasswordChangeCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.USER_PASSWORD_CHANGE);
        UUID publicId = publicId(jwt);
        if (!stepUpTokenService.verify(command.getStepUpToken(), publicId, command.getDeviceFingerprint(),
                passwordChangeActionFingerprint(publicId))) {
            throw new UserStepUpInvalidException();
        }
        try {
            otpCommandService.validateOtp(publicId, command.getOtp());
        } catch (OtpTokenInvalidException e) {
            throw new UserStepUpInvalidException();
        }

        User user = repository.findByPublicId(publicId).orElseThrow(UserNotFoundException::new);
        user.changePassword(passwordEncoder.encode(command.getNewPassword()));
        repository.save(user);
        return authenticationCommandService.revokeAllSessionsAndReissue(
                user.getId(), user.getPublicId(), command.getDeviceFingerprint());
    }

    @Override
    @Command
    public void forgotPassword(ForgotPasswordCommand command) {
        log.info("password.forgot.requested");
        repository.findByEmail(command.getEmail()).ifPresent(user -> taskExecutor.execute(() -> {
            try {
                sendOtp(user);
            } catch (RuntimeException e) {
                log.error("password.forgot otp delivery failed for user {}", user.getPublicId(), e);
            }
        }));
    }

    @Override
    @Command
    @Transactional
    public void resetPassword(ResetPasswordCommand command) {
        User user = repository.findByEmail(command.getEmail()).orElseThrow(() -> {
            log.warn("password.reset.failed: unknown email");
            return new UserPasswordResetInvalidException();
        });
        try {
            otpCommandService.validateOtp(user.getPublicId(), command.getOtp());
        } catch (OtpTokenInvalidException e) {
            log.warn("password.reset.failed: invalid otp for user {}", user.getPublicId());
            throw new UserPasswordResetInvalidException();
        }
        user.changePassword(passwordEncoder.encode(command.getNewPassword()));
        repository.save(user);
        authenticationCommandService.revokeAllSessions(user.getId(), user.getPublicId());
        log.info("password.reset.completed for user {}", user.getPublicId());
    }

    private void sendOtp(User user) {
        otpCommandService.createOtp(user.getPublicId(), OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(user.getEmail())
                .build());
    }

    private String passwordChangeActionFingerprint(UUID publicId) {
        return stepUpTokenService.actionFingerprint(
                UserCommandConstants.PASSWORD_CHANGE_ENDPOINT, publicId.toString());
    }

    private static UUID publicId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
