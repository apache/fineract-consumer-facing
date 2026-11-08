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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.infrastructure.fineractclient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Set;
import org.apache.fineract.consumer.infrastructure.fineractclient.data.LoanPayoffData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.AccountTransfersApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.AccountTransferRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetAccountTransfersPageItems;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetAccountTransfersResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdStatus;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsTemplateResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostAccountTransfersResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferFineractClientImplTest {

    private static final Long CLIENT_ID = 10L;
    private static final Long OFFICE_ID = 2L;
    private static final Long SAVINGS_ID = 20L;
    private static final Long LOAN_ID = 30L;
    private static final Long TRANSFER_ID = 100L;
    private static final Long TRANSFER_DETAIL_ID = 104L;

    @Mock
    private ClientApi clientApi;

    @Mock
    private SavingsAccountApi savingsAccountApi;

    @Mock
    private AccountTransfersApi accountTransfersApi;

    @Mock
    private LoansApi loansApi;

    @Mock
    private LoanTransactionsApi loanTransactionsApi;

    @InjectMocks
    private TransferFineractClientImpl client;

    @Test
    void getClientOfficeIdRetrievesOfficeId() {
        when(clientApi.retrieveOneClient(CLIENT_ID, false))
                .thenReturn(new GetClientsClientIdResponse().officeId(OFFICE_ID));

        Long officeId = client.getClientOfficeId(CLIENT_ID);

        assertThat(officeId).isEqualTo(OFFICE_ID);
        verify(clientApi).retrieveOneClient(CLIENT_ID, false);
    }

    @Test
    void getClientOfficeIdReturnsNullWhenResponseNull() {
        when(clientApi.retrieveOneClient(CLIENT_ID, false)).thenReturn(null);

        Long officeId = client.getClientOfficeId(CLIENT_ID);

        assertThat(officeId).isNull();
    }

    @Test
    void getSavingsAccountClientIdRetrievesClientId() {
        when(savingsAccountApi.retrieveSavingsAccount(SAVINGS_ID, null, null, null))
                .thenReturn(new SavingsAccountData().clientId(CLIENT_ID));

        Long clientId = client.getSavingsAccountClientId(SAVINGS_ID);

        assertThat(clientId).isEqualTo(CLIENT_ID);
        verify(savingsAccountApi).retrieveSavingsAccount(SAVINGS_ID, null, null, null);
    }

    @Test
    void getSavingsAccountClientIdReturnsNullWhenResponseNull() {
        when(savingsAccountApi.retrieveSavingsAccount(SAVINGS_ID, null, null, null)).thenReturn(null);

        Long clientId = client.getSavingsAccountClientId(SAVINGS_ID);

        assertThat(clientId).isNull();
    }

    @Test
    void createAccountTransferDelegatesAndReturnsResourceId() {
        AccountTransferRequest request = new AccountTransferRequest();
        when(accountTransfersApi.createAccountTransfer(request))
                .thenReturn(new PostAccountTransfersResponse().resourceId(TRANSFER_ID));

        Long resourceId = client.createAccountTransfer(request);

        assertThat(resourceId).isEqualTo(TRANSFER_ID);
        verify(accountTransfersApi).createAccountTransfer(request);
    }

    @Test
    void createAccountTransferReturnsNullWhenResponseNull() {
        AccountTransferRequest request = new AccountTransferRequest();
        when(accountTransfersApi.createAccountTransfer(request)).thenReturn(null);

        Long resourceId = client.createAccountTransfer(request);

        assertThat(resourceId).isNull();
    }

    @Test
    void getLoanPayoffReturnsPayoffAmountForActiveLoan() {
        when(loansApi.retrieveOneLoan(LOAN_ID, false, "", null, null))
                .thenReturn(new GetLoansLoanIdResponse().status(new GetLoansLoanIdStatus().active(true)));
        when(loanTransactionsApi.retrieveTemplateLoanTransaction(LOAN_ID, "prepayLoan", null, null, null, null))
                .thenReturn(new GetLoansLoanIdTransactionsTemplateResponse().amount(new BigDecimal("250.75")));

        LoanPayoffData payoff = client.getLoanPayoff(LOAN_ID);

        assertThat(payoff.isActive()).isTrue();
        assertThat(payoff.getPayoffAmount()).isEqualByComparingTo(new BigDecimal("250.75"));
        verify(loanTransactionsApi).retrieveTemplateLoanTransaction(LOAN_ID, "prepayLoan", null, null, null, null);
    }

    @Test
    void getLoanPayoffReturnsInactiveWithoutCallingTemplateWhenLoanNotActive() {
        when(loansApi.retrieveOneLoan(LOAN_ID, false, "", null, null))
                .thenReturn(new GetLoansLoanIdResponse().status(new GetLoansLoanIdStatus().active(false)));

        LoanPayoffData payoff = client.getLoanPayoff(LOAN_ID);

        assertThat(payoff.isActive()).isFalse();
        assertThat(payoff.getPayoffAmount()).isEqualTo(BigDecimal.ZERO);
        verify(loanTransactionsApi, never()).retrieveTemplateLoanTransaction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getLoanPayoffHandlesNullStatusOrNullLoan() {
        when(loansApi.retrieveOneLoan(LOAN_ID, false, "", null, null)).thenReturn(null);

        LoanPayoffData payoff = client.getLoanPayoff(LOAN_ID);

        assertThat(payoff.isActive()).isFalse();
        assertThat(payoff.getPayoffAmount()).isEqualTo(BigDecimal.ZERO);
        verify(loanTransactionsApi, never()).retrieveTemplateLoanTransaction(any(), any(), any(), any(), any(), any());
    }

    @Test
    void retrieveAllClientAccountsDelegatesToGeneratedApi() {
        GetClientsClientIdAccountsResponse expected = new GetClientsClientIdAccountsResponse();
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenReturn(expected);

        GetClientsClientIdAccountsResponse result = client.retrieveAllClientAccounts(CLIENT_ID);

        assertThat(result).isSameAs(expected);
        verify(clientApi).retrieveAllClientAccounts(CLIENT_ID);
    }

    @Test
    void retrieveSavingsAccountDelegatesToGeneratedApi() {
        SavingsAccountData expected = new SavingsAccountData();
        when(savingsAccountApi.retrieveSavingsAccount(SAVINGS_ID, null, null, "transactions")).thenReturn(expected);

        SavingsAccountData result = client.retrieveSavingsAccount(SAVINGS_ID, null, null, "transactions");

        assertThat(result).isSameAs(expected);
        verify(savingsAccountApi).retrieveSavingsAccount(SAVINGS_ID, null, null, "transactions");
    }

    @Test
    void retrieveOneAccountTransferDelegatesToGeneratedApi() {
        GetAccountTransfersPageItems expected = new GetAccountTransfersPageItems();
        when(accountTransfersApi.retrieveOneAccountTransfer(TRANSFER_ID)).thenReturn(expected);

        GetAccountTransfersPageItems result = client.retrieveOneAccountTransfer(TRANSFER_ID);

        assertThat(result).isSameAs(expected);
        verify(accountTransfersApi).retrieveOneAccountTransfer(TRANSFER_ID);
    }

    @Test
    void getAccountTransferIdReadsTheOneTransferUnderTheDetail() {
        when(accountTransfersApi.retrieveAllAccountTransfers(null, null, null, null, null, TRANSFER_DETAIL_ID))
                .thenReturn(new GetAccountTransfersResponse()
                        .pageItems(Set.of(new GetAccountTransfersPageItems().id(TRANSFER_ID))));

        assertThat(client.getAccountTransferId(TRANSFER_DETAIL_ID)).isEqualTo(TRANSFER_ID);
    }

    @Test
    void getAccountTransferIdReturnsNullUnlessExactlyOneTransferMatches() {
        when(accountTransfersApi.retrieveAllAccountTransfers(null, null, null, null, null, TRANSFER_DETAIL_ID))
                .thenReturn(new GetAccountTransfersResponse().pageItems(Set.of()))
                .thenReturn(new GetAccountTransfersResponse().pageItems(Set.of(
                        new GetAccountTransfersPageItems().id(TRANSFER_ID),
                        new GetAccountTransfersPageItems().id(TRANSFER_ID + 1))))
                .thenReturn(null);

        assertThat(client.getAccountTransferId(TRANSFER_DETAIL_ID)).isNull();
        assertThat(client.getAccountTransferId(TRANSFER_DETAIL_ID)).isNull();
        assertThat(client.getAccountTransferId(TRANSFER_DETAIL_ID)).isNull();
    }

    @Test
    void getAccountTransferIdNeverListsUnfilteredTransfers() {
        assertThat(client.getAccountTransferId(null)).isNull();

        verify(accountTransfersApi, never()).retrieveAllAccountTransfers(any(), any(), any(), any(), any(), any());
    }
}