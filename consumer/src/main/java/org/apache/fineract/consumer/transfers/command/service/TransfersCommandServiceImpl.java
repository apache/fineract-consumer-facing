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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.beneficiaries.query.data.BeneficiaryQueryData;
import org.apache.fineract.consumer.beneficiaries.query.service.BeneficiariesQueryService;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.data.ResourceType;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.FineractCaller;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.TransferFineractClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.AccountTransferRequest;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyDeriver;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyHolder;
import org.apache.fineract.consumer.infrastructure.jwt.data.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.data.StepUpConstants;
import org.apache.fineract.consumer.infrastructure.stepup.service.StepUpTokenService;
import org.apache.fineract.consumer.infrastructure.web.service.EmailMaskingService;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.fineractclient.data.LoanPayoffData;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpConstants;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpDestination;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpPurpose;
import org.apache.fineract.consumer.infrastructure.otp.service.OtpService;
import org.apache.fineract.consumer.transfers.command.data.ConfirmTransferCommand;
import org.apache.fineract.consumer.transfers.command.data.InitiateTransferCommand;
import org.apache.fineract.consumer.transfers.command.data.TransferChallengeCommandData;
import org.apache.fineract.consumer.transfers.command.data.TransferCommandData;
import org.apache.fineract.consumer.transfers.command.data.TransferConstants;
import org.apache.fineract.consumer.transfers.command.exception.TransferAccessDeniedException;
import org.apache.fineract.consumer.transfers.command.exception.TransferBeneficiaryLimitExceededException;
import org.apache.fineract.consumer.transfers.command.exception.TransferInProgressException;
import org.apache.fineract.consumer.transfers.command.exception.TransferInvalidException;
import org.apache.fineract.consumer.transfers.command.exception.TransferLoanNotActiveException;
import org.apache.fineract.consumer.transfers.command.exception.TransferLoanOverpaymentException;
import org.apache.fineract.consumer.transfers.command.exception.TransferNotFoundException;
import org.apache.fineract.consumer.transfers.command.exception.TransferSameAccountException;
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
    private final BeneficiariesQueryService beneficiariesQueryService;
    private final OtpService otpService;
    private final StepUpTokenService stepUpTokenService;
    private final TransferFineractClient transferFineractClient;
    private final IdempotencyKeyHolder idempotencyKeyHolder;

    @Override
    public TransferChallengeCommandData initiate(Jwt jwt, InitiateTransferCommand command) {
        boolean toLoan = resolveToLoan(command.getToAccountType());
        requireDistinctAccounts(command.getFromAccountId(), command.getToAccountId(), toLoan);

        UUID publicId = publicId(jwt);
        UserQueryData user = userQueryService.findByPublicId(publicId);

        accessPolicyEvaluator.authorize(jwt, ConsumerAction.TRANSFER_EXECUTE, command.getFromAccountId(),
                TransferAccessDeniedException::new);
        requireDestinationAllowed(jwt, user.getId(), command.getToAccountId(), toLoan, command.getAmount());

        otpService.createOtp(publicId, OtpPurpose.TRANSFER, OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(user.getEmail())
                .build());

        String actionFingerprint = transferActionFingerprint(
                command.getFromAccountId(), command.getToAccountId(), toLoan, command.getAmount());
        IssuedJwt issued = stepUpTokenService.issue(
                publicId, command.getDeviceFingerprint(), actionFingerprint, StepUpConstants.STEPUP_TTL);

        return TransferChallengeCommandData.builder()
                .stepUpToken(issued.getTokenValue())
                .expiresAt(issued.getExpiresAt())
                .sentTo(EmailMaskingService.mask(user.getEmail()))
                .build();
    }

    @Override
    public TransferCommandData confirm(Jwt jwt, ConfirmTransferCommand command) {
        boolean toLoan = resolveToLoan(command.getToAccountType());
        requireDistinctAccounts(command.getFromAccountId(), command.getToAccountId(), toLoan);

        UUID publicId = publicId(jwt);
        String actionFingerprint = transferActionFingerprint(
                command.getFromAccountId(), command.getToAccountId(), toLoan, command.getAmount());
        if (!stepUpTokenService.verify(
                command.getStepUpToken(), publicId, command.getDeviceFingerprint(), actionFingerprint)) {
            throw new TransferStepUpInvalidException();
        }

        UserQueryData user = userQueryService.findByPublicId(publicId);
        otpService.validateOtp(publicId, OtpPurpose.TRANSFER, command.getOtp(), TransferStepUpInvalidException::new);

        accessPolicyEvaluator.authorize(jwt, ConsumerAction.TRANSFER_EXECUTE, command.getFromAccountId(),
                TransferAccessDeniedException::new);
        requireDestinationAllowed(jwt, user.getId(), command.getToAccountId(), toLoan, command.getAmount());

        Long callerClientId = user.getFineractClientId();

        idempotencyKeyHolder.set(IdempotencyKeyDeriver.derive(publicId, command.getIdempotencyKey()));
        try {
            Long callerOfficeId = call(() -> transferFineractClient.getClientOfficeId(callerClientId));

            Long toClientId;
            Long toOfficeId;
            if (toLoan) {
                toClientId = callerClientId;
                toOfficeId = callerOfficeId;
            } else {
                Long destinationClientId =
                        call(() -> transferFineractClient.getSavingsAccountClientId(command.getToAccountId()));
                toClientId = destinationClientId;
                toOfficeId = call(() -> transferFineractClient.getClientOfficeId(destinationClientId));
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

            Long transferId = callWrite(() -> transferFineractClient.createAccountTransfer(request));

            return TransferCommandData.builder()
                    .transferId(transferId)
                    .fromAccountId(command.getFromAccountId())
                    .toAccountId(command.getToAccountId())
                    .amount(command.getAmount())
                    .build();
        } finally {
            idempotencyKeyHolder.clear();
        }
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

    private String transferActionFingerprint(Long fromAccountId, Long toAccountId, boolean toLoan, BigDecimal amount) {
        return stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT,
                fromAccountId,
                toAccountId,
                toLoan ? TransferConstants.LOAN_TYPE_CODE : TransferConstants.SAVINGS_TYPE_CODE,
                amount);
    }

    private static void requireDistinctAccounts(Long fromAccountId, Long toAccountId, boolean toLoan) {
        if (!toLoan && fromAccountId.equals(toAccountId)) {
            throw new TransferSameAccountException();
        }
    }
    }

    private void requireDestinationAllowed(Jwt jwt, Long userId, Long toAccountId, boolean toLoan, BigDecimal amount) {
        if (toLoan) {
            if (!accessPolicyEvaluator.ownsResource(jwt, ResourceType.LOANS, toAccountId)) {
                throw new TransferAccessDeniedException();
            }
            requireWithinOutstanding(toAccountId, amount);
            return;
        }
        Optional<BeneficiaryQueryData> beneficiary = beneficiariesQueryService.findActiveByAccount(userId, toAccountId);
        if (beneficiary.isPresent()) {
            BigDecimal limit = beneficiary.get().getTransferLimit();
            if (limit != null && amount.compareTo(limit) > 0) {
                throw new TransferBeneficiaryLimitExceededException();
            }
            return;
        }
        if (!accessPolicyEvaluator.ownsResource(jwt, ResourceType.SAVINGS, toAccountId)) {
            throw new TransferAccessDeniedException();
        }
    }

    private void requireWithinOutstanding(Long loanId, BigDecimal amount) {
        LoanPayoffData payoff = call(() -> transferFineractClient.getLoanPayoff(loanId));
        if (payoff == null || !payoff.isActive()) {
            throw new TransferLoanNotActiveException();
        }
        BigDecimal outstanding = payoff.getPayoffAmount();
        if (outstanding == null || outstanding.signum() <= 0) {
            throw new TransferLoanNotActiveException();
        }
        if (amount.compareTo(outstanding) > 0) {
            throw new TransferLoanOverpaymentException();
        }
    }

    private <T> T call(Supplier<T> upstream) {
        return FineractCaller.call(upstream,
                e -> new TransferNotFoundException(),
                TransferInvalidException::new,
                TransferUpstreamUnavailableException::new);
    }

    private <T> T callWrite(Supplier<T> upstream) {
        return FineractCaller.call(upstream,
                e -> new TransferNotFoundException(),
                TransferInvalidException::new,
                TransferInProgressException::new,
                TransferUpstreamUnavailableException::new);
    }

}
