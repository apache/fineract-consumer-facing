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

package org.apache.fineract.consumer.savings.command.service;

import feign.FeignException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.command.Command;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdResponse;
import org.apache.fineract.consumer.infrastructure.jwt.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpConstants;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpTokenService;
import org.apache.fineract.consumer.infrastructure.web.AccessPolicyEvaluator;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.service.OtpCommandService;
import org.apache.fineract.consumer.savings.command.data.ConfirmSavingsChargePaymentCommand;
import org.apache.fineract.consumer.savings.command.data.InitiateSavingsChargePaymentCommand;
import org.apache.fineract.consumer.savings.command.data.SavingsChargePaymentChallengeCommandData;
import org.apache.fineract.consumer.savings.command.data.SavingsChargePaymentCommandData;
import org.apache.fineract.consumer.savings.command.data.SavingsChargePaymentConstants;
import org.apache.fineract.consumer.savings.command.exception.SavingsAccountAccessDeniedException;
import org.apache.fineract.consumer.savings.command.exception.SavingsChargePaymentInvalidException;
import org.apache.fineract.consumer.savings.command.exception.SavingsChargePaymentNotFoundException;
import org.apache.fineract.consumer.savings.command.exception.SavingsChargePaymentStepUpInvalidException;
import org.apache.fineract.consumer.savings.command.exception.SavingsChargePaymentUpstreamUnavailableException;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsCommandServiceImpl implements SavingsCommandService {

    private final UserQueryService userQueryService;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final OtpCommandService otpCommandService;
    private final StepUpTokenService stepUpTokenService;
    private final SavingsChargesApi savingsChargesApi;

    @Override
    @Command
    public SavingsChargePaymentChallengeCommandData initiateChargePayment(
            Jwt jwt, InitiateSavingsChargePaymentCommand command) {
        UUID publicId = publicId(jwt);
        UserQueryData user = userQueryService.findByPublicId(publicId);

        requireAccess(user.getFineractClientId(), command.getSavingsId());

        otpCommandService.createOtp(publicId, OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(user.getEmail())
                .build());

        String actionFingerprint = stepUpTokenService.actionFingerprint(
                SavingsChargePaymentConstants.ENDPOINT,
                command.getSavingsId(),
                command.getChargeId(),
                command.getAmount());
        IssuedJwt issued = stepUpTokenService.issue(
                publicId, command.getDeviceFingerprint(), actionFingerprint, StepUpConstants.STEPUP_TTL);

        return SavingsChargePaymentChallengeCommandData.builder()
                .stepUpToken(issued.getTokenValue())
                .expiresAt(issued.getExpiresAt())
                .sentTo(maskEmail(user.getEmail()))
                .build();
    }

    @Override
    @Command
    public SavingsChargePaymentCommandData confirmChargePayment(
            Jwt jwt, ConfirmSavingsChargePaymentCommand command) {
        UUID publicId = publicId(jwt);
        String actionFingerprint = stepUpTokenService.actionFingerprint(
                SavingsChargePaymentConstants.ENDPOINT,
                command.getSavingsId(),
                command.getChargeId(),
                command.getAmount());
        if (!stepUpTokenService.verify(
                command.getStepUpToken(), publicId, command.getDeviceFingerprint(), actionFingerprint)) {
            throw new SavingsChargePaymentStepUpInvalidException();
        }

        UserQueryData user = userQueryService.findByPublicId(publicId);
        try {
            otpCommandService.validateOtp(publicId, command.getOtp());
        } catch (OtpTokenInvalidException e) {
            throw new SavingsChargePaymentStepUpInvalidException();
        }

        requireAccess(user.getFineractClientId(), command.getSavingsId());

        PostSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdRequest request =
                new PostSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdRequest()
                        .locale(SavingsChargePaymentConstants.LOCALE)
                        .dateFormat(SavingsChargePaymentConstants.DATE_FORMAT)
                        .dueDate(LocalDate.now().toString())
                        .amount(command.getAmount().floatValue());

        PostSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdResponse response =
                call(() -> savingsChargesApi.payOrWaiveSavingsAccountCharge(
                        command.getSavingsId(),
                        command.getChargeId(),
                        request,
                        SavingsChargePaymentConstants.PAYCHARGE_COMMAND));

        return SavingsChargePaymentCommandData.builder()
                .savingsId(command.getSavingsId())
                .chargeId(command.getChargeId())
                .amount(command.getAmount())
                .resourceId(response.getResourceId())
                .build();
    }

    private UUID publicId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private void requireAccess(Long callerClientId, Long savingsId) {
        if (!accessPolicyEvaluator.canAccessSavings(callerClientId, savingsId)) {
            throw new SavingsAccountAccessDeniedException();
        }
    }

    private <T> T call(Supplier<T> upstream) {
        try {
            return upstream.get();
        } catch (FeignException.NotFound e) {
            throw new SavingsChargePaymentNotFoundException();
        } catch (FeignException.BadRequest e) {
            throw new SavingsChargePaymentInvalidException(e);
        } catch (FeignException e) {
            throw new SavingsChargePaymentUpstreamUnavailableException(e);
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
