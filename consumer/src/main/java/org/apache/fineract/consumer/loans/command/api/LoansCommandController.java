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

package org.apache.fineract.consumer.loans.command.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.apache.fineract.consumer.loans.command.data.ConfirmLoanChargePaymentCommand;
import org.apache.fineract.consumer.loans.command.data.ConfirmLoanChargePaymentCommandRequest;
import org.apache.fineract.consumer.loans.command.data.InitiateLoanChargePaymentCommand;
import org.apache.fineract.consumer.loans.command.data.LoanApplicationCommandData;
import org.apache.fineract.consumer.loans.command.data.LoanChargePaymentChallengeCommandData;
import org.apache.fineract.consumer.loans.command.data.LoanChargePaymentCommandData;
import org.apache.fineract.consumer.loans.command.data.ModifyLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.ModifyLoanApplicationCommandRequest;
import org.apache.fineract.consumer.loans.command.data.SubmitLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.SubmitLoanApplicationCommandRequest;
import org.apache.fineract.consumer.loans.command.data.WithdrawLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.WithdrawLoanApplicationCommandRequest;
import org.apache.fineract.consumer.loans.command.exception.LoanApplicationInvalidException;
import org.apache.fineract.consumer.loans.command.service.LoansCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/loans", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class LoansCommandController {

    private final LoansCommandService loansCommandService;

    @Operation(operationId = "submitLoanApplication")
    @PostMapping
    public ResponseEntity<LoanApplicationCommandData> submit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SubmitLoanApplicationCommandRequest request) {
        SubmitLoanApplicationCommand cmd = SubmitLoanApplicationCommand.builder()
                .productId(request.getProductId())
                .principal(request.getPrincipal())
                .loanTermFrequency(request.getLoanTermFrequency())
                .loanTermFrequencyType(request.getLoanTermFrequencyType())
                .numberOfRepayments(request.getNumberOfRepayments())
                .repaymentEvery(request.getRepaymentEvery())
                .repaymentFrequencyType(request.getRepaymentFrequencyType())
                .interestRatePerPeriod(request.getInterestRatePerPeriod())
                .amortizationType(request.getAmortizationType())
                .interestType(request.getInterestType())
                .interestCalculationPeriodType(request.getInterestCalculationPeriodType())
                .transactionProcessingStrategyCode(request.getTransactionProcessingStrategyCode())
                .expectedDisbursementDate(request.getExpectedDisbursementDate())
                .submittedOnDate(request.getSubmittedOnDate())
                .build();
        LoanApplicationCommandData data = loansCommandService.submitApplication(jwt, cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(data);
    }

    @Operation(operationId = "modifyLoanApplication")
    @PutMapping("/{loanId}")
    public ResponseEntity<LoanApplicationCommandData> modify(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long loanId,
            @Valid @RequestBody ModifyLoanApplicationCommandRequest request) {
        ModifyLoanApplicationCommand cmd = ModifyLoanApplicationCommand.builder()
                .loanId(loanId)
                .productId(request.getProductId())
                .principal(request.getPrincipal())
                .loanTermFrequency(request.getLoanTermFrequency())
                .loanTermFrequencyType(request.getLoanTermFrequencyType())
                .numberOfRepayments(request.getNumberOfRepayments())
                .repaymentEvery(request.getRepaymentEvery())
                .repaymentFrequencyType(request.getRepaymentFrequencyType())
                .interestRatePerPeriod(request.getInterestRatePerPeriod())
                .amortizationType(request.getAmortizationType())
                .interestType(request.getInterestType())
                .interestCalculationPeriodType(request.getInterestCalculationPeriodType())
                .transactionProcessingStrategyCode(request.getTransactionProcessingStrategyCode())
                .expectedDisbursementDate(request.getExpectedDisbursementDate())
                .submittedOnDate(request.getSubmittedOnDate())
                .build();
        return ResponseEntity.ok(loansCommandService.modifyApplication(jwt, cmd));
    }

    @Operation(operationId = "withdrawLoanApplication")
    @PostMapping("/{loanId}")
    public ResponseEntity<LoanApplicationCommandData> withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long loanId,
            @RequestParam("command") String command,
            @Valid @RequestBody WithdrawLoanApplicationCommandRequest request) {
        if (!LoansCommandService.WITHDRAW_COMMAND.equals(command)) {
            throw new LoanApplicationInvalidException();
        }
        WithdrawLoanApplicationCommand cmd = WithdrawLoanApplicationCommand.builder()
                .loanId(loanId)
                .withdrawnOnDate(request.getWithdrawnOnDate())
                .build();
        return ResponseEntity.ok(loansCommandService.withdrawApplication(jwt, cmd));
    }

    @Operation(operationId = "initiateLoanChargePayment")
    @PostMapping("/{loanId}/charges/{chargeId}/pay")
    public ResponseEntity<LoanChargePaymentChallengeCommandData> initiateChargePayment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @PathVariable Long loanId,
            @PathVariable Long chargeId) {
        InitiateLoanChargePaymentCommand command = InitiateLoanChargePaymentCommand.builder()
                .loanId(loanId)
                .chargeId(chargeId)
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(loansCommandService.initiateChargePayment(jwt, command));
    }

    @Operation(operationId = "confirmLoanChargePayment")
    @PostMapping("/{loanId}/charges/{chargeId}/pay/confirm")
    public ResponseEntity<LoanChargePaymentCommandData> confirmChargePayment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @PathVariable Long loanId,
            @PathVariable Long chargeId,
            @Valid @RequestBody ConfirmLoanChargePaymentCommandRequest request) {
        ConfirmLoanChargePaymentCommand command = ConfirmLoanChargePaymentCommand.builder()
                .loanId(loanId)
                .chargeId(chargeId)
                .stepUpToken(request.getStepUpToken())
                .otp(request.getOtp())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(loansCommandService.confirmChargePayment(jwt, command));
    }
}
