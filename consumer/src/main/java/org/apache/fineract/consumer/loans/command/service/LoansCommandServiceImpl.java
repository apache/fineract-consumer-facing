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

import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.command.Command;
import org.apache.fineract.consumer.infrastructure.fineractclient.FineractCaller;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansLoanIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutLoansLoanIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.OwnedAccountsCache;
import org.apache.fineract.consumer.loans.command.data.LoanApplicationCommandData;
import org.apache.fineract.consumer.loans.command.data.ModifyLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.SubmitLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.WithdrawLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.exception.LoanApplicationInvalidException;
import org.apache.fineract.consumer.loans.command.exception.LoanCommandNotFoundException;
import org.apache.fineract.consumer.loans.command.exception.LoanCommandUpstreamUnavailableException;
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
    private final OwnedAccountsCache ownedAccountsCache;
    private final LoansApi loansApi;

    @Override
    @Command
    public LoanApplicationCommandData submitApplication(Jwt jwt, SubmitLoanApplicationCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.LOAN_APPLICATION_SUBMIT);
        Long clientId = resolveClientId(jwt);
        PostLoansRequest request = buildSubmitRequest(command, clientId);
        PostLoansResponse response = call(() -> loansApi.calculateLoanScheduleOrSubmitLoanApplication(request, null));
        ownedAccountsCache.evict(clientId);
        return LoanApplicationCommandData.builder()
                .loanId(response.getLoanId())
                .resourceId(response.getResourceId())
                .clientId(clientId)
                .build();
    }

    @Override
    @Command
    public LoanApplicationCommandData modifyApplication(Jwt jwt, ModifyLoanApplicationCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.LOAN_APPLICATION_MODIFY, command.getLoanId());
        Long clientId = resolveClientId(jwt);
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
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.LOAN_APPLICATION_WITHDRAW, command.getLoanId());
        Long clientId = resolveClientId(jwt);
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

    private Long resolveClientId(Jwt jwt) {
        UUID publicId = UUID.fromString(jwt.getSubject());
        UserQueryData user = userQueryService.findByPublicId(publicId);
        return user.getFineractClientId();
    }

    private <T> T call(Supplier<T> upstream) {
        return FineractCaller.call(upstream,
                e -> new LoanCommandNotFoundException(),
                LoanApplicationInvalidException::new,
                LoanCommandUpstreamUnavailableException::new);
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

}
