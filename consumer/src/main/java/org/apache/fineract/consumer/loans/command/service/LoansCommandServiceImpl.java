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

package org.apache.fineract.consumer.loans.command.service;

import feign.FeignException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.command.Command;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansLoanIdChargesChargeIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansLoanIdChargesChargeIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansLoanIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutLoansLoanIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.jwt.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpConstants;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpTokenService;
import org.apache.fineract.consumer.infrastructure.web.AccessPolicyEvaluator;
import org.apache.fineract.consumer.loans.command.data.ConfirmLoanChargePaymentCommand;
import org.apache.fineract.consumer.loans.command.data.InitiateLoanChargePaymentCommand;
import org.apache.fineract.consumer.loans.command.data.LoanApplicationCommandData;
import org.apache.fineract.consumer.loans.command.data.LoanChargePaymentChallengeCommandData;
import org.apache.fineract.consumer.loans.command.data.LoanChargePaymentCommandData;
import org.apache.fineract.consumer.loans.command.data.LoanChargePaymentConstants;
import org.apache.fineract.consumer.loans.command.data.ModifyLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.SubmitLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.WithdrawLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.exception.LoanAccessDeniedException;
import org.apache.fineract.consumer.loans.command.exception.LoanApplicationInvalidException;
import org.apache.fineract.consumer.loans.command.exception.LoanChargePaymentInvalidException;
import org.apache.fineract.consumer.loans.command.exception.LoanChargePaymentNotFoundException;
import org.apache.fineract.consumer.loans.command.exception.LoanChargePaymentStepUpInvalidException;
import org.apache.fineract.consumer.loans.command.exception.LoanChargePaymentUpstreamUnavailableException;
import org.apache.fineract.consumer.loans.command.exception.LoanNotFoundException;
import org.apache.fineract.consumer.loans.command.exception.LoanUpstreamUnavailableException;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.service.OtpCommandService;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoansCommandServiceImpl implements LoansCommandService {

    private static final String LOCALE = "en";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String LOAN_TYPE = "individual";

    private final UserQueryService userQueryService;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final LoansApi loansApi;
    private final OtpCommandService otpCommandService;
    private final StepUpTokenService stepUpTokenService;
    private final LoanChargesApi loanChargesApi;

    @Override
    @Command
    public LoanApplicationCommandData submitApplication(Jwt jwt, SubmitLoanApplicationCommand command) {
        Long clientId = resolveClientId(jwt);
        PostLoansRequest request = buildSubmitRequest(command, clientId);
        PostLoansResponse response = call(() -> loansApi.calculateLoanScheduleOrSubmitLoanApplication(request, null));
        return LoanApplicationCommandData.builder()
                .loanId(response.getLoanId())
                .resourceId(response.getResourceId())
                .clientId(clientId)
                .build();
    }

    @Override
    @Command
    public LoanApplicationCommandData modifyApplication(Jwt jwt, ModifyLoanApplicationCommand command) {
        Long clientId = resolveClientId(jwt);
        requireAccess(clientId, command.getLoanId());
        PutLoansLoanIdRequest request = buildModifyRequest(command);
        PutLoansLoanIdResponse response = call(() -> loansApi.modifyLoanApplication(command.getLoanId(), request, null));
        return LoanApplicationCommandData.builder()
                .loanId(command.getLoanId())
                .resourceId(response.getResourceId())
                .clientId(clientId)
                .build();
    }

    @Override
    @Command
    public LoanApplicationCommandData withdrawApplication(Jwt jwt, WithdrawLoanApplicationCommand command) {
        Long clientId = resolveClientId(jwt);
        requireAccess(clientId, command.getLoanId());
        PostLoansLoanIdRequest request = new PostLoansLoanIdRequest()
                .withdrawnOnDate(command.getWithdrawnOnDate().toString())
                .dateFormat(DATE_FORMAT)
                .locale(LOCALE);
        PostLoansLoanIdResponse response =
                call(() -> loansApi.stateTransitions(command.getLoanId(), request, WITHDRAW_COMMAND));
        return LoanApplicationCommandData.builder()
                .loanId(command.getLoanId())
                .resourceId(response.getResourceId())
                .clientId(clientId)
                .build();
    }

    @Override
    @Command
    public LoanChargePaymentChallengeCommandData initiateChargePayment(
            Jwt jwt, InitiateLoanChargePaymentCommand command) {
        UUID publicId = UUID.fromString(jwt.getSubject());
        UserQueryData user = userQueryService.findByPublicId(publicId);

        requireChargeAccess(user.getFineractClientId(), command.getLoanId());

        otpCommandService.createOtp(publicId, OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(user.getEmail())
                .build());

        String actionFingerprint = stepUpTokenService.actionFingerprint(
                LoanChargePaymentConstants.ENDPOINT,
                command.getLoanId(),
                command.getChargeId());
        IssuedJwt issued = stepUpTokenService.issue(
                publicId, command.getDeviceFingerprint(), actionFingerprint, StepUpConstants.STEPUP_TTL);

        return LoanChargePaymentChallengeCommandData.builder()
                .stepUpToken(issued.getTokenValue())
                .expiresAt(issued.getExpiresAt())
                .sentTo(maskEmail(user.getEmail()))
                .build();
    }

    @Override
    @Command
    public LoanChargePaymentCommandData confirmChargePayment(Jwt jwt, ConfirmLoanChargePaymentCommand command) {
        UUID publicId = UUID.fromString(jwt.getSubject());
        String actionFingerprint = stepUpTokenService.actionFingerprint(
                LoanChargePaymentConstants.ENDPOINT,
                command.getLoanId(),
                command.getChargeId());
        if (!stepUpTokenService.verify(
                command.getStepUpToken(), publicId, command.getDeviceFingerprint(), actionFingerprint)) {
            throw new LoanChargePaymentStepUpInvalidException();
        }

        UserQueryData user = userQueryService.findByPublicId(publicId);
        try {
            otpCommandService.validateOtp(publicId, command.getOtp());
        } catch (OtpTokenInvalidException e) {
            throw new LoanChargePaymentStepUpInvalidException();
        }

        requireChargeAccess(user.getFineractClientId(), command.getLoanId());

        PostLoansLoanIdChargesChargeIdRequest request = new PostLoansLoanIdChargesChargeIdRequest()
                .transactionDate(LocalDate.now().toString())
                .locale(LoanChargePaymentConstants.LOCALE)
                .dateFormat(LoanChargePaymentConstants.DATE_FORMAT);

        PostLoansLoanIdChargesChargeIdResponse response =
                callChargePayment(() -> loanChargesApi.executeLoanChargeOnExistingCharge(
                        command.getLoanId(),
                        command.getChargeId(),
                        request,
                        LoanChargePaymentConstants.PAY_COMMAND));

        return LoanChargePaymentCommandData.builder()
                .loanId(command.getLoanId())
                .chargeId(command.getChargeId())
                .resourceId(response.getResourceId())
                .build();
    }

    private void requireChargeAccess(Long clientId, Long loanId) {
        if (!accessPolicyEvaluator.canAccessLoans(clientId, loanId)) {
            throw new LoanAccessDeniedException();
        }
    }

    private <T> T callChargePayment(Supplier<T> upstream) {
        try {
            return upstream.get();
        } catch (FeignException.NotFound e) {
            throw new LoanChargePaymentNotFoundException();
        } catch (FeignException.BadRequest e) {
            throw new LoanChargePaymentInvalidException(e);
        } catch (FeignException e) {
            throw new LoanChargePaymentUpstreamUnavailableException(e);
        }
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private Long resolveClientId(Jwt jwt) {
        UUID publicId = UUID.fromString(jwt.getSubject());
        UserQueryData user = userQueryService.findByPublicId(publicId);
        return user.getFineractClientId();
    }

    private void requireAccess(Long clientId, Long loanId) {
        if (!accessPolicyEvaluator.canAccessLoans(clientId, loanId)) {
            throw new LoanAccessDeniedException();
        }
    }

    private <T> T call(Supplier<T> upstream) {
        try {
            return upstream.get();
        } catch (FeignException.NotFound e) {
            throw new LoanNotFoundException();
        } catch (FeignException.BadRequest e) {
            throw new LoanApplicationInvalidException(e);
        } catch (FeignException e) {
            throw new LoanUpstreamUnavailableException(e);
        }
    }

    private PostLoansRequest buildSubmitRequest(SubmitLoanApplicationCommand c, Long clientId) {
        return new PostLoansRequest()
                .clientId(clientId)
                .loanType(LOAN_TYPE)
                .productId(c.getProductId())
                .principal(c.getPrincipal())
                .loanTermFrequency(c.getLoanTermFrequency())
                .loanTermFrequencyType(c.getLoanTermFrequencyType())
                .numberOfRepayments(c.getNumberOfRepayments())
                .repaymentEvery(c.getRepaymentEvery())
                .repaymentFrequencyType(c.getRepaymentFrequencyType())
                .interestRatePerPeriod(c.getInterestRatePerPeriod())
                .amortizationType(c.getAmortizationType())
                .interestType(c.getInterestType())
                .interestCalculationPeriodType(c.getInterestCalculationPeriodType())
                .transactionProcessingStrategyCode(c.getTransactionProcessingStrategyCode())
                .expectedDisbursementDate(c.getExpectedDisbursementDate().toString())
                .submittedOnDate(c.getSubmittedOnDate().toString())
                .dateFormat(DATE_FORMAT)
                .locale(LOCALE);
    }

    private PutLoansLoanIdRequest buildModifyRequest(ModifyLoanApplicationCommand c) {
        return new PutLoansLoanIdRequest()
                .loanType(LOAN_TYPE)
                .productId(c.getProductId())
                .principal(c.getPrincipal().longValueExact())
                .loanTermFrequency(c.getLoanTermFrequency())
                .loanTermFrequencyType(c.getLoanTermFrequencyType())
                .numberOfRepayments(c.getNumberOfRepayments())
                .repaymentEvery(c.getRepaymentEvery())
                .repaymentFrequencyType(c.getRepaymentFrequencyType())
                .interestRatePerPeriod(c.getInterestRatePerPeriod())
                .amortizationType(c.getAmortizationType())
                .interestType(c.getInterestType())
                .interestCalculationPeriodType(c.getInterestCalculationPeriodType())
                .transactionProcessingStrategyCode(c.getTransactionProcessingStrategyCode())
                .expectedDisbursementDate(c.getExpectedDisbursementDate().toString())
                .submittedOnDate(c.getSubmittedOnDate().toString())
                .dateFormat(DATE_FORMAT)
                .locale(LOCALE);
    }

}
