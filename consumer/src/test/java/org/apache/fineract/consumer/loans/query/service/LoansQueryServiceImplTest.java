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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.math.BigDecimal;
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
                .status(new GetClientsLoanAccountsStatus().code("loanStatusType.active"))
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
        assertThat(item.getCurrency()).isEqualTo("USD");
        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.LOANS_LIST);
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
    void listTransactionsMapsPageContent() {
        Jwt jwt = jwt();
        GetLoansLoanIdTransactionsResponse response = new GetLoansLoanIdTransactionsResponse()
                .content(List.of(
                        new GetLoansLoanIdTransactionsTransactionIdResponse().id(1L).amount(BigDecimal.valueOf(100.0)),
                        new GetLoansLoanIdTransactionsTransactionIdResponse().id(2L).amount(BigDecimal.valueOf(50.0))));
        when(loanTransactionsApi.retrieveTransactionsByLoanId(LOAN_ID, null, 0, 20, "date,desc"))
                .thenReturn(response);

        LoanTransactionListQuery query = LoanTransactionListQuery.builder()
                .loanId(LOAN_ID)
                .page(0)
                .size(20)
                .sort("date,desc")
                .build();
        List<LoanTransactionQueryData> result = service.listTransactions(jwt, query);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.LOANS_VIEW, LOAN_ID);
    }

    @Test
    void listTransactionsEmptyWhenNoContent() {
        Jwt jwt = jwt();
        when(loanTransactionsApi.retrieveTransactionsByLoanId(LOAN_ID, null, null, null, null))
                .thenReturn(new GetLoansLoanIdTransactionsResponse());

        LoanTransactionListQuery query = LoanTransactionListQuery.builder().loanId(LOAN_ID).build();

        assertThat(service.listTransactions(jwt, query)).isEmpty();
    }

    @Test
    void listTransactionsDeniedWhenAccessPolicyRejects() {
        Jwt jwt = jwt();
        doThrow(new LoanQueryAccessDeniedException())
                .when(accessPolicyEvaluator).authorize(jwt, ConsumerAction.LOANS_VIEW, LOAN_ID);

        LoanTransactionListQuery query = LoanTransactionListQuery.builder().loanId(LOAN_ID).build();

        assertThatThrownBy(() -> service.listTransactions(jwt, query))
                .isInstanceOf(LoanQueryAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", LoanQueryAccessDeniedException.CODE);

        verify(loanTransactionsApi, never()).retrieveTransactionsByLoanId(LOAN_ID, null, null, null, null);
    }
}
