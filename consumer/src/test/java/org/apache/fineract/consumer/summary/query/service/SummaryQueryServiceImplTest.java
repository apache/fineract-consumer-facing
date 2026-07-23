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

package org.apache.fineract.consumer.summary.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessKycRequiredException;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccountsStatus;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoansAccountsCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccountsCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccountsStatus;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.summary.query.data.AccountsSummaryQueryData;
import org.apache.fineract.consumer.summary.query.data.LoanSummaryItemQueryData;
import org.apache.fineract.consumer.summary.query.data.SavingsSummaryItemQueryData;
import org.apache.fineract.consumer.summary.query.exception.SummaryUpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class SummaryQueryServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long CLIENT_ID = 11L;
    private static final Long SAVINGS_ACCOUNT_ID = 42L;
    private static final String SAVINGS_ACCOUNT_NO = "000000042";
    private static final String SAVINGS_PRODUCT_NAME = "Regular Savings";
    private static final String SAVINGS_STATUS = "Active";
    private static final String CURRENCY_CODE = "USD";
    private static final String SAVINGS_BALANCE = "1500.50";
    private static final String SAVINGS_AVAILABLE_BALANCE = "1400.25";
    private static final Long LOAN_ACCOUNT_ID = 7L;
    private static final String LOAN_ACCOUNT_NO = "000000007";
    private static final String LOAN_PRODUCT_NAME = "Personal Loan";
    private static final String LOAN_STATUS_CODE = "loanStatusType.active";
    private static final String LOAN_BALANCE = "9000.00";
    private static final String LOAN_AMOUNT_PAID = "1000.00";

    @Mock
    private ClientApi clientApi;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private UserClientResolver userClientResolver;

    @InjectMocks
    private SummaryQueryServiceImpl service;

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .claim("scope", "read")
                .build();
    }

    @Test
    void getAccountsSummaryMapsSavingsAndLoans() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        GetClientsSavingsAccounts savings = new GetClientsSavingsAccounts()
                .id(SAVINGS_ACCOUNT_ID)
                .accountNo(SAVINGS_ACCOUNT_NO)
                .productName(SAVINGS_PRODUCT_NAME)
                .status(new GetClientsSavingsAccountsStatus().value(SAVINGS_STATUS))
                .currency(new GetClientsSavingsAccountsCurrency().code(CURRENCY_CODE))
                .accountBalance(new BigDecimal(SAVINGS_BALANCE))
                .availableBalance(new BigDecimal(SAVINGS_AVAILABLE_BALANCE));
        GetClientsLoanAccounts loan = new GetClientsLoanAccounts()
                .id(LOAN_ACCOUNT_ID)
                .accountNo(LOAN_ACCOUNT_NO)
                .productName(LOAN_PRODUCT_NAME)
                .status(new GetClientsLoanAccountsStatus().code(LOAN_STATUS_CODE))
                .currency(new GetClientsLoansAccountsCurrency().code(CURRENCY_CODE))
                .loanBalance(new BigDecimal(LOAN_BALANCE))
                .amountPaid(new BigDecimal(LOAN_AMOUNT_PAID));
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse()
                        .savingsAccounts(Set.of(savings))
                        .loanAccounts(Set.of(loan)));

        AccountsSummaryQueryData result = service.getAccountsSummary(jwt);

        assertThat(result.getSavings()).hasSize(1);
        SavingsSummaryItemQueryData savingsItem = result.getSavings().get(0);
        assertThat(savingsItem.getId()).isEqualTo(SAVINGS_ACCOUNT_ID);
        assertThat(savingsItem.getAccountNo()).isEqualTo(SAVINGS_ACCOUNT_NO);
        assertThat(savingsItem.getProductName()).isEqualTo(SAVINGS_PRODUCT_NAME);
        assertThat(savingsItem.getStatus()).isEqualTo(SAVINGS_STATUS);
        assertThat(savingsItem.getCurrency()).isEqualTo(CURRENCY_CODE);
        assertThat(savingsItem.getAccountBalance()).isEqualByComparingTo(SAVINGS_BALANCE);
        assertThat(savingsItem.getAvailableBalance()).isEqualByComparingTo(SAVINGS_AVAILABLE_BALANCE);

        assertThat(result.getLoans()).hasSize(1);
        LoanSummaryItemQueryData loanItem = result.getLoans().get(0);
        assertThat(loanItem.getId()).isEqualTo(LOAN_ACCOUNT_ID);
        assertThat(loanItem.getAccountNo()).isEqualTo(LOAN_ACCOUNT_NO);
        assertThat(loanItem.getProductName()).isEqualTo(LOAN_PRODUCT_NAME);
        assertThat(loanItem.getStatus()).isEqualTo(LOAN_STATUS_CODE);
        assertThat(loanItem.getCurrency()).isEqualTo(CURRENCY_CODE);
        assertThat(loanItem.getLoanBalance()).isEqualByComparingTo(LOAN_BALANCE);
        assertThat(loanItem.getAmountPaid()).isEqualByComparingTo(LOAN_AMOUNT_PAID);

        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.SUMMARY_VIEW);
    }

    @Test
    void getAccountsSummarySortsAccountsById() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse()
                        .savingsAccounts(Set.of(
                                new GetClientsSavingsAccounts().id(9L),
                                new GetClientsSavingsAccounts().id(3L),
                                new GetClientsSavingsAccounts().id(5L))));

        AccountsSummaryQueryData result = service.getAccountsSummary(jwt);

        assertThat(result.getSavings())
                .extracting(SavingsSummaryItemQueryData::getId)
                .containsExactly(3L, 5L, 9L);
    }

    @Test
    void getAccountsSummaryEmptyWhenUpstreamHasNoAccounts() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse());

        AccountsSummaryQueryData result = service.getAccountsSummary(jwt);

        assertThat(result.getSavings()).isEmpty();
        assertThat(result.getLoans()).isEmpty();
    }

    @Test
    void getAccountsSummaryTranslatesUpstreamFailure() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> service.getAccountsSummary(jwt))
                .isInstanceOf(SummaryUpstreamUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", SummaryUpstreamUnavailableException.CODE);
    }

    @Test
    void getAccountsSummaryDeniedWhenAccessPolicyRejects() {
        Jwt jwt = jwt();
        doThrow(new AccessKycRequiredException())
                .when(accessPolicyEvaluator).authorize(jwt, ConsumerAction.SUMMARY_VIEW);

        assertThatThrownBy(() -> service.getAccountsSummary(jwt))
                .isInstanceOf(AccessKycRequiredException.class);
        verifyNoInteractions(clientApi);
    }
}
