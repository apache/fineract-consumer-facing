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

package org.apache.fineract.consumer.loans.query.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.web.UserClientResolver;
import org.apache.fineract.consumer.loans.query.data.CalculateLoanScheduleQuery;
import org.apache.fineract.consumer.loans.query.data.LoanAccountListItemQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanAccountQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanApplicationTemplateQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanChargeQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanGuarantorQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanScheduleQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanSchedulePreviewQueryRequest;
import org.apache.fineract.consumer.loans.query.data.LoanTransactionListQuery;
import org.apache.fineract.consumer.loans.query.data.LoanTransactionQueryData;
import org.apache.fineract.consumer.loans.query.service.LoansQueryService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/loans", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class LoansQueryController {

    private final LoansQueryService loansQueryService;
    private final UserClientResolver userClientResolver;

    @Operation(operationId = "listLoanAccounts")
    @GetMapping
    public List<LoanAccountListItemQueryData> listAccounts(@AuthenticationPrincipal Jwt jwt) {
        Long clientId = userClientResolver.resolveClientId(jwt);
        return loansQueryService.listAccounts(clientId);
    }

    @Operation(operationId = "previewLoanSchedule")
    @PostMapping("/schedule-preview")
    public LoanScheduleQueryData previewSchedule(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody LoanSchedulePreviewQueryRequest request) {
        Long clientId = userClientResolver.resolveClientId(jwt);
        CalculateLoanScheduleQuery query = CalculateLoanScheduleQuery.builder()
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
        return loansQueryService.calculateSchedule(clientId, query);
    }

    @Operation(operationId = "getLoanAccount")
    @GetMapping("/{loanId}")
    public LoanAccountQueryData getLoan(@AuthenticationPrincipal Jwt jwt, @PathVariable Long loanId) {
        Long clientId = userClientResolver.resolveClientId(jwt);
        return loansQueryService.getLoan(clientId, loanId);
    }

    @Operation(operationId = "listLoanTransactions")
    @GetMapping("/{loanId}/transactions")
    public List<LoanTransactionQueryData> listTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long loanId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Long clientId = userClientResolver.resolveClientId(jwt);
        LoanTransactionListQuery query = LoanTransactionListQuery.builder()
                .loanId(loanId)
                .page(page)
                .size(size)
                .sort(sort)
                .build();
        return loansQueryService.listTransactions(clientId, query);
    }

    @Operation(operationId = "getLoanTransaction")
    @GetMapping("/{loanId}/transactions/{transactionId}")
    public LoanTransactionQueryData getTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long loanId,
            @PathVariable Long transactionId) {
        Long clientId = userClientResolver.resolveClientId(jwt);
        return loansQueryService.getTransaction(clientId, loanId, transactionId);
    }

    @Operation(operationId = "getLoanCharges")
    @GetMapping("/{loanId}/charges")
    public List<LoanChargeQueryData> getCharges(@AuthenticationPrincipal Jwt jwt, @PathVariable Long loanId) {
        Long clientId = userClientResolver.resolveClientId(jwt);
        return loansQueryService.getCharges(clientId, loanId);
    }

    @Operation(operationId = "getLoanCharge")
    @GetMapping("/{loanId}/charges/{chargeId}")
    public LoanChargeQueryData getCharge(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long loanId,
            @PathVariable Long chargeId) {
        Long clientId = userClientResolver.resolveClientId(jwt);
        return loansQueryService.getCharge(clientId, loanId, chargeId);
    }

    @Operation(operationId = "getLoanGuarantors")
    @GetMapping("/{loanId}/guarantors")
    public List<LoanGuarantorQueryData> getGuarantors(@AuthenticationPrincipal Jwt jwt, @PathVariable Long loanId) {
        Long clientId = userClientResolver.resolveClientId(jwt);
        return loansQueryService.getGuarantors(clientId, loanId);
    }

    @Operation(operationId = "getLoanApplicationTemplate")
    @GetMapping("/template")
    public LoanApplicationTemplateQueryData getApplicationTemplate(@RequestParam(required = false) Long productId) {
        return loansQueryService.getApplicationTemplate(productId);
    }
}
