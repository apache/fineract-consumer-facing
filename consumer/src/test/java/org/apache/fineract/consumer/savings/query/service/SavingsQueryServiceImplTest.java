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

package org.apache.fineract.consumer.savings.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.CurrencyData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetChargesCurrencyResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccountsCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccountsStatus;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsAccountTransactionsPageItem;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsAccountsSavingsAccountIdChargesResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetTransactionsCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionsSearchResponse;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountListItemQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsChargeQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionQueryResponse;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionSearchQuery;
import org.apache.fineract.consumer.savings.query.exception.SavingsQueryAccessDeniedException;
import org.apache.fineract.consumer.savings.query.exception.SavingsUpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SavingsQueryServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long CLIENT_ID = 11L;
    private static final Long SAVINGS_ID = 42L;
    private static final Long TRANSACTION_ID = 7L;
    private static final Integer PAGE = 0;
    private static final Integer SIZE = 20;
    private static final String LOCALE = "en";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String CURRENCY_CODE = "USD";
    private static final String ACCOUNT_NO = "000000042";
    private static final String PRODUCT_NAME = "Regular Savings";
    private static final String STATUS_ACTIVE = "savingsAccountStatusType.active";
    private static final String SORT_BY_AMOUNT = "amount";
    private static final String SORT_ORDER_DESC = "desc";
    private static final Long TOTAL_ELEMENTS = 45L;

    @Mock
    private ClientApi clientApi;

    @Mock
    private SavingsAccountApi savingsAccountApi;

    @Mock
    private SavingsChargesApi savingsChargesApi;

    @Mock
    private SavingsAccountTransactionsApi savingsAccountTransactionsApi;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private UserClientResolver userClientResolver;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SavingsQueryServiceImpl service;

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
        GetClientsSavingsAccounts account = new GetClientsSavingsAccounts()
                .id(42L)
                .accountNo(ACCOUNT_NO)
                .productName(PRODUCT_NAME)
                .status(new GetClientsSavingsAccountsStatus().code(STATUS_ACTIVE).active(true))
                .currency(new GetClientsSavingsAccountsCurrency().code(CURRENCY_CODE));
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse().savingsAccounts(Set.of(account)));

        List<SavingsAccountListItemQueryData> result = service.listAccounts(jwt);

        assertThat(result).hasSize(1);
        SavingsAccountListItemQueryData item = result.get(0);
        assertThat(item.getId()).isEqualTo(42L);
        assertThat(item.getAccountNo()).isEqualTo(ACCOUNT_NO);
        assertThat(item.getProductName()).isEqualTo(PRODUCT_NAME);
        assertThat(item.getStatus()).isEqualTo(STATUS_ACTIVE);
        assertThat(item.isActive()).isTrue();
        assertThat(item.getCurrency()).isEqualTo(CURRENCY_CODE);
        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.SAVINGS_LIST);
    }

    @Test
    void listAccountsEmptyWhenNoSavingsAccounts() {
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
                .isInstanceOf(SavingsUpstreamUnavailableException.class);
    }

    @Test
    void getAccountDeniedWhenAccessPolicyRejects() {
        Jwt jwt = jwt();
        doThrow(new SavingsQueryAccessDeniedException())
                .when(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.SAVINGS_VIEW), eq(SAVINGS_ID), any());

        assertThatThrownBy(() -> service.getAccount(jwt, SAVINGS_ID))
                .isInstanceOf(SavingsQueryAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", SavingsQueryAccessDeniedException.CODE);
    }

    @Test
    void getAccountMapsCurrency() {
        Jwt jwt = jwt();
        when(savingsAccountApi.retrieveSavingsAccount(SAVINGS_ID, null, null, null))
                .thenReturn(new SavingsAccountData()
                        .id(SAVINGS_ID)
                        .accountNo(ACCOUNT_NO)
                        .currency(new CurrencyData().code(CURRENCY_CODE)));

        SavingsAccountQueryData result = service.getAccount(jwt, SAVINGS_ID);

        assertThat(result.getId()).isEqualTo(SAVINGS_ID);
        assertThat(result.getCurrency()).isEqualTo(CURRENCY_CODE);
        assertThat(result.isActive()).isFalse();
    }

    @Test
    void getChargesMapsCurrency() {
        Jwt jwt = jwt();
        when(savingsChargesApi.retrieveAllSavingsAccountCharges(SAVINGS_ID, null))
                .thenReturn(List.of(new GetSavingsAccountsSavingsAccountIdChargesResponse()
                        .id(9L)
                        .name("Withdrawal fee")
                        .currency(new GetChargesCurrencyResponse().code(CURRENCY_CODE))));

        List<SavingsChargeQueryData> result = service.getCharges(jwt, SAVINGS_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrency()).isEqualTo(CURRENCY_CODE);
    }

    @Test
    void getTransactionMapsCurrency() {
        Jwt jwt = jwt();
        String json = "{}";
        when(savingsAccountTransactionsApi.retrieveOneSavingsAccountTransaction(SAVINGS_ID, TRANSACTION_ID))
                .thenReturn(json);
        when(objectMapper.readValue(json, SavingsAccountTransactionData.class))
                .thenReturn(new SavingsAccountTransactionData()
                        .id(TRANSACTION_ID)
                        .currency(new CurrencyData().code(CURRENCY_CODE)));

        SavingsTransactionQueryData result = service.getTransaction(jwt, SAVINGS_ID, TRANSACTION_ID);

        assertThat(result.getId()).isEqualTo(TRANSACTION_ID);
        assertThat(result.getCurrency()).isEqualTo(CURRENCY_CODE);
    }

    @Test
    void searchTransactionsSplitsSortIntoOrderByAndSortOrder() {
        Jwt jwt = jwt();
        SavingsAccountTransactionsSearchResponse response = new SavingsAccountTransactionsSearchResponse()
                .content(Set.of(new GetSavingsAccountTransactionsPageItem()
                        .id(1L)
                        .currency(new GetTransactionsCurrency().code(CURRENCY_CODE))))
                .total(TOTAL_ELEMENTS);
        when(savingsAccountTransactionsApi.searchTransactions(
                SAVINGS_ID, null, null, null, null, null, null, null, null, null,
                0, SIZE, SORT_BY_AMOUNT, SORT_ORDER_DESC, LOCALE, DATE_FORMAT))
                .thenReturn(response);

        SavingsTransactionQueryResponse result = service.searchTransactions(jwt, SavingsTransactionSearchQuery.builder()
                .savingsId(SAVINGS_ID)
                .page(PAGE)
                .size(SIZE)
                .sort(SORT_BY_AMOUNT + "," + SORT_ORDER_DESC)
                .build());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getCurrency()).isEqualTo(CURRENCY_CODE);
        assertThat(result.getPage()).isEqualTo(PAGE);
        assertThat(result.getSize()).isEqualTo(SIZE);
        assertThat(result.getTotalElements()).isEqualTo(TOTAL_ELEMENTS);
        assertThat(result.getTotalPages()).isEqualTo(3);
        verify(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.SAVINGS_VIEW), eq(SAVINGS_ID), any());
    }

    @Test
    void searchTransactionsPassesBareSortAsOrderByOnly() {
        Jwt jwt = jwt();
        when(savingsAccountTransactionsApi.searchTransactions(
                SAVINGS_ID, null, null, null, null, null, null, null, null, null,
                0, SIZE, SORT_BY_AMOUNT, null, LOCALE, DATE_FORMAT))
                .thenReturn(new SavingsAccountTransactionsSearchResponse()
                        .content(Set.of(new GetSavingsAccountTransactionsPageItem().id(1L)))
                        .total(1L));

        SavingsTransactionQueryResponse result = service.searchTransactions(jwt, SavingsTransactionSearchQuery.builder()
                .savingsId(SAVINGS_ID)
                .page(PAGE)
                .size(SIZE)
                .sort(SORT_BY_AMOUNT)
                .build());

        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    void searchTransactionsEmptyEnvelopeWhenNullContent() {
        Jwt jwt = jwt();
        when(savingsAccountTransactionsApi.searchTransactions(
                SAVINGS_ID, null, null, null, null, null, null, null, null, null,
                0, SIZE, null, null, LOCALE, DATE_FORMAT))
                .thenReturn(new SavingsAccountTransactionsSearchResponse());

        SavingsTransactionQueryResponse result = service.searchTransactions(jwt, SavingsTransactionSearchQuery.builder()
                .savingsId(SAVINGS_ID)
                .page(PAGE)
                .size(SIZE)
                .build());

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getPage()).isEqualTo(PAGE);
        assertThat(result.getSize()).isEqualTo(SIZE);
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }
}
