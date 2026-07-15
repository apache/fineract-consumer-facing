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

package org.apache.fineract.consumer.registration.command.service;

import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.command.Command;
import org.apache.fineract.consumer.infrastructure.web.EmailMasking;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.data.PendingOtp;
import org.apache.fineract.consumer.otp.command.service.OtpCommandService;
import org.apache.fineract.consumer.registration.command.data.SendOtpCommand;
import org.apache.fineract.consumer.registration.command.data.SendOtpCommandData;
import org.apache.fineract.consumer.registration.command.data.SubmitRegistrationCommand;
import org.apache.fineract.consumer.registration.command.data.SubmitRegistrationCommandData;
import org.apache.fineract.consumer.registration.command.data.VerifyOtpCommand;
import org.apache.fineract.consumer.registration.command.data.VerifyOtpCommandData;
import org.apache.fineract.consumer.registration.command.exception.IdentityNotVerifiedException;
import org.apache.fineract.consumer.registration.query.data.IdentityVerificationQueryData;
import org.apache.fineract.consumer.registration.query.data.IdentityVerificationQuery;
import org.apache.fineract.consumer.registration.query.service.IdentityQueryService;
import org.apache.fineract.consumer.user.command.data.CreateUserCommand;
import org.apache.fineract.consumer.user.command.data.UserCreatedCommandData;
import org.apache.fineract.consumer.user.command.domain.UserStatus;
import org.apache.fineract.consumer.user.command.service.UserCommandService;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationCommandServiceImpl implements RegistrationCommandService {

    private final IdentityQueryService identityQueryService;
    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;
    private final OtpCommandService otpCommandService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Command
    @Transactional
    public SubmitRegistrationCommandData submit(SubmitRegistrationCommand command) {
        IdentityVerificationQuery verification = IdentityVerificationQuery.builder()
                .fineractClientId(command.getFineractClientId())
                .documentTypeName(command.getDocumentTypeName())
                .documentKey(command.getDocumentKey())
                .build();

        IdentityVerificationQueryData verificationResult = identityQueryService.verifyIdentity(verification);
        if (!verificationResult.isVerified()) {
            throw new IdentityNotVerifiedException();
        }

        CreateUserCommand createUser = CreateUserCommand.builder()
                .email(command.getEmail())
                .passwordHash(passwordEncoder.encode(command.getPassword()))
                .fineractClientId(command.getFineractClientId())
                .build();
        UserCreatedCommandData createdUser = userCommandService.create(createUser);

        return SubmitRegistrationCommandData.builder()
                .registrationId(createdUser.getPublicId())
                .status(UserStatus.PENDING_OTP)
                .maskedLastFour(verificationResult.getMaskedLastFour())
                .build();
    }

    @Override
    @Command
    public SendOtpCommandData sendOtp(SendOtpCommand command) {
        UserQueryData user = userQueryService.findByPublicId(command.getRegistrationId());
        OtpDestination destination = OtpDestination.builder()
                .deliveryMethod(command.getDeliveryMethod())
                .target(user.getEmail())
                .build();
        PendingOtp request = otpCommandService.createOtp(user.getPublicId(), destination);
        ZonedDateTime expiresAt = request.getMetadata().getRequestTime()
                .plusSeconds(request.getMetadata().getTokenLiveTimeInSec());
        return SendOtpCommandData.builder()
                .sentTo(EmailMasking.mask(user.getEmail()))
                .expiresAt(expiresAt)
                .tokenLiveTimeInSec(request.getMetadata().getTokenLiveTimeInSec())
                .build();
    }

    @Override
    @Command
    @Transactional
    public VerifyOtpCommandData verifyOtp(VerifyOtpCommand command) {
        UserQueryData user = userQueryService.findByPublicId(command.getRegistrationId());
        otpCommandService.validateOtp(user.getPublicId(), command.getToken());
        userCommandService.markOtpVerified(user.getId());
        return VerifyOtpCommandData.builder()
                .status(UserStatus.BOUND)
                .build();
    }

}
