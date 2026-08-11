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

package org.apache.fineract.consumer.transfers.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
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
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.AccountTransfersApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.AccountTransferData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.CurrencyData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetAccountTransfersPageItems;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetAccountTransfersPageItemsFromAccount;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetAccountTransfersPageItemsToAccountType;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionEnumData;
import org.apache.fineract.consumer.transfers.query.data.TransferDirection;
import org.apache.fineract.consumer.transfers.query.data.TransferHistoryQuery;
import org.apache.fineract.consumer.transfers.query.data.TransferQueryData;
import org.apache.fineract.consumer.transfers.query.data.TransferQueryResponse;
import org.apache.fineract.consumer.transfers.query.exception.TransferQueryAccessDeniedException;
import org.apache.fineract.consumer.transfers.query.exception.TransferQueryUpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class TransfersQueryServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000003");
    private static final Long CLIENT_ID = 11L;
    private static final Long ACCOUNT_A_ID = 1L;
    private static final Long ACCOUNT_B_ID = 2L;
    private static final String ACCOUNT_A_NO = "SAV-A";
    private static final String ACCOUNT_B_NO = "SAV-B";
    private static final String TRANSACTIONS_ASSOCIATION = "transactions";
    private static final String CURRENCY = "USD";
    private static final Long LOAN_ACCOUNT_TYPE_ID = 1L;
    private static final Long SAVINGS_ACCOUNT_TYPE_ID = 2L;
    private static final Long BENEFICIARY_ACCOUNT_NO = 4L;
    private static final String BENEFICIARY_ACCOUNT_NO_PADDED = "000000004";
    private static final Long OVERLONG_ACCOUNT_NO = 1234567890L;
    private static final int PAGE_SIZE = 10;
    private static final LocalDate DATE_NEWER = LocalDate.of(2026, 8, 10);
    private static final LocalDate DATE_OLDER = LocalDate.of(2026, 8, 1);
    private static final Long TRANSFER_ID = 10L;
    private static final String AMOUNT_100 = "100.00";
    private static final String AMOUNT_50 = "50.00";
    private static final String AMOUNT_20 = "20.00";
    private static final String AMOUNT_10 = "10.00";

    @Mock
    private ClientApi clientApi;

    @Mock
    private SavingsAccountApi savingsAccountApi;

    @Mock
    private AccountTransfersApi accountTransfersApi;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private UserClientResolver userClientResolver;

    @InjectMocks
    private TransfersQueryServiceImpl service;

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .claim("scope", "read")
                .build();
    }

    private static TransferHistoryQuery query(int page, int size, LocalDate fromDate, LocalDate toDate) {
        return TransferHistoryQuery.builder().page(page).size(size).fromDate(fromDate).toDate(toDate).build();
    }

    private static AccountTransferData transfer(Long id, LocalDate date, String amount) {
        return new AccountTransferData()
                .id(id)
                .transferDate(date)
                .transferAmount(new BigDecimal(amount))
                .currency(new CurrencyData().code(CURRENCY));
    }

    private static SavingsAccountTransactionData withdrawalLeg(AccountTransferData transfer) {
        return new SavingsAccountTransactionData()
                .transactionType(new SavingsAccountTransactionEnumData().withdrawal(true))
                .transfer(transfer);
    }

    private static SavingsAccountTransactionData depositLeg(AccountTransferData transfer) {
        return new SavingsAccountTransactionData()
                .transactionType(new SavingsAccountTransactionEnumData().deposit(true))
                .transfer(transfer);
    }

    private static SavingsAccountTransactionData plainDeposit() {
        return new SavingsAccountTransactionData()
                .transactionType(new SavingsAccountTransactionEnumData().deposit(true));
    }

    private void givenAccounts(Jwt jwt, GetClientsSavingsAccounts... accounts) {
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse().savingsAccounts(Set.of(accounts)));
    }

    private void givenTransactions(Long accountId, String accountNo, SavingsAccountTransactionData... transactions) {
        when(savingsAccountApi.retrieveSavingsAccount(accountId, null, null, TRANSACTIONS_ASSOCIATION))
                .thenReturn(new SavingsAccountData().accountNo(accountNo).transactions(List.of(transactions)));
    }

    private static GetClientsSavingsAccounts account(Long id) {
        return new GetClientsSavingsAccounts().id(id);
    }

    private void givenTransferDestinationType(Long transferId, Long accountTypeId) {
        when(accountTransfersApi.retrieveOne6(transferId)).thenReturn(new GetAccountTransfersPageItems()
                .toAccountType(new GetAccountTransfersPageItemsToAccountType().id(accountTypeId)));
    }

    private void givenTransferDestinationAccount(Long transferId, Long destinationAccountNo) {
        when(accountTransfersApi.retrieveOne6(transferId)).thenReturn(new GetAccountTransfersPageItems()
                .toAccountType(new GetAccountTransfersPageItemsToAccountType().id(SAVINGS_ACCOUNT_TYPE_ID))
                .toAccount(new GetAccountTransfersPageItemsFromAccount().accountNo(destinationAccountNo)));
    }

    @Test
    void listTransfersKeepsOnlyTransferLegsAndMapsOutgoing() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID));
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO,
                withdrawalLeg(transfer(TRANSFER_ID, DATE_NEWER, AMOUNT_100)), plainDeposit());

        List<TransferQueryData> result = service.listTransfers(jwt, query(0, 20, null, null)).getContent();

        assertThat(result).hasSize(1);
        TransferQueryData item = result.get(0);
        assertThat(item.getTransferId()).isEqualTo(TRANSFER_ID);
        assertThat(item.getDate()).isEqualTo(DATE_NEWER);
        assertThat(item.getAmount()).isEqualByComparingTo(AMOUNT_100);
        assertThat(item.getCurrency()).isEqualTo(CURRENCY);
        assertThat(item.getFromAccount()).isEqualTo(ACCOUNT_A_NO);
        assertThat(item.getToAccount()).isNull();
        assertThat(item.getDirection()).isEqualTo(TransferDirection.OUTGOING);
        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.TRANSFER_LIST);
        verify(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.SAVINGS_VIEW), eq(ACCOUNT_A_ID), any());
    }

    @Test
    void listTransfersDedupesLegsOfInternalTransferByTransferId() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID), account(ACCOUNT_B_ID));
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO, withdrawalLeg(transfer(TRANSFER_ID, DATE_NEWER, AMOUNT_50)));
        givenTransactions(ACCOUNT_B_ID, ACCOUNT_B_NO, depositLeg(transfer(TRANSFER_ID, DATE_NEWER, AMOUNT_50)));

        List<TransferQueryData> result = service.listTransfers(jwt, query(0, 20, null, null)).getContent();

        assertThat(result).hasSize(1);
        TransferQueryData item = result.get(0);
        assertThat(item.getTransferId()).isEqualTo(TRANSFER_ID);
        assertThat(item.getFromAccount()).isEqualTo(ACCOUNT_A_NO);
        assertThat(item.getToAccount()).isEqualTo(ACCOUNT_B_NO);
        assertThat(item.getDirection()).isEqualTo(TransferDirection.OUTGOING);
    }

    @Test
    void listTransfersPrunesTransfersWhoseDestinationIsALoan() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID));
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO, withdrawalLeg(transfer(TRANSFER_ID, DATE_NEWER, AMOUNT_100)));
        givenTransferDestinationType(TRANSFER_ID, LOAN_ACCOUNT_TYPE_ID);

        assertThat(service.listTransfers(jwt, query(0, 20, null, null)).getContent()).isEmpty();
        verify(accountTransfersApi).retrieveOne6(TRANSFER_ID);
    }

    @Test
    void listTransfersKeepsTransfersToSavingsAccountsOutsideTheClient() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID));
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO, withdrawalLeg(transfer(TRANSFER_ID, DATE_NEWER, AMOUNT_100)));
        givenTransferDestinationAccount(TRANSFER_ID, BENEFICIARY_ACCOUNT_NO);

        List<TransferQueryData> result = service.listTransfers(jwt, query(0, 20, null, null)).getContent();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTransferId()).isEqualTo(TRANSFER_ID);
        assertThat(result.get(0).getFromAccount()).isEqualTo(ACCOUNT_A_NO);
        assertThat(result.get(0).getToAccount()).isEqualTo(BENEFICIARY_ACCOUNT_NO_PADDED);
        assertThat(result.get(0).getDirection()).isEqualTo(TransferDirection.OUTGOING);
        verify(accountTransfersApi).retrieveOne6(TRANSFER_ID);
    }

    @Test
    void listTransfersLeavesAccountNumbersWiderThanThePadUntouched() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID));
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO, withdrawalLeg(transfer(TRANSFER_ID, DATE_NEWER, AMOUNT_100)));
        givenTransferDestinationAccount(TRANSFER_ID, OVERLONG_ACCOUNT_NO);

        List<TransferQueryData> result = service.listTransfers(jwt, query(0, 20, null, null)).getContent();

        assertThat(result.get(0).getToAccount()).isEqualTo(String.valueOf(OVERLONG_ACCOUNT_NO));
    }

    @Test
    void listTransfersKeepsTheRowWhenTheDetailCarriesNoDestinationAccount() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID));
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO, withdrawalLeg(transfer(TRANSFER_ID, DATE_NEWER, AMOUNT_100)));
        givenTransferDestinationType(TRANSFER_ID, SAVINGS_ACCOUNT_TYPE_ID);

        List<TransferQueryData> result = service.listTransfers(jwt, query(0, 20, null, null)).getContent();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getToAccount()).isNull();
    }

    @Test
    void listTransfersKeepsOwnSavingsToSavingsTransferWithoutLookingUpItsDestination() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID), account(ACCOUNT_B_ID));
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO, withdrawalLeg(transfer(TRANSFER_ID, DATE_NEWER, AMOUNT_50)));
        givenTransactions(ACCOUNT_B_ID, ACCOUNT_B_NO, depositLeg(transfer(TRANSFER_ID, DATE_NEWER, AMOUNT_50)));

        List<TransferQueryData> result = service.listTransfers(jwt, query(0, 20, null, null)).getContent();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTransferId()).isEqualTo(TRANSFER_ID);
        verify(accountTransfersApi, never()).retrieveOne6(any());
    }

    @Test
    void listTransfersMarksIncomingWhenOnlyDepositLegIsOwned() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID));
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO, depositLeg(transfer(TRANSFER_ID, DATE_NEWER, "25.00")));

        List<TransferQueryData> result = service.listTransfers(jwt, query(0, 20, null, null)).getContent();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDirection()).isEqualTo(TransferDirection.INCOMING);
        assertThat(result.get(0).getFromAccount()).isNull();
        assertThat(result.get(0).getToAccount()).isEqualTo(ACCOUNT_A_NO);
    }

    @Test
    void listTransfersFiltersByDateRange() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID));
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO,
                withdrawalLeg(transfer(10L, DATE_OLDER, AMOUNT_10)),
                withdrawalLeg(transfer(11L, DATE_NEWER, AMOUNT_20)));

        List<TransferQueryData> result = service.listTransfers(jwt, query(0, 20, DATE_NEWER, null)).getContent();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTransferId()).isEqualTo(11L);
    }

    @Test
    void listTransfersSortsNewestFirstAndPagesInBff() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID));
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO,
                withdrawalLeg(transfer(10L, DATE_OLDER, AMOUNT_10)),
                withdrawalLeg(transfer(11L, DATE_NEWER, AMOUNT_20)),
                withdrawalLeg(transfer(12L, DATE_NEWER.minusDays(2), "30.00")));

        TransferQueryResponse firstPage = service.listTransfers(jwt, query(0, 2, null, null));
        TransferQueryResponse secondPage = service.listTransfers(jwt, query(1, 2, null, null));

        assertThat(firstPage.getContent()).extracting(TransferQueryData::getTransferId).containsExactly(11L, 12L);
        assertThat(firstPage.getPage()).isZero();
        assertThat(firstPage.getSize()).isEqualTo(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3L);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getContent()).extracting(TransferQueryData::getTransferId).containsExactly(10L);
        assertThat(secondPage.getPage()).isEqualTo(1);
        assertThat(secondPage.getTotalElements()).isEqualTo(3L);
    }

    @Test
    void listTransfersReportsASinglePageWhenTheTotalIsAnExactMultipleOfTheSize() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID));
        SavingsAccountTransactionData[] legs = new SavingsAccountTransactionData[PAGE_SIZE];
        for (int i = 0; i < PAGE_SIZE; i++) {
            legs[i] = withdrawalLeg(transfer((long) i, DATE_NEWER.minusDays(i), AMOUNT_10));
        }
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO, legs);

        TransferQueryResponse page = service.listTransfers(jwt, query(0, PAGE_SIZE, null, null));

        assertThat(page.getContent()).hasSize(PAGE_SIZE);
        assertThat(page.getTotalElements()).isEqualTo(PAGE_SIZE);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    void listTransfersReportsNoPagesWhenNothingMatches() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID));
        givenTransactions(ACCOUNT_A_ID, ACCOUNT_A_NO, plainDeposit());

        TransferQueryResponse page = service.listTransfers(jwt, query(0, 20, null, null));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getTotalPages()).isZero();
    }

    @Test
    void listTransfersTranslatesUpstreamFailure() {
        Jwt jwt = jwt();
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> service.listTransfers(jwt, query(0, 20, null, null)))
                .isInstanceOf(TransferQueryUpstreamUnavailableException.class);
    }

    @Test
    void listTransfersDeniedWhenPerAccountCheckRejects() {
        Jwt jwt = jwt();
        givenAccounts(jwt, account(ACCOUNT_A_ID));
        doNothing().when(accessPolicyEvaluator).authorize(jwt, ConsumerAction.TRANSFER_LIST);
        doThrow(new TransferQueryAccessDeniedException())
                .when(accessPolicyEvaluator)
                .authorize(eq(jwt), eq(ConsumerAction.SAVINGS_VIEW), eq(ACCOUNT_A_ID), any());

        assertThatThrownBy(() -> service.listTransfers(jwt, query(0, 20, null, null)))
                .isInstanceOf(TransferQueryAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", TransferQueryAccessDeniedException.CODE);
    }
}
