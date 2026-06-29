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

package org.apache.fineract.consumer.transfers.command.service;

import feign.FeignException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.command.Command;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.AccountTransfersApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.AccountTransferRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostAccountTransfersResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.jwt.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpConstants;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpTokenService;
import org.apache.fineract.consumer.infrastructure.web.AccessPolicyEvaluator;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.service.OtpCommandService;
import org.apache.fineract.consumer.transfers.command.data.ConfirmTransferCommand;
import org.apache.fineract.consumer.transfers.command.data.InitiateTransferCommand;
import org.apache.fineract.consumer.transfers.command.data.TransferChallengeCommandData;
import org.apache.fineract.consumer.transfers.command.data.TransferCommandData;
import org.apache.fineract.consumer.transfers.command.data.TransferConstants;
import org.apache.fineract.consumer.transfers.command.exception.TransferAccessDeniedException;
import org.apache.fineract.consumer.transfers.command.exception.TransferInvalidException;
import org.apache.fineract.consumer.transfers.command.exception.TransferNotFoundException;
import org.apache.fineract.consumer.transfers.command.exception.TransferStepUpInvalidException;
import org.apache.fineract.consumer.transfers.command.exception.TransferUpstreamUnavailableException;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransfersCommandServiceImpl implements TransfersCommandService {

    private final UserQueryService userQueryService;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final OtpCommandService otpCommandService;
    private final StepUpTokenService stepUpTokenService;
    private final ClientApi clientApi;
    private final SavingsAccountApi savingsAccountApi;
    private final AccountTransfersApi accountTransfersApi;

    @Override
    @Command
    public TransferChallengeCommandData initiate(Jwt jwt, InitiateTransferCommand command) {
        boolean toLoan = resolveToLoan(command.getToAccountType());

        UUID publicId = publicId(jwt);
        UserQueryData user = userQueryService.findByPublicId(publicId);

        requireAccess(user.getFineractClientId(), command.getFromAccountId(), command.getToAccountId(), toLoan);

        otpCommandService.createOtp(publicId, OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(user.getEmail())
                .build());

        String actionFingerprint = stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT,
                command.getFromAccountId(),
                command.getToAccountId(),
                toLoan ? TransferConstants.LOAN_TYPE_CODE : TransferConstants.SAVINGS_TYPE_CODE,
                command.getAmount());
        IssuedJwt issued = stepUpTokenService.issue(
                publicId, command.getDeviceFingerprint(), actionFingerprint, StepUpConstants.STEPUP_TTL);

        return TransferChallengeCommandData.builder()
                .stepUpToken(issued.getTokenValue())
                .expiresAt(issued.getExpiresAt())
                .sentTo(maskEmail(user.getEmail()))
                .build();
    }

    @Override
    @Command
    public TransferCommandData confirm(Jwt jwt, ConfirmTransferCommand command) {
        boolean toLoan = resolveToLoan(command.getToAccountType());

        UUID publicId = publicId(jwt);
        String actionFingerprint = stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT,
                command.getFromAccountId(),
                command.getToAccountId(),
                toLoan ? TransferConstants.LOAN_TYPE_CODE : TransferConstants.SAVINGS_TYPE_CODE,
                command.getAmount());
        if (!stepUpTokenService.verify(
                command.getStepUpToken(), publicId, command.getDeviceFingerprint(), actionFingerprint)) {
            throw new TransferStepUpInvalidException();
        }

        UserQueryData user = userQueryService.findByPublicId(publicId);
        try {
            otpCommandService.validateOtp(publicId, command.getOtp());
        } catch (OtpTokenInvalidException e) {
            throw new TransferStepUpInvalidException();
        }

        Long callerClientId = user.getFineractClientId();
        requireAccess(callerClientId, command.getFromAccountId(), command.getToAccountId(), toLoan);

        Long callerOfficeId = call(() -> clientApi.retrieveOneClient(callerClientId, false)).getOfficeId();

        Long toClientId;
        Long toOfficeId;
        if (toLoan) {
            toClientId = callerClientId;
            toOfficeId = callerOfficeId;
        } else {
            SavingsAccountData destination =
                    call(() -> savingsAccountApi.retrieveSavingsAccount(command.getToAccountId(), null, null, null));
            Long destinationClientId = destination.getClientId();
            toClientId = destinationClientId;
            toOfficeId = call(() -> clientApi.retrieveOneClient(destinationClientId, false)).getOfficeId();
        }

        AccountTransferRequest request = new AccountTransferRequest()
                .fromOfficeId(String.valueOf(callerOfficeId))
                .fromClientId(String.valueOf(callerClientId))
                .fromAccountId(String.valueOf(command.getFromAccountId()))
                .fromAccountType(TransferConstants.SAVINGS_TYPE_CODE)
                .toOfficeId(String.valueOf(toOfficeId))
                .toClientId(String.valueOf(toClientId))
                .toAccountId(String.valueOf(command.getToAccountId()))
                .toAccountType(toLoan ? TransferConstants.LOAN_TYPE_CODE : TransferConstants.SAVINGS_TYPE_CODE)
                .transferAmount(command.getAmount().toPlainString())
                .transferDate(LocalDate.now().toString())
                .dateFormat(TransferConstants.DATE_FORMAT)
                .locale(TransferConstants.LOCALE)
                .transferDescription(TransferConstants.DEFAULT_DESCRIPTION);

        PostAccountTransfersResponse response = call(() -> accountTransfersApi.createAccountTransfer(request));

        return TransferCommandData.builder()
                .transferId(response.getResourceId())
                .fromAccountId(command.getFromAccountId())
                .toAccountId(command.getToAccountId())
                .amount(command.getAmount())
                .build();
    }

    private UUID publicId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean resolveToLoan(String toAccountType) {
        if (TransferConstants.LOAN_TYPE_NAME.equalsIgnoreCase(toAccountType)) {
            return true;
        }
        if (TransferConstants.SAVINGS_TYPE_NAME.equalsIgnoreCase(toAccountType)) {
            return false;
        }
        throw new TransferInvalidException();
    }

    private void requireAccess(Long callerClientId, Long fromAccountId, Long toAccountId, boolean toLoan) {
        boolean allowed = accessPolicyEvaluator.canAccessSavings(callerClientId, fromAccountId)
                && (!toLoan || accessPolicyEvaluator.canAccessLoans(callerClientId, toAccountId));
        if (!allowed) {
            throw new TransferAccessDeniedException();
        }
    }

    private <T> T call(Supplier<T> upstream) {
        try {
            return upstream.get();
        } catch (FeignException.NotFound e) {
            throw new TransferNotFoundException();
        } catch (FeignException.BadRequest e) {
            throw new TransferInvalidException(e);
        } catch (FeignException e) {
            throw new TransferUpstreamUnavailableException(e);
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
