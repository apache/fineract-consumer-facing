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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsProductApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsAccountsSavingsAccountIdChargesResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsProductsProductIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsProductsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionsSearchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsFineractClientImplTest {

    private static final Long CLIENT_ID = 11L;
    private static final Long SAVINGS_ID = 42L;
    private static final Long TRANSACTION_ID = 7L;
    private static final Long PRODUCT_ID = 5L;

    @Mock
    private ClientApi clientApi;

    @Mock
    private SavingsAccountApi savingsAccountApi;

    @Mock
    private SavingsChargesApi savingsChargesApi;

    @Mock
    private SavingsAccountTransactionsApi savingsAccountTransactionsApi;

    @Mock
    private SavingsProductApi savingsProductApi;

    @InjectMocks
    private SavingsFineractClientImpl client;

    @Test
    void listClientSavingsAccountsUnpacksSavingsAccounts() {
        GetClientsSavingsAccounts account = new GetClientsSavingsAccounts().id(SAVINGS_ID);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse().savingsAccounts(Set.of(account)));

        List<GetClientsSavingsAccounts> result = client.listClientSavingsAccounts(CLIENT_ID);

        assertThat(result).containsExactly(account);
        verify(clientApi).retrieveAllClientAccounts(CLIENT_ID);
    }

    @Test
    void listClientSavingsAccountsReturnsEmptyWhenNull() {
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse());

        assertThat(client.listClientSavingsAccounts(CLIENT_ID)).isEmpty();
    }

    @Test
    void getAccountDelegatesToGeneratedApi() {
        SavingsAccountData expected = new SavingsAccountData();
        when(savingsAccountApi.retrieveSavingsAccount(SAVINGS_ID, null, null, null)).thenReturn(expected);

        SavingsAccountData result = client.getAccount(SAVINGS_ID);

        assertThat(result).isSameAs(expected);
        verify(savingsAccountApi).retrieveSavingsAccount(SAVINGS_ID, null, null, null);
    }

    @Test
    void getChargesDelegatesToGeneratedApi() {
        List<GetSavingsAccountsSavingsAccountIdChargesResponse> expected =
                List.of(new GetSavingsAccountsSavingsAccountIdChargesResponse());
        when(savingsChargesApi.retrieveAllSavingsAccountCharges(SAVINGS_ID, null)).thenReturn(expected);

        List<GetSavingsAccountsSavingsAccountIdChargesResponse> result = client.getCharges(SAVINGS_ID);

        assertThat(result).isSameAs(expected);
        verify(savingsChargesApi).retrieveAllSavingsAccountCharges(SAVINGS_ID, null);
    }

    @Test
    void searchTransactionsPassesLocaleDateFormatAndDates() {
        SavingsAccountTransactionsSearchResponse expected = new SavingsAccountTransactionsSearchResponse();
        when(savingsAccountTransactionsApi.searchSavingsAccountTransactions(
                SAVINGS_ID, "2026-01-01", "2026-01-31", null, null, null, null, null, null, null,
                0, 20, "amount", "desc", "en", "yyyy-MM-dd"))
                .thenReturn(expected);

        SavingsAccountTransactionsSearchResponse result = client.searchTransactions(
                SAVINGS_ID, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                0, 20, "amount", "desc");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getTransactionDelegatesToGeneratedApi() {
        SavingsAccountTransactionData expected = new SavingsAccountTransactionData();
        when(savingsAccountTransactionsApi.retrieveOneSavingsAccountTransaction(SAVINGS_ID, TRANSACTION_ID))
                .thenReturn(expected);

        SavingsAccountTransactionData result = client.getTransaction(SAVINGS_ID, TRANSACTION_ID);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void listSavingsProductsDelegatesToGeneratedApi() {
        List<GetSavingsProductsResponse> expected = List.of(new GetSavingsProductsResponse());
        when(savingsProductApi.retrieveAllSavingsProducts()).thenReturn(expected);

        assertThat(client.listSavingsProducts()).isSameAs(expected);
    }

    @Test
    void getSavingsProductDelegatesToGeneratedApi() {
        GetSavingsProductsProductIdResponse expected = new GetSavingsProductsProductIdResponse();
        when(savingsProductApi.retrieveOneSavingsProduct(PRODUCT_ID)).thenReturn(expected);

        assertThat(client.getSavingsProduct(PRODUCT_ID)).isSameAs(expected);
    }
}