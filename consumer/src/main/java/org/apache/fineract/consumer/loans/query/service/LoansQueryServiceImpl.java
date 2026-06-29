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

package org.apache.fineract.consumer.loans.query.service;

import feign.FeignException;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.GuarantorsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanProductsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanProductsProductIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanProductsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdChargesChargeIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdRepaymentPeriod;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdRepaymentSchedule;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdSummary;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsTransactionIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GuarantorData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansRepaymentSchedulePeriods;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansResponse;
import org.apache.fineract.consumer.infrastructure.query.Query;
import org.apache.fineract.consumer.infrastructure.web.AccessPolicyEvaluator;
import org.apache.fineract.consumer.loans.query.data.CalculateLoanScheduleQuery;
import org.apache.fineract.consumer.loans.query.data.LoanAccountListItemQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanAccountQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanApplicationTemplateQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanChargeQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanGuarantorQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanProductOptionQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanScheduleQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanTransactionListQuery;
import org.apache.fineract.consumer.loans.query.data.LoanTransactionQueryData;
import org.apache.fineract.consumer.loans.query.exception.LoanAccessDeniedException;
import org.apache.fineract.consumer.loans.query.exception.LoanNotFoundException;
import org.apache.fineract.consumer.loans.query.exception.LoanProductNotFoundException;
import org.apache.fineract.consumer.loans.query.exception.LoanSchedulePreviewInvalidException;
import org.apache.fineract.consumer.loans.query.exception.LoanUpstreamUnavailableException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoansQueryServiceImpl implements LoansQueryService {

    private static final String ASSOCIATIONS = "repaymentSchedule,transactions";
    private static final String LOCALE = "en";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String LOAN_TYPE = "individual";

    private final LoansApi loansApi;
    private final LoanTransactionsApi loanTransactionsApi;
    private final LoanChargesApi loanChargesApi;
    private final GuarantorsApi guarantorsApi;
    private final LoanProductsApi loanProductsApi;
    private final ClientApi clientApi;
    private final AccessPolicyEvaluator accessPolicyEvaluator;

    @Override
    @Query
    public List<LoanAccountListItemQueryData> listAccounts(Long clientId) {
        GetClientsClientIdAccountsResponse accounts = fetch(() -> clientApi.retrieveAllClientAccounts(clientId));
        if (accounts == null || accounts.getLoanAccounts() == null) {
            return List.of();
        }
        return accounts.getLoanAccounts().stream().map(this::toListItem).toList();
    }

    @Override
    @Query
    public LoanScheduleQueryData calculateSchedule(Long clientId, CalculateLoanScheduleQuery query) {
        PostLoansRequest request = buildScheduleRequest(query, clientId);
        PostLoansResponse response = previewCall(
                () -> loansApi.calculateLoanScheduleOrSubmitLoanApplication(request, CALCULATE_SCHEDULE_COMMAND));
        return toScheduleData(response);
    }

    @Override
    @Query
    public LoanAccountQueryData getLoan(Long clientId, Long loanId) {
        requireAccess(clientId, loanId);
        GetLoansLoanIdResponse loan = fetch(() -> loansApi.retrieveLoan(loanId, false, ASSOCIATIONS, null, null));
        return toAccountData(loan);
    }

    @Override
    @Query
    public List<LoanTransactionQueryData> listTransactions(Long clientId, LoanTransactionListQuery query) {
        requireAccess(clientId, query.getLoanId());
        GetLoansLoanIdTransactionsResponse response = fetch(() -> loanTransactionsApi.retrieveTransactionsByLoanId(
                query.getLoanId(), null, query.getPage(), query.getSize(), query.getSort()));
        if (response == null || response.getContent() == null) {
            return List.of();
        }
        return response.getContent().stream().map(this::toTransactionData).toList();
    }

    @Override
    @Query
    public LoanTransactionQueryData getTransaction(Long clientId, Long loanId, Long transactionId) {
        requireAccess(clientId, loanId);
        GetLoansLoanIdTransactionsTransactionIdResponse transaction =
                fetch(() -> loanTransactionsApi.retrieveTransaction(loanId, transactionId, null));
        return toTransactionData(transaction);
    }

    @Override
    @Query
    public List<LoanChargeQueryData> getCharges(Long clientId, Long loanId) {
        requireAccess(clientId, loanId);
        List<GetLoansLoanIdChargesChargeIdResponse> charges =
                fetch(() -> loanChargesApi.retrieveAllLoanCharges(loanId));
        if (charges == null) {
            return List.of();
        }
        return charges.stream().map(this::toChargeData).toList();
    }

    @Override
    @Query
    public LoanChargeQueryData getCharge(Long clientId, Long loanId, Long chargeId) {
        requireAccess(clientId, loanId);
        GetLoansLoanIdChargesChargeIdResponse charge =
                fetch(() -> loanChargesApi.retrieveLoanCharge(loanId, chargeId));
        return toChargeData(charge);
    }

    @Override
    @Query
    public List<LoanGuarantorQueryData> getGuarantors(Long clientId, Long loanId) {
        requireAccess(clientId, loanId);
        List<GuarantorData> guarantors = fetch(() -> guarantorsApi.retrieveGuarantorDetails(loanId));
        if (guarantors == null) {
            return List.of();
        }
        return guarantors.stream().map(this::toGuarantorData).toList();
    }

    @Override
    @Query
    public LoanApplicationTemplateQueryData getApplicationTemplate(Long productId) {
        if (productId == null) {
            List<GetLoanProductsResponse> products = fetch(loanProductsApi::retrieveAllLoanProducts);
            return toBrowseTemplate(products);
        }
        GetLoanProductsProductIdResponse product = fetchProduct(productId);
        return toDetailTemplate(product);
    }

    private void requireAccess(Long clientId, Long loanId) {
        if (!accessPolicyEvaluator.canAccessLoans(clientId, loanId)) {
            throw new LoanAccessDeniedException();
        }
    }

    private <T> T fetch(Supplier<T> call) {
        try {
            return call.get();
        } catch (FeignException.NotFound e) {
            throw new LoanNotFoundException();
        } catch (FeignException e) {
            throw new LoanUpstreamUnavailableException(e);
        }
    }

    private <T> T previewCall(Supplier<T> call) {
        try {
            return call.get();
        } catch (FeignException.BadRequest e) {
            throw new LoanSchedulePreviewInvalidException(e);
        } catch (FeignException e) {
            throw new LoanUpstreamUnavailableException(e);
        }
    }

    private PostLoansRequest buildScheduleRequest(CalculateLoanScheduleQuery q, Long clientId) {
        return new PostLoansRequest()
                .clientId(clientId)
                .loanType(LOAN_TYPE)
                .productId(q.getProductId())
                .principal(q.getPrincipal())
                .loanTermFrequency(q.getLoanTermFrequency())
                .loanTermFrequencyType(q.getLoanTermFrequencyType())
                .numberOfRepayments(q.getNumberOfRepayments())
                .repaymentEvery(q.getRepaymentEvery())
                .repaymentFrequencyType(q.getRepaymentFrequencyType())
                .interestRatePerPeriod(q.getInterestRatePerPeriod())
                .amortizationType(q.getAmortizationType())
                .interestType(q.getInterestType())
                .interestCalculationPeriodType(q.getInterestCalculationPeriodType())
                .transactionProcessingStrategyCode(q.getTransactionProcessingStrategyCode())
                .expectedDisbursementDate(q.getExpectedDisbursementDate().toString())
                .submittedOnDate(q.getSubmittedOnDate().toString())
                .dateFormat(DATE_FORMAT)
                .locale(LOCALE);
    }

    private LoanScheduleQueryData toScheduleData(PostLoansResponse response) {
        GetLoansLoanIdCurrency currency = response.getCurrency();
        List<LoanScheduleQueryData.Period> periods = response.getPeriods() == null
                ? List.of()
                : response.getPeriods().stream().map(this::toPeriod).toList();
        return LoanScheduleQueryData.builder()
                .currency(currency == null ? null : currency.getCode())
                .totalPrincipalDisbursed(response.getTotalPrincipalDisbursed())
                .totalInterestCharged(response.getTotalInterestCharged())
                .totalRepaymentExpected(response.getTotalRepaymentExpected())
                .periods(periods)
                .build();
    }

    private LoanScheduleQueryData.Period toPeriod(PostLoansRepaymentSchedulePeriods period) {
        return LoanScheduleQueryData.Period.builder()
                .period(period.getPeriod())
                .dueDate(period.getDueDate())
                .principalDue(period.getPrincipalDisbursed())
                .totalDueForPeriod(period.getTotalDueForPeriod())
                .outstandingBalance(period.getPrincipalLoanBalanceOutstanding())
                .build();
    }

    private GetLoanProductsProductIdResponse fetchProduct(Long productId) {
        try {
            return loanProductsApi.retrieveLoanProductDetails(productId);
        } catch (FeignException.NotFound e) {
            throw new LoanProductNotFoundException();
        } catch (FeignException e) {
            throw new LoanUpstreamUnavailableException(e);
        }
    }

    private LoanAccountListItemQueryData toListItem(GetClientsLoanAccounts account) {
        return LoanAccountListItemQueryData.builder()
                .id(account.getId())
                .accountNo(account.getAccountNo())
                .productName(account.getProductName())
                .status(account.getStatus() == null ? null : account.getStatus().getCode())
                .currency(account.getCurrency() == null ? null : account.getCurrency().getCode())
                .build();
    }

    private LoanAccountQueryData toAccountData(GetLoansLoanIdResponse loan) {
        GetLoansLoanIdSummary summary = loan.getSummary();
        GetLoansLoanIdRepaymentPeriod nextDue = nextDuePeriod(loan.getRepaymentSchedule());
        return LoanAccountQueryData.builder()
                .id(loan.getId())
                .accountNo(loan.getAccountNo())
                .productName(loan.getLoanProductName())
                .status(loan.getStatus() == null ? null : loan.getStatus().getCode())
                .principalDisbursed(summary == null ? null : summary.getPrincipalDisbursed())
                .totalOutstanding(summary == null ? null : summary.getTotalOutstanding())
                .interestOutstanding(summary == null ? null : summary.getInterestOutstanding())
                .annualInterestRate(loan.getAnnualInterestRate())
                .currency(loan.getCurrency() == null ? null : loan.getCurrency().getCode())
                .nextDueDate(nextDue == null ? null : nextDue.getDueDate())
                .nextDueAmount(nextDue == null ? null : nextDue.getTotalDueForPeriod())
                .build();
    }

    private GetLoansLoanIdRepaymentPeriod nextDuePeriod(GetLoansLoanIdRepaymentSchedule schedule) {
        if (schedule == null || schedule.getPeriods() == null) {
            return null;
        }
        return schedule.getPeriods().stream()
                .filter(period -> period.getDueDate() != null)
                .filter(period -> !Boolean.TRUE.equals(period.getComplete()))
                .findFirst()
                .orElse(null);
    }

    private LoanTransactionQueryData toTransactionData(GetLoansLoanIdTransactionsTransactionIdResponse transaction) {
        return LoanTransactionQueryData.builder()
                .id(transaction.getId())
                .type(transaction.getType() == null ? null : transaction.getType().getCode())
                .date(transaction.getDate())
                .amount(toBigDecimal(transaction.getAmount()))
                .currency(transaction.getCurrency() == null ? null : transaction.getCurrency().getCode())
                .outstandingLoanBalance(toBigDecimal(transaction.getOutstandingLoanBalance()))
                .build();
    }

    private LoanChargeQueryData toChargeData(GetLoansLoanIdChargesChargeIdResponse charge) {
        return LoanChargeQueryData.builder()
                .id(charge.getId())
                .name(charge.getName())
                .amount(toBigDecimal(charge.getAmount()))
                .amountOutstanding(toBigDecimal(charge.getAmountOutstanding()))
                .chargeTimeType(charge.getChargeTimeType() == null ? null : charge.getChargeTimeType().getCode())
                .chargeCalculationType(
                        charge.getChargeCalculationType() == null ? null : charge.getChargeCalculationType().getCode())
                .currency(charge.getCurrency() == null ? null : charge.getCurrency().getCode())
                .build();
    }

    private LoanGuarantorQueryData toGuarantorData(GuarantorData guarantor) {
        return LoanGuarantorQueryData.builder()
                .id(guarantor.getId())
                .guarantorType(guarantor.getGuarantorType() == null ? null : guarantor.getGuarantorType().getValue())
                .displayName(displayName(guarantor.getFirstname(), guarantor.getLastname()))
                .relationship(guarantor.getClientRelationshipType() == null
                        ? null
                        : guarantor.getClientRelationshipType().getName())
                .build();
    }

    private LoanApplicationTemplateQueryData toBrowseTemplate(List<GetLoanProductsResponse> products) {
        List<LoanProductOptionQueryData> options = products == null
                ? List.of()
                : products.stream()
                        .map(product -> LoanProductOptionQueryData.builder()
                                .id(product.getId())
                                .name(product.getName())
                                .build())
                        .toList();
        return LoanApplicationTemplateQueryData.builder()
                .productOptions(options)
                .build();
    }

    private LoanApplicationTemplateQueryData toDetailTemplate(GetLoanProductsProductIdResponse product) {
        return LoanApplicationTemplateQueryData.builder()
                .productId(product.getId())
                .productName(product.getName())
                .currencyCode(product.getCurrency() == null ? null : product.getCurrency().getCode())
                .principal(toBigDecimal(product.getPrincipal()))
                .minPrincipal(toBigDecimal(product.getMinPrincipal()))
                .maxPrincipal(toBigDecimal(product.getMaxPrincipal()))
                .numberOfRepayments(product.getNumberOfRepayments())
                .minNumberOfRepayments(product.getMinNumberOfRepayments())
                .maxNumberOfRepayments(product.getMaxNumberOfRepayments())
                .repaymentEvery(product.getRepaymentEvery())
                .repaymentFrequencyTypeId(
                        product.getRepaymentFrequencyType() == null ? null : product.getRepaymentFrequencyType().getId())
                .repaymentFrequencyTypeCode(
                        product.getRepaymentFrequencyType() == null ? null : product.getRepaymentFrequencyType().getCode())
                .interestRatePerPeriod(toBigDecimal(product.getInterestRatePerPeriod()))
                .minInterestRatePerPeriod(toBigDecimal(product.getMinInterestRatePerPeriod()))
                .maxInterestRatePerPeriod(toBigDecimal(product.getMaxInterestRatePerPeriod()))
                .annualInterestRate(toBigDecimal(product.getAnnualInterestRate()))
                .interestRateFrequencyTypeId(
                        product.getInterestRateFrequencyType() == null ? null : product.getInterestRateFrequencyType().getId())
                .interestRateFrequencyTypeCode(
                        product.getInterestRateFrequencyType() == null ? null : product.getInterestRateFrequencyType().getCode())
                .amortizationTypeId(product.getAmortizationType() == null ? null : product.getAmortizationType().getId())
                .amortizationTypeCode(product.getAmortizationType() == null ? null : product.getAmortizationType().getCode())
                .interestTypeId(product.getInterestType() == null ? null : product.getInterestType().getId())
                .interestTypeCode(product.getInterestType() == null ? null : product.getInterestType().getCode())
                .interestCalculationPeriodTypeId(
                        product.getInterestCalculationPeriodType() == null
                                ? null
                                : product.getInterestCalculationPeriodType().getId())
                .interestCalculationPeriodTypeCode(
                        product.getInterestCalculationPeriodType() == null
                                ? null
                                : product.getInterestCalculationPeriodType().getCode())
                .transactionProcessingStrategyCode(product.getTransactionProcessingStrategyCode())
                .transactionProcessingStrategyName(product.getTransactionProcessingStrategyName())
                .inArrearsTolerance(product.getInArrearsTolerance())
                .build();
    }

    private static String displayName(String firstname, String lastname) {
        String first = firstname == null ? "" : firstname.trim();
        String last = lastname == null ? "" : lastname.trim();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? null : name;
    }

    private static BigDecimal toBigDecimal(Double value) {
        return value == null ? null : new BigDecimal(value.toString());
    }
}
