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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.CodeValueData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.EnumOptionData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccountsStatus;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoansAccountsCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanChargeCalculationType;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanChargeCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanChargeTimeType;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanProductsAmortizationType;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanProductsCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanProductsInterestRateFrequencyType;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanProductsInterestTemplateType;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanProductsProductIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanProductsRepaymentFrequencyType;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanProductsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdChargesChargeIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdRepaymentPeriod;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdRepaymentSchedule;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdStatus;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdSummary;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsTransactionIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansProductsInterestCalculationPeriodType;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansType;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GuarantorData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansRepaymentSchedulePeriods;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.LoansQueryFineractClient;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
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
import org.apache.fineract.consumer.loans.query.data.LoanTransactionQueryResponse;
import org.apache.fineract.consumer.loans.query.exception.LoanProductNotFoundException;
import org.apache.fineract.consumer.loans.query.exception.LoanQueryAccessDeniedException;
import org.apache.fineract.consumer.loans.query.exception.LoanQueryNotFoundException;
import org.apache.fineract.consumer.loans.query.exception.LoanQueryUpstreamUnavailableException;
import org.apache.fineract.consumer.loans.query.exception.LoanSchedulePreviewInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class LoansQueryServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long CLIENT_ID = 11L;
    private static final Long LOAN_ID = 42L;
    private static final Integer PAGE = 0;
    private static final Integer SIZE = 20;
    private static final Long TRANSACTION_ID = 7L;
    private static final Long CHARGE_ID = 8L;
    private static final Long PRODUCT_ID = 5L;

    @Mock
    private LoansQueryFineractClient fineractClient;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private UserClientResolver userClientResolver;

    @InjectMocks
    private LoansQueryServiceImpl service;

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .claim("scope", "read")
                .build();
    }

    @Test
    void listAccountsMapsIndexFields() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        GetClientsLoanAccounts account = new GetClientsLoanAccounts()
                .id(77L)
                .accountNo("000000077")
                .productName("Personal Loan")
                .status(new GetClientsLoanAccountsStatus().code("loanStatusType.active").active(true))
                .currency(new GetClientsLoansAccountsCurrency().code("USD"));
        when(fineractClient.listClientLoanAccounts(CLIENT_ID)).thenReturn(List.of(account));

        List<LoanAccountListItemQueryData> result = service.listAccounts(jwt);

        assertThat(result).hasSize(1);
        LoanAccountListItemQueryData item = result.get(0);
        assertThat(item.getId()).isEqualTo(77L);
        assertThat(item.getAccountNo()).isEqualTo("000000077");
        assertThat(item.getProductName()).isEqualTo("Personal Loan");
        assertThat(item.getStatus()).isEqualTo("loanStatusType.active");
        assertThat(item.isActive()).isTrue();
        assertThat(item.getCurrency()).isEqualTo("USD");
        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.LOANS_LIST);
    }

    @Test
    void listAccountsMarksAccountInactiveWhenStatusMissing() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        GetClientsLoanAccounts account = new GetClientsLoanAccounts()
                .id(78L)
                .accountNo("000000078");
        when(fineractClient.listClientLoanAccounts(CLIENT_ID)).thenReturn(List.of(account));

        List<LoanAccountListItemQueryData> result = service.listAccounts(jwt);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isNull();
        assertThat(result.get(0).isActive()).isFalse();
    }

    @Test
    void listAccountsEmptyWhenNoLoanAccounts() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(fineractClient.listClientLoanAccounts(CLIENT_ID)).thenReturn(List.of());

        assertThat(service.listAccounts(jwt)).isEmpty();
    }

    @Test
    void listAccountsTranslatesUpstreamFailure() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(fineractClient.listClientLoanAccounts(CLIENT_ID)).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> service.listAccounts(jwt))
                .isInstanceOf(LoanQueryUpstreamUnavailableException.class);
    }

    @Test
    void listTransactionsMapsPageContentAndPassesFineractTotalsThrough() {
        Jwt jwt = jwt();
        GetLoansLoanIdTransactionsResponse response = new GetLoansLoanIdTransactionsResponse()
                .content(List.of(
                        new GetLoansLoanIdTransactionsTransactionIdResponse().id(1L).amount(BigDecimal.valueOf(100.0)),
                        new GetLoansLoanIdTransactionsTransactionIdResponse().id(2L).amount(BigDecimal.valueOf(50.0))))
                .totalElements(42L)
                .totalPages(3);
        when(fineractClient.getTransactionsPage(LOAN_ID, PAGE, SIZE, "date,desc"))
                .thenReturn(response);

        LoanTransactionListQuery query = LoanTransactionListQuery.builder()
                .loanId(LOAN_ID)
                .page(PAGE)
                .size(SIZE)
                .sort("date,desc")
                .build();
        LoanTransactionQueryResponse result = service.listTransactions(jwt, query);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getContent().get(1).getId()).isEqualTo(2L);
        assertThat(result.getPage()).isEqualTo(PAGE);
        assertThat(result.getSize()).isEqualTo(SIZE);
        assertThat(result.getTotalElements()).isEqualTo(42L);
        assertThat(result.getTotalPages()).isEqualTo(3);
        verify(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.LOANS_VIEW), eq(LOAN_ID), any());
    }

    @Test
    void listTransactionsEmptyEnvelopeWhenNoContent() {
        Jwt jwt = jwt();
        when(fineractClient.getTransactionsPage(LOAN_ID, PAGE, SIZE, null))
                .thenReturn(new GetLoansLoanIdTransactionsResponse());

        LoanTransactionListQuery query = LoanTransactionListQuery.builder()
                .loanId(LOAN_ID)
                .page(PAGE)
                .size(SIZE)
                .build();
        LoanTransactionQueryResponse result = service.listTransactions(jwt, query);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getPage()).isEqualTo(PAGE);
        assertThat(result.getSize()).isEqualTo(SIZE);
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }

    @Test
    void listTransactionsDateFilteredComputesTotalsBffSide() {
        Jwt jwt = jwt();
        GetLoansLoanIdTransactionsResponse response = new GetLoansLoanIdTransactionsResponse()
                .content(List.of(
                        transactionOn(1L, LocalDate.of(2026, 1, 1)),
                        transactionOn(2L, LocalDate.of(2026, 1, 5)),
                        transactionOn(3L, LocalDate.of(2026, 1, 10)),
                        transactionOn(4L, LocalDate.of(2026, 1, 15)),
                        transactionOn(5L, LocalDate.of(2026, 1, 20))))
                .totalElements(999L)
                .totalPages(99);
        when(fineractClient.getAllTransactions(LOAN_ID, null)).thenReturn(response);

        LoanTransactionListQuery query = LoanTransactionListQuery.builder()
                .loanId(LOAN_ID)
                .page(0)
                .size(2)
                .fromDate(LocalDate.of(2026, 1, 5))
                .toDate(LocalDate.of(2026, 1, 15))
                .build();
        LoanTransactionQueryResponse result = service.listTransactions(jwt, query);

        assertThat(result.getContent()).extracting(LoanTransactionQueryData::getId).containsExactly(2L, 3L);
        assertThat(result.getTotalElements()).isEqualTo(3L);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getPage()).isZero();
        assertThat(result.getSize()).isEqualTo(2);
        verify(fineractClient).getAllTransactions(LOAN_ID, null);
    }

    @Test
    void listTransactionsDateFilteredSlicesRequestedPage() {
        Jwt jwt = jwt();
        GetLoansLoanIdTransactionsResponse response = new GetLoansLoanIdTransactionsResponse()
                .content(List.of(
                        transactionOn(2L, LocalDate.of(2026, 1, 5)),
                        transactionOn(3L, LocalDate.of(2026, 1, 10)),
                        transactionOn(4L, LocalDate.of(2026, 1, 15))));
        when(fineractClient.getAllTransactions(LOAN_ID, null)).thenReturn(response);

        LoanTransactionListQuery query = LoanTransactionListQuery.builder()
                .loanId(LOAN_ID)
                .page(1)
                .size(2)
                .fromDate(LocalDate.of(2026, 1, 5))
                .toDate(LocalDate.of(2026, 1, 15))
                .build();
        LoanTransactionQueryResponse result = service.listTransactions(jwt, query);

        assertThat(result.getContent()).extracting(LoanTransactionQueryData::getId).containsExactly(4L);
        assertThat(result.getTotalElements()).isEqualTo(3L);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void listTransactionsDateFilteredEmptyWhenPagePastEnd() {
        Jwt jwt = jwt();
        GetLoansLoanIdTransactionsResponse response = new GetLoansLoanIdTransactionsResponse()
                .content(List.of(transactionOn(2L, LocalDate.of(2026, 1, 5))));
        when(fineractClient.getAllTransactions(LOAN_ID, null)).thenReturn(response);

        LoanTransactionListQuery query = LoanTransactionListQuery.builder()
                .loanId(LOAN_ID)
                .page(5)
                .size(2)
                .fromDate(LocalDate.of(2026, 1, 1))
                .toDate(LocalDate.of(2026, 1, 31))
                .build();
        LoanTransactionQueryResponse result = service.listTransactions(jwt, query);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    void listTransactionsDeniedWhenAccessPolicyRejects() {
        Jwt jwt = jwt();
        doThrow(new LoanQueryAccessDeniedException())
                .when(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.LOANS_VIEW), eq(LOAN_ID), any());

        LoanTransactionListQuery query = LoanTransactionListQuery.builder()
                .loanId(LOAN_ID)
                .page(PAGE)
                .size(SIZE)
                .build();

        assertThatThrownBy(() -> service.listTransactions(jwt, query))
                .isInstanceOf(LoanQueryAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", LoanQueryAccessDeniedException.CODE);

        verify(fineractClient, never()).getTransactionsPage(any(), anyInt(), anyInt(), any());
        verify(fineractClient, never()).getAllTransactions(any(), any());
    }

    @Test
    void getLoanMapsMainFields() {
        Jwt jwt = jwt();
        GetLoansLoanIdResponse loan = new GetLoansLoanIdResponse()
                .id(LOAN_ID)
                .accountNo("000000042")
                .loanProductName("Personal Loan")
                .status(new GetLoansLoanIdStatus().code("loanStatusType.active").active(true))
                .summary(new GetLoansLoanIdSummary()
                        .principalDisbursed(new BigDecimal("1000.00"))
                        .totalOutstanding(new BigDecimal("750.00"))
                        .interestOutstanding(new BigDecimal("12.50")))
                .annualInterestRate(new BigDecimal("9.5"))
                .currency(new GetLoansLoanIdCurrency().code("USD"));
        when(fineractClient.getLoanWithScheduleAndTransactions(LOAN_ID)).thenReturn(loan);

        LoanAccountQueryData result = service.getLoan(jwt, LOAN_ID);

        assertThat(result.getId()).isEqualTo(LOAN_ID);
        assertThat(result.getAccountNo()).isEqualTo("000000042");
        assertThat(result.getProductName()).isEqualTo("Personal Loan");
        assertThat(result.getStatus()).isEqualTo("loanStatusType.active");
        assertThat(result.isActive()).isTrue();
        assertThat(result.getPrincipalDisbursed()).isEqualByComparingTo("1000.00");
        assertThat(result.getTotalOutstanding()).isEqualByComparingTo("750.00");
        assertThat(result.getInterestOutstanding()).isEqualByComparingTo("12.50");
        assertThat(result.getAnnualInterestRate()).isEqualByComparingTo("9.5");
        assertThat(result.getCurrency()).isEqualTo("USD");
        verify(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.LOANS_VIEW), eq(LOAN_ID), any());
    }

    @Test
    void getLoanTakesNextDueFromFirstIncompletePeriodWithDueDate() {
        Jwt jwt = jwt();
        GetLoansLoanIdRepaymentSchedule schedule = new GetLoansLoanIdRepaymentSchedule().periods(List.of(
                new GetLoansLoanIdRepaymentPeriod().period(0).totalDueForPeriod(new BigDecimal("999.00")),
                new GetLoansLoanIdRepaymentPeriod().period(1).dueDate(LocalDate.of(2026, 2, 1))
                        .complete(true).totalDueForPeriod(new BigDecimal("100.00")),
                new GetLoansLoanIdRepaymentPeriod().period(2).dueDate(LocalDate.of(2026, 3, 1))
                        .complete(false).totalDueForPeriod(new BigDecimal("101.00")),
                new GetLoansLoanIdRepaymentPeriod().period(3).dueDate(LocalDate.of(2026, 4, 1))
                        .complete(false).totalDueForPeriod(new BigDecimal("102.00"))));
        when(fineractClient.getLoanWithScheduleAndTransactions(LOAN_ID))
                .thenReturn(new GetLoansLoanIdResponse().id(LOAN_ID).repaymentSchedule(schedule));

        LoanAccountQueryData result = service.getLoan(jwt, LOAN_ID);

        assertThat(result.getNextDueDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(result.getNextDueAmount()).isEqualByComparingTo("101.00");
    }

    @Test
    void getLoanTranslatesFineractNotFound() {
        Jwt jwt = jwt();
        when(fineractClient.getLoanWithScheduleAndTransactions(LOAN_ID))
                .thenThrow(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> service.getLoan(jwt, LOAN_ID))
                .isInstanceOf(LoanQueryNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", LoanQueryNotFoundException.CODE);
    }

    @Test
    void getLoanDeniedWhenAccessPolicyRejects() {
        Jwt jwt = jwt();
        doThrow(new LoanQueryAccessDeniedException())
                .when(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.LOANS_VIEW), eq(LOAN_ID), any());

        assertThatThrownBy(() -> service.getLoan(jwt, LOAN_ID))
                .isInstanceOf(LoanQueryAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", LoanQueryAccessDeniedException.CODE);

        verify(fineractClient, never()).getLoanWithScheduleAndTransactions(any());
    }

    @Test
    void calculateScheduleBuildsRequestFromQueryWithResolvedClientId() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        ArgumentCaptor<PostLoansRequest> captor = ArgumentCaptor.forClass(PostLoansRequest.class);
        when(fineractClient.previewSchedule(captor.capture())).thenReturn(new PostLoansResponse());

        service.calculateSchedule(jwt, scheduleQuery());

        PostLoansRequest sent = captor.getValue();
        assertThat(sent.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(sent.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(sent.getPrincipal()).isEqualByComparingTo("5000.00");
        assertThat(sent.getLoanTermFrequency()).isEqualTo(12);
        assertThat(sent.getLoanTermFrequencyType()).isEqualTo(2);
        assertThat(sent.getNumberOfRepayments()).isEqualTo(12);
        assertThat(sent.getRepaymentEvery()).isEqualTo(1);
        assertThat(sent.getRepaymentFrequencyType()).isEqualTo(2);
        assertThat(sent.getInterestRatePerPeriod()).isEqualByComparingTo("1.5");
        assertThat(sent.getAmortizationType()).isEqualTo(1);
        assertThat(sent.getInterestType()).isZero();
        assertThat(sent.getInterestCalculationPeriodType()).isEqualTo(1);
        assertThat(sent.getTransactionProcessingStrategyCode()).isEqualTo("mifos-standard-strategy");
        assertThat(sent.getExpectedDisbursementDate()).isEqualTo("2026-02-01");
        assertThat(sent.getSubmittedOnDate()).isEqualTo("2026-01-25");
        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.LOAN_SCHEDULE_CALCULATE);
    }

    @Test
    void calculateScheduleMapsResponse() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        PostLoansRepaymentSchedulePeriods first = new PostLoansRepaymentSchedulePeriods()
                .period(1)
                .dueDate(LocalDate.of(2026, 3, 1))
                .principalDisbursed(400L)
                .totalDueForPeriod(450L)
                .principalLoanBalanceOutstanding(4600L);
        PostLoansRepaymentSchedulePeriods second = new PostLoansRepaymentSchedulePeriods()
                .period(2)
                .dueDate(LocalDate.of(2026, 4, 1))
                .principalDisbursed(410L)
                .totalDueForPeriod(455L)
                .principalLoanBalanceOutstanding(4190L);
        PostLoansResponse response = new PostLoansResponse()
                .currency(new GetLoansLoanIdCurrency().code("USD"))
                .totalPrincipalDisbursed(5000L)
                .totalInterestCharged(new BigDecimal("540.00"))
                .totalRepaymentExpected(new BigDecimal("5540.00"))
                .periods(new LinkedHashSet<>(List.of(first, second)));
        when(fineractClient.previewSchedule(any())).thenReturn(response);

        LoanScheduleQueryData result = service.calculateSchedule(jwt, scheduleQuery());

        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getTotalPrincipalDisbursed()).isEqualTo(5000L);
        assertThat(result.getTotalInterestCharged()).isEqualByComparingTo("540.00");
        assertThat(result.getTotalRepaymentExpected()).isEqualByComparingTo("5540.00");
        assertThat(result.getPeriods()).hasSize(2);
        LoanScheduleQueryData.Period period = result.getPeriods().get(0);
        assertThat(period.getPeriod()).isEqualTo(1);
        assertThat(period.getDueDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(period.getPrincipalDue()).isEqualTo(400L);
        assertThat(period.getTotalDueForPeriod()).isEqualTo(450L);
        assertThat(period.getOutstandingBalance()).isEqualTo(4600L);
        assertThat(result.getPeriods().get(1).getPeriod()).isEqualTo(2);
    }

    @Test
    void calculateScheduleTranslatesFineractBadRequestToPreviewInvalid() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(fineractClient.previewSchedule(any())).thenThrow(mock(FeignException.BadRequest.class));

        assertThatThrownBy(() -> service.calculateSchedule(jwt, scheduleQuery()))
                .isInstanceOf(LoanSchedulePreviewInvalidException.class)
                .hasFieldOrPropertyWithValue("code", LoanSchedulePreviewInvalidException.CODE);
    }

    @Test
    void calculateScheduleTranslatesFineractNotFoundToUpstreamUnavailable() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(fineractClient.previewSchedule(any())).thenThrow(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> service.calculateSchedule(jwt, scheduleQuery()))
                .isInstanceOf(LoanQueryUpstreamUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", LoanQueryUpstreamUnavailableException.CODE);
    }

    @Test
    void getTransactionMapsFields() {
        Jwt jwt = jwt();
        GetLoansLoanIdTransactionsTransactionIdResponse transaction = new GetLoansLoanIdTransactionsTransactionIdResponse()
                .id(TRANSACTION_ID)
                .type(new GetLoansType().code("loanTransactionType.repayment"))
                .date(LocalDate.of(2026, 3, 1))
                .amount(new BigDecimal("450.00"))
                .currency(new GetLoansCurrency().code("USD"))
                .outstandingLoanBalance(new BigDecimal("4600.00"));
        when(fineractClient.getTransaction(LOAN_ID, TRANSACTION_ID)).thenReturn(transaction);

        LoanTransactionQueryData result = service.getTransaction(jwt, LOAN_ID, TRANSACTION_ID);

        assertThat(result.getId()).isEqualTo(TRANSACTION_ID);
        assertThat(result.getType()).isEqualTo("loanTransactionType.repayment");
        assertThat(result.getDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(result.getAmount()).isEqualByComparingTo("450.00");
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.getOutstandingLoanBalance()).isEqualByComparingTo("4600.00");
        verify(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.LOANS_VIEW), eq(LOAN_ID), any());
    }

    @Test
    void getTransactionTranslatesFineractNotFound() {
        Jwt jwt = jwt();
        when(fineractClient.getTransaction(LOAN_ID, TRANSACTION_ID)).thenThrow(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> service.getTransaction(jwt, LOAN_ID, TRANSACTION_ID))
                .isInstanceOf(LoanQueryNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", LoanQueryNotFoundException.CODE);
    }

    @Test
    void getChargesMapsList() {
        Jwt jwt = jwt();
        when(fineractClient.getCharges(LOAN_ID)).thenReturn(List.of(
                new GetLoansLoanIdChargesChargeIdResponse().id(1L).name("Processing fee"),
                new GetLoansLoanIdChargesChargeIdResponse().id(2L).name("Late fee")));

        List<LoanChargeQueryData> result = service.getCharges(jwt, LOAN_ID);

        assertThat(result).extracting(LoanChargeQueryData::getId).containsExactly(1L, 2L);
        assertThat(result).extracting(LoanChargeQueryData::getName).containsExactly("Processing fee", "Late fee");
        verify(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.LOANS_VIEW), eq(LOAN_ID), any());
    }

    @Test
    void getChargesEmptyWhenFineractReturnsNull() {
        Jwt jwt = jwt();
        when(fineractClient.getCharges(LOAN_ID)).thenReturn(null);

        assertThat(service.getCharges(jwt, LOAN_ID)).isEmpty();
    }

    @Test
    void getChargeMapsFieldsIncludingTypeCodes() {
        Jwt jwt = jwt();
        GetLoansLoanIdChargesChargeIdResponse charge = new GetLoansLoanIdChargesChargeIdResponse()
                .id(CHARGE_ID)
                .name("Processing fee")
                .amount(new BigDecimal("25.00"))
                .amountOutstanding(new BigDecimal("10.00"))
                .chargeTimeType(new GetLoanChargeTimeType().code("chargeTimeType.disbursement"))
                .chargeCalculationType(new GetLoanChargeCalculationType().code("chargeCalculationType.flat"))
                .currency(new GetLoanChargeCurrency().code("USD"));
        when(fineractClient.getCharge(LOAN_ID, CHARGE_ID)).thenReturn(charge);

        LoanChargeQueryData result = service.getCharge(jwt, LOAN_ID, CHARGE_ID);

        assertThat(result.getId()).isEqualTo(CHARGE_ID);
        assertThat(result.getName()).isEqualTo("Processing fee");
        assertThat(result.getAmount()).isEqualByComparingTo("25.00");
        assertThat(result.getAmountOutstanding()).isEqualByComparingTo("10.00");
        assertThat(result.getChargeTimeType()).isEqualTo("chargeTimeType.disbursement");
        assertThat(result.getChargeCalculationType()).isEqualTo("chargeCalculationType.flat");
        assertThat(result.getCurrency()).isEqualTo("USD");
        verify(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.LOANS_VIEW), eq(LOAN_ID), any());
    }

    @Test
    void getChargeTranslatesFineractNotFound() {
        Jwt jwt = jwt();
        when(fineractClient.getCharge(LOAN_ID, CHARGE_ID)).thenThrow(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> service.getCharge(jwt, LOAN_ID, CHARGE_ID))
                .isInstanceOf(LoanQueryNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", LoanQueryNotFoundException.CODE);
    }

    @Test
    void getGuarantorsMapsFields() {
        Jwt jwt = jwt();
        GuarantorData guarantor = new GuarantorData()
                .id(3L)
                .guarantorType(new EnumOptionData().code("guarantor.existing.customer"))
                .firstname("Asha")
                .lastname("Rao")
                .clientRelationshipType(new CodeValueData().name("Sibling"));
        when(fineractClient.getGuarantors(LOAN_ID)).thenReturn(List.of(guarantor));

        List<LoanGuarantorQueryData> result = service.getGuarantors(jwt, LOAN_ID);

        assertThat(result).hasSize(1);
        LoanGuarantorQueryData item = result.get(0);
        assertThat(item.getId()).isEqualTo(3L);
        assertThat(item.getGuarantorType()).isEqualTo("guarantor.existing.customer");
        assertThat(item.getDisplayName()).isEqualTo("Asha Rao");
        assertThat(item.getRelationship()).isEqualTo("Sibling");
        verify(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.LOANS_VIEW), eq(LOAN_ID), any());
    }

    @Test
    void getGuarantorsTrimsAndJoinsDisplayName() {
        Jwt jwt = jwt();
        when(fineractClient.getGuarantors(LOAN_ID)).thenReturn(List.of(
                new GuarantorData().id(3L).firstname("  Asha ").lastname(" Rao  "),
                new GuarantorData().id(4L).firstname("Asha").lastname(null),
                new GuarantorData().id(5L).firstname(null).lastname("Rao")));

        List<LoanGuarantorQueryData> result = service.getGuarantors(jwt, LOAN_ID);

        assertThat(result).extracting(LoanGuarantorQueryData::getDisplayName)
                .containsExactly("Asha Rao", "Asha", "Rao");
    }

    @Test
    void getGuarantorsDisplayNameNullWhenNamesBlank() {
        Jwt jwt = jwt();
        when(fineractClient.getGuarantors(LOAN_ID)).thenReturn(List.of(
                new GuarantorData().id(3L).firstname("   ").lastname(""),
                new GuarantorData().id(4L).firstname(null).lastname(null)));

        List<LoanGuarantorQueryData> result = service.getGuarantors(jwt, LOAN_ID);

        assertThat(result).extracting(LoanGuarantorQueryData::getDisplayName).containsExactly(null, null);
    }

    @Test
    void getGuarantorsEmptyWhenFineractReturnsNull() {
        Jwt jwt = jwt();
        when(fineractClient.getGuarantors(LOAN_ID)).thenReturn(null);

        assertThat(service.getGuarantors(jwt, LOAN_ID)).isEmpty();
    }

    @Test
    void getApplicationTemplateWithoutProductListsProductOptions() {
        Jwt jwt = jwt();
        when(fineractClient.listLoanProducts()).thenReturn(List.of(
                new GetLoanProductsResponse().id(PRODUCT_ID).name("Personal Loan"),
                new GetLoanProductsResponse().id(6L).name("Vehicle Loan")));

        LoanApplicationTemplateQueryData result = service.getApplicationTemplate(jwt, null);

        assertThat(result.getProductOptions()).extracting(LoanProductOptionQueryData::getId)
                .containsExactly(PRODUCT_ID, 6L);
        assertThat(result.getProductOptions()).extracting(LoanProductOptionQueryData::getName)
                .containsExactly("Personal Loan", "Vehicle Loan");
        assertThat(result.getProductId()).isNull();
        verify(fineractClient, never()).getLoanProduct(any());
        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.LOAN_APPLICATION_TEMPLATE_VIEW);
    }

    @Test
    void getApplicationTemplateWithProductMapsDetailFields() {
        Jwt jwt = jwt();
        GetLoanProductsProductIdResponse product = new GetLoanProductsProductIdResponse()
                .id(PRODUCT_ID)
                .name("Personal Loan")
                .currency(new GetLoanProductsCurrency().code("USD"))
                .principal(new BigDecimal("5000.00"))
                .minPrincipal(new BigDecimal("1000.00"))
                .maxPrincipal(new BigDecimal("10000.00"))
                .numberOfRepayments(12)
                .minNumberOfRepayments(6)
                .maxNumberOfRepayments(24)
                .repaymentEvery(1)
                .repaymentFrequencyType(
                        new GetLoanProductsRepaymentFrequencyType().id(2L).code("repaymentFrequency.periodFrequencyType.months"))
                .interestRatePerPeriod(new BigDecimal("1.5"))
                .minInterestRatePerPeriod(new BigDecimal("1.0"))
                .maxInterestRatePerPeriod(new BigDecimal("2.0"))
                .annualInterestRate(new BigDecimal("18.0"))
                .interestRateFrequencyType(
                        new GetLoanProductsInterestRateFrequencyType().id(2L).code("interestRateFrequency.periodFrequencyType.months"))
                .amortizationType(
                        new GetLoanProductsAmortizationType().id(1L).code("amortizationType.equal.installments"))
                .interestType(
                        new GetLoanProductsInterestTemplateType().id(0L).code("interestType.declining.balance"))
                .interestCalculationPeriodType(
                        new GetLoansProductsInterestCalculationPeriodType().id(1L).code("interestCalculationPeriodType.same.as.repayment.period"))
                .transactionProcessingStrategyCode("mifos-standard-strategy")
                .transactionProcessingStrategyName("Penalties, Fees, Interest, Principal order")
                .inArrearsTolerance(0);
        when(fineractClient.getLoanProduct(PRODUCT_ID)).thenReturn(product);

        LoanApplicationTemplateQueryData result = service.getApplicationTemplate(jwt, PRODUCT_ID);

        assertThat(result.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(result.getProductName()).isEqualTo("Personal Loan");
        assertThat(result.getCurrencyCode()).isEqualTo("USD");
        assertThat(result.getPrincipal()).isEqualByComparingTo("5000.00");
        assertThat(result.getMinPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(result.getMaxPrincipal()).isEqualByComparingTo("10000.00");
        assertThat(result.getNumberOfRepayments()).isEqualTo(12);
        assertThat(result.getMinNumberOfRepayments()).isEqualTo(6);
        assertThat(result.getMaxNumberOfRepayments()).isEqualTo(24);
        assertThat(result.getRepaymentEvery()).isEqualTo(1);
        assertThat(result.getRepaymentFrequencyTypeId()).isEqualTo(2L);
        assertThat(result.getRepaymentFrequencyTypeCode()).isEqualTo("repaymentFrequency.periodFrequencyType.months");
        assertThat(result.getInterestRatePerPeriod()).isEqualByComparingTo("1.5");
        assertThat(result.getMinInterestRatePerPeriod()).isEqualByComparingTo("1.0");
        assertThat(result.getMaxInterestRatePerPeriod()).isEqualByComparingTo("2.0");
        assertThat(result.getAnnualInterestRate()).isEqualByComparingTo("18.0");
        assertThat(result.getInterestRateFrequencyTypeId()).isEqualTo(2L);
        assertThat(result.getInterestRateFrequencyTypeCode())
                .isEqualTo("interestRateFrequency.periodFrequencyType.months");
        assertThat(result.getAmortizationTypeId()).isEqualTo(1L);
        assertThat(result.getAmortizationTypeCode()).isEqualTo("amortizationType.equal.installments");
        assertThat(result.getInterestTypeId()).isZero();
        assertThat(result.getInterestTypeCode()).isEqualTo("interestType.declining.balance");
        assertThat(result.getInterestCalculationPeriodTypeId()).isEqualTo(1L);
        assertThat(result.getInterestCalculationPeriodTypeCode())
                .isEqualTo("interestCalculationPeriodType.same.as.repayment.period");
        assertThat(result.getTransactionProcessingStrategyCode()).isEqualTo("mifos-standard-strategy");
        assertThat(result.getTransactionProcessingStrategyName()).isEqualTo("Penalties, Fees, Interest, Principal order");
        assertThat(result.getInArrearsTolerance()).isZero();
        verify(fineractClient, never()).listLoanProducts();
    }

    @Test
    void getApplicationTemplateTranslatesFineractProductNotFound() {
        Jwt jwt = jwt();
        when(fineractClient.getLoanProduct(PRODUCT_ID)).thenThrow(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> service.getApplicationTemplate(jwt, PRODUCT_ID))
                .isInstanceOf(LoanProductNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", LoanProductNotFoundException.CODE);
    }

    private static CalculateLoanScheduleQuery scheduleQuery() {
        return CalculateLoanScheduleQuery.builder()
                .productId(PRODUCT_ID)
                .principal(new BigDecimal("5000.00"))
                .loanTermFrequency(12)
                .loanTermFrequencyType(2)
                .numberOfRepayments(12)
                .repaymentEvery(1)
                .repaymentFrequencyType(2)
                .interestRatePerPeriod(new BigDecimal("1.5"))
                .amortizationType(1)
                .interestType(0)
                .interestCalculationPeriodType(1)
                .transactionProcessingStrategyCode("mifos-standard-strategy")
                .expectedDisbursementDate(LocalDate.of(2026, 2, 1))
                .submittedOnDate(LocalDate.of(2026, 1, 25))
                .build();
    }

    private static GetLoansLoanIdTransactionsTransactionIdResponse transactionOn(Long id, LocalDate date) {
        return new GetLoansLoanIdTransactionsTransactionIdResponse().id(id).date(date);
    }
}
