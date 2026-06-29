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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.util.List;
import java.util.Set;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccountsStatus;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoansAccountsCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsTransactionIdResponse;
import org.apache.fineract.consumer.infrastructure.web.AccessPolicyEvaluator;
import org.apache.fineract.consumer.loans.query.data.LoanAccountListItemQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanTransactionListQuery;
import org.apache.fineract.consumer.loans.query.data.LoanTransactionQueryData;
import org.apache.fineract.consumer.loans.query.exception.LoanAccessDeniedException;
import org.apache.fineract.consumer.loans.query.exception.LoanUpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoansQueryServiceImplTest {

    private static final Long CLIENT_ID = 11L;
    private static final Long LOAN_ID = 42L;

    @Mock
    private ClientApi clientApi;

    @Mock
    private LoanTransactionsApi loanTransactionsApi;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @InjectMocks
    private LoansQueryServiceImpl service;

    @Test
    void listAccountsMapsIndexFields() {
        GetClientsLoanAccounts account = new GetClientsLoanAccounts()
                .id(77L)
                .accountNo("000000077")
                .productName("Personal Loan")
                .status(new GetClientsLoanAccountsStatus().code("loanStatusType.active"))
                .currency(new GetClientsLoansAccountsCurrency().code("USD"));
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse().loanAccounts(Set.of(account)));

        List<LoanAccountListItemQueryData> result = service.listAccounts(CLIENT_ID);

        assertThat(result).hasSize(1);
        LoanAccountListItemQueryData item = result.get(0);
        assertThat(item.getId()).isEqualTo(77L);
        assertThat(item.getAccountNo()).isEqualTo("000000077");
        assertThat(item.getProductName()).isEqualTo("Personal Loan");
        assertThat(item.getStatus()).isEqualTo("loanStatusType.active");
        assertThat(item.getCurrency()).isEqualTo("USD");
    }

    @Test
    void listAccountsEmptyWhenNoLoanAccounts() {
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse());

        assertThat(service.listAccounts(CLIENT_ID)).isEmpty();
    }

    @Test
    void listAccountsTranslatesUpstreamFailure() {
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> service.listAccounts(CLIENT_ID))
                .isInstanceOf(LoanUpstreamUnavailableException.class);
    }

    @Test
    void listTransactionsMapsPageContent() {
        when(accessPolicyEvaluator.canAccessLoans(CLIENT_ID, LOAN_ID)).thenReturn(true);
        GetLoansLoanIdTransactionsResponse response = new GetLoansLoanIdTransactionsResponse()
                .content(List.of(
                        new GetLoansLoanIdTransactionsTransactionIdResponse().id(1L).amount(100.0),
                        new GetLoansLoanIdTransactionsTransactionIdResponse().id(2L).amount(50.0)));
        when(loanTransactionsApi.retrieveTransactionsByLoanId(LOAN_ID, null, 0, 20, "date,desc"))
                .thenReturn(response);

        LoanTransactionListQuery query = LoanTransactionListQuery.builder()
                .loanId(LOAN_ID)
                .page(0)
                .size(20)
                .sort("date,desc")
                .build();
        List<LoanTransactionQueryData> result = service.listTransactions(CLIENT_ID, query);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }

    @Test
    void listTransactionsEmptyWhenNoContent() {
        when(accessPolicyEvaluator.canAccessLoans(CLIENT_ID, LOAN_ID)).thenReturn(true);
        when(loanTransactionsApi.retrieveTransactionsByLoanId(LOAN_ID, null, null, null, null))
                .thenReturn(new GetLoansLoanIdTransactionsResponse());

        LoanTransactionListQuery query = LoanTransactionListQuery.builder().loanId(LOAN_ID).build();

        assertThat(service.listTransactions(CLIENT_ID, query)).isEmpty();
    }

    @Test
    void listTransactionsDeniedWhenAccessPolicyRejects() {
        when(accessPolicyEvaluator.canAccessLoans(CLIENT_ID, LOAN_ID)).thenReturn(false);

        LoanTransactionListQuery query = LoanTransactionListQuery.builder().loanId(LOAN_ID).build();

        assertThatThrownBy(() -> service.listTransactions(CLIENT_ID, query))
                .isInstanceOf(LoanAccessDeniedException.class);
    }
}
