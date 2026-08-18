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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccountsStatus;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoansAccountsCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsTransactionIdResponse;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.loans.query.data.LoanAccountListItemQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanTransactionListQuery;
import org.apache.fineract.consumer.loans.query.data.LoanTransactionQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanTransactionQueryResponse;
import org.apache.fineract.consumer.loans.query.exception.LoanQueryAccessDeniedException;
import org.apache.fineract.consumer.loans.query.exception.LoanQueryUpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock
    private ClientApi clientApi;

    @Mock
    private LoanTransactionsApi loanTransactionsApi;

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
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse().loanAccounts(Set.of(account)));

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
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse().loanAccounts(Set.of(account)));

        List<LoanAccountListItemQueryData> result = service.listAccounts(jwt);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isNull();
        assertThat(result.get(0).isActive()).isFalse();
    }

    @Test
    void listAccountsEmptyWhenNoLoanAccounts() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse());

        assertThat(service.listAccounts(jwt)).isEmpty();
    }

    @Test
    void listAccountsTranslatesUpstreamFailure() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenThrow(mock(FeignException.class));

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
        when(loanTransactionsApi.retrieveTransactionsByLoanId(LOAN_ID, null, PAGE, SIZE, "date,desc"))
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
        when(loanTransactionsApi.retrieveTransactionsByLoanId(LOAN_ID, null, PAGE, SIZE, null))
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
        when(loanTransactionsApi.retrieveTransactionsByLoanId(
                LOAN_ID, null, 0, LoansQueryServiceImpl.FETCH_ALL_PAGE_SIZE, null))
                .thenReturn(response);

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
        verify(loanTransactionsApi).retrieveTransactionsByLoanId(
                LOAN_ID, null, 0, LoansQueryServiceImpl.FETCH_ALL_PAGE_SIZE, null);
    }

    @Test
    void listTransactionsDateFilteredSlicesRequestedPage() {
        Jwt jwt = jwt();
        GetLoansLoanIdTransactionsResponse response = new GetLoansLoanIdTransactionsResponse()
                .content(List.of(
                        transactionOn(2L, LocalDate.of(2026, 1, 5)),
                        transactionOn(3L, LocalDate.of(2026, 1, 10)),
                        transactionOn(4L, LocalDate.of(2026, 1, 15))));
        when(loanTransactionsApi.retrieveTransactionsByLoanId(
                LOAN_ID, null, 0, LoansQueryServiceImpl.FETCH_ALL_PAGE_SIZE, null))
                .thenReturn(response);

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
        when(loanTransactionsApi.retrieveTransactionsByLoanId(
                LOAN_ID, null, 0, LoansQueryServiceImpl.FETCH_ALL_PAGE_SIZE, null))
                .thenReturn(response);

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

        verify(loanTransactionsApi, never()).retrieveTransactionsByLoanId(any(), any(), any(), any(), any());
    }

    private static GetLoansLoanIdTransactionsTransactionIdResponse transactionOn(Long id, LocalDate date) {
        return new GetLoansLoanIdTransactionsTransactionIdResponse().id(id).date(date);
    }
}
