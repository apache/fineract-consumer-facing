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

package org.apache.fineract.consumer.openbanking.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.apache.fineract.consumer.loans.query.data.LoanAccountListItemQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanAccountQueryData;
import org.apache.fineract.consumer.loans.query.service.LoansQueryService;
import org.apache.fineract.consumer.openbanking.query.data.ConsentStatus;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingAccountQueryData;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingBalanceQueryData;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingPermission;
import org.apache.fineract.consumer.openbanking.query.domain.OpenBankingAccountId;
import org.apache.fineract.consumer.openbanking.query.domain.OpenBankingConsentQueryEntity;
import org.apache.fineract.consumer.openbanking.query.exception.OpenBankingAccountNotFoundException;
import org.apache.fineract.consumer.openbanking.query.exception.OpenBankingConsentInvalidException;
import org.apache.fineract.consumer.openbanking.query.repository.OpenBankingConsentQueryRepository;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountListItemQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountQueryData;
import org.apache.fineract.consumer.savings.query.service.SavingsQueryService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class OpenBankingQueryServiceImplTest {

    private static final String TPP_CLIENT_ID = "demo-tpp";
    private static final UUID CONSENT_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000002");
    private static final UUID OTHER_USER_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000003");
    private static final Instant FUTURE = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Instant PAST = Instant.now().minus(1, ChronoUnit.MINUTES);
    private static final Long SAVINGS_ID = 21L;
    private static final Long LOAN_ID = 31L;
    private static final String CURRENCY = "USD";
    private static final String CLOSING_BOOKED = "ClosingBooked";
    private static final String INTERIM_AVAILABLE = "InterimAvailable";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final BigDecimal SAVINGS_BALANCE = new BigDecimal("150.00");

    @Mock
    private OpenBankingConsentQueryRepository openBankingConsentQueryRepository;

    @Mock
    private SavingsQueryService savingsQueryService;

    @Mock
    private LoansQueryService loansQueryService;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @InjectMocks
    private OpenBankingQueryServiceImpl service;

    @Test
    void listAccountsMergesSavingsAndLoansUnderCompositeIds() {
        stubValidConsent(EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC));
        when(savingsQueryService.listAccounts(any(Jwt.class))).thenReturn(List.of(savingsListItem()));
        when(loansQueryService.listAccounts(any(Jwt.class))).thenReturn(List.of(loanListItem()));

        List<OpenBankingAccountQueryData> accounts = service.listAccounts(jwt());

        verify(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.OPENBANKING_ACCOUNTS_VIEW));
        assertThat(accounts).containsExactly(
                OpenBankingAccountQueryData.builder()
                        .accountId(OpenBankingAccountId.compose(OpenBankingAccountId.Type.SAVINGS, SAVINGS_ID))
                        .accountType(OpenBankingAccountId.Type.SAVINGS.getPrefix())
                        .accountNo("000000021")
                        .productName("Everyday Savings")
                        .status(ACTIVE_STATUS)
                        .active(true)
                        .currency(CURRENCY)
                        .build(),
                OpenBankingAccountQueryData.builder()
                        .accountId(OpenBankingAccountId.compose(OpenBankingAccountId.Type.LOAN, LOAN_ID))
                        .accountType(OpenBankingAccountId.Type.LOAN.getPrefix())
                        .accountNo("000000031")
                        .productName("Personal Loan")
                        .status(ACTIVE_STATUS)
                        .active(false)
                        .currency(CURRENCY)
                        .build());
    }

    @Test
    void tokenWithoutConsentIdClaimIsRejectedBeforeAnyLookup() {
        assertConsentInvalid(() -> service.listAccounts(jwtWithoutConsentId()));
        verify(openBankingConsentQueryRepository, never()).findById(any());
    }

    @Test
    void malformedConsentIdClaimIsRejected() {
        assertConsentInvalid(() -> service.listAccounts(jwtWithConsentClaim("not-a-uuid")));
        verify(openBankingConsentQueryRepository, never()).findById(any());
    }

    @Test
    void unknownConsentIsRejected() {
        when(openBankingConsentQueryRepository.findById(CONSENT_ID)).thenReturn(Optional.empty());

        assertConsentInvalid(() -> service.listAccounts(jwt()));
    }

    @Test
    void expiredConsentIsRejected() {
        when(openBankingConsentQueryRepository.findById(CONSENT_ID)).thenReturn(Optional.of(consent(
                ConsentStatus.AUTHORISED, PAST, EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC),
                USER_ID, TPP_CLIENT_ID)));

        assertConsentInvalid(() -> service.listAccounts(jwt()));
    }

    @Test
    void revokedConsentIsRejected() {
        when(openBankingConsentQueryRepository.findById(CONSENT_ID)).thenReturn(Optional.of(consent(
                ConsentStatus.REVOKED, FUTURE, EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC),
                USER_ID, TPP_CLIENT_ID)));

        assertConsentInvalid(() -> service.listAccounts(jwt()));
    }

    @Test
    void anotherTppClientsConsentIsRejected() {
        when(openBankingConsentQueryRepository.findById(CONSENT_ID)).thenReturn(Optional.of(consent(
                ConsentStatus.AUTHORISED, FUTURE, EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC),
                USER_ID, "other-tpp")));

        assertConsentInvalid(() -> service.listAccounts(jwt()));
    }

    @Test
    void anotherUsersConsentIsRejected() {
        when(openBankingConsentQueryRepository.findById(CONSENT_ID)).thenReturn(Optional.of(consent(
                ConsentStatus.AUTHORISED, FUTURE, EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC),
                OTHER_USER_ID, TPP_CLIENT_ID)));

        assertConsentInvalid(() -> service.listAccounts(jwt()));
    }

    @Test
    void balancesRequireTheReadBalancesPermission() {
        stubValidConsent(EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC));

        assertConsentInvalid(() -> service.getBalances(jwt(),
                OpenBankingAccountId.compose(OpenBankingAccountId.Type.SAVINGS, SAVINGS_ID)));
    }

    @Test
    void abacDenialPropagatesAfterConsentCheck() {
        stubValidConsent(EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC));
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(any(Jwt.class), any(ConsumerAction.class));

        assertThatThrownBy(() -> service.listAccounts(jwt()))
                .isInstanceOf(AccessScopeInsufficientException.class);
        verify(savingsQueryService, never()).listAccounts(any(Jwt.class));
    }

    @Test
    void savingsBalancesMapClosingBookedAndInterimAvailable() {
        stubValidConsent(EnumSet.allOf(OpenBankingPermission.class));
        when(savingsQueryService.listAccounts(any(Jwt.class))).thenReturn(List.of(savingsListItem()));
        when(savingsQueryService.getAccount(any(Jwt.class), eq(SAVINGS_ID)))
                .thenReturn(savingsAccount(SAVINGS_BALANCE, new BigDecimal("120.00")));

        List<OpenBankingBalanceQueryData> balances = service.getBalances(jwt(),
                OpenBankingAccountId.compose(OpenBankingAccountId.Type.SAVINGS, SAVINGS_ID));

        assertThat(balances).containsExactly(
                balance(OpenBankingAccountId.Type.SAVINGS, SAVINGS_ID, CLOSING_BOOKED, SAVINGS_BALANCE),
                balance(OpenBankingAccountId.Type.SAVINGS, SAVINGS_ID, INTERIM_AVAILABLE, new BigDecimal("120.00")));
    }

    @Test
    void savingsBalancesSkipNullAvailableBalance() {
        stubValidConsent(EnumSet.allOf(OpenBankingPermission.class));
        when(savingsQueryService.listAccounts(any(Jwt.class))).thenReturn(List.of(savingsListItem()));
        when(savingsQueryService.getAccount(any(Jwt.class), eq(SAVINGS_ID)))
                .thenReturn(savingsAccount(SAVINGS_BALANCE, null));

        List<OpenBankingBalanceQueryData> balances = service.getBalances(jwt(),
                OpenBankingAccountId.compose(OpenBankingAccountId.Type.SAVINGS, SAVINGS_ID));

        assertThat(balances).containsExactly(
                balance(OpenBankingAccountId.Type.SAVINGS, SAVINGS_ID, CLOSING_BOOKED, SAVINGS_BALANCE));
    }

    @Test
    void loanBalancesMapClosingBookedFromTotalOutstanding() {
        stubValidConsent(EnumSet.allOf(OpenBankingPermission.class));
        when(loansQueryService.listAccounts(any(Jwt.class))).thenReturn(List.of(loanListItem()));
        when(loansQueryService.getLoan(any(Jwt.class), eq(LOAN_ID)))
                .thenReturn(LoanAccountQueryData.builder()
                        .id(LOAN_ID)
                        .totalOutstanding(new BigDecimal("500.00"))
                        .currency(CURRENCY)
                        .build());

        List<OpenBankingBalanceQueryData> balances = service.getBalances(jwt(),
                OpenBankingAccountId.compose(OpenBankingAccountId.Type.LOAN, LOAN_ID));

        assertThat(balances).containsExactly(
                balance(OpenBankingAccountId.Type.LOAN, LOAN_ID, CLOSING_BOOKED, new BigDecimal("500.00")));
    }

    @Test
    void malformedCompositeAccountIdIsNotFound() {
        stubValidConsent(EnumSet.allOf(OpenBankingPermission.class));

        assertThatThrownBy(() -> service.getBalances(jwt(), "checking-9"))
                .isInstanceOf(OpenBankingAccountNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", OpenBankingAccountNotFoundException.CODE);
    }

    @Test
    void foreignOwnedSavingsAccountIsShapedAsNotFound() {
        stubValidConsent(EnumSet.allOf(OpenBankingPermission.class));
        when(savingsQueryService.listAccounts(any(Jwt.class))).thenReturn(List.of(savingsListItem()));

        assertThatThrownBy(() -> service.getBalances(jwt(),
                OpenBankingAccountId.compose(OpenBankingAccountId.Type.SAVINGS, 999L)))
                .isInstanceOf(OpenBankingAccountNotFoundException.class);
        verify(savingsQueryService, never()).getAccount(any(Jwt.class), anyLong());
    }

    private void assertConsentInvalid(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOf(OpenBankingConsentInvalidException.class)
                .hasFieldOrPropertyWithValue("code", OpenBankingConsentInvalidException.CODE);
        verify(accessPolicyEvaluator, never()).authorize(any(Jwt.class), any(ConsumerAction.class));
        verify(savingsQueryService, never()).listAccounts(any(Jwt.class));
        verify(loansQueryService, never()).listAccounts(any(Jwt.class));
    }

    private void stubValidConsent(Set<OpenBankingPermission> permissions) {
        when(openBankingConsentQueryRepository.findById(CONSENT_ID)).thenReturn(Optional.of(consent(
                ConsentStatus.AUTHORISED, FUTURE, permissions, USER_ID, TPP_CLIENT_ID)));
    }

    private static OpenBankingConsentQueryEntity consent(ConsentStatus status, Instant expiresAt,
            Set<OpenBankingPermission> permissions, UUID userId, String tppClientId) {
        Instant created = Instant.now().minus(1, ChronoUnit.HOURS);
        return OpenBankingConsentQueryEntity.of(CONSENT_ID, tppClientId, userId, permissions, status,
                created, expiresAt, created);
    }

    private static Jwt jwt() {
        return jwtWithConsentClaim(CONSENT_ID.toString());
    }

    private static Jwt jwtWithConsentClaim(String rawConsentId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .audience(List.of(TPP_CLIENT_ID))
                .claim(OAuth2Constants.CONSENT_ID, rawConsentId)
                .build();
    }

    private static Jwt jwtWithoutConsentId() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(TPP_CLIENT_ID)
                .audience(List.of(TPP_CLIENT_ID))
                .build();
    }

    private static SavingsAccountListItemQueryData savingsListItem() {
        return SavingsAccountListItemQueryData.builder()
                .id(SAVINGS_ID)
                .accountNo("000000021")
                .productName("Everyday Savings")
                .status(ACTIVE_STATUS)
                .active(true)
                .currency(CURRENCY)
                .build();
    }

    private static LoanAccountListItemQueryData loanListItem() {
        return LoanAccountListItemQueryData.builder()
                .id(LOAN_ID)
                .accountNo("000000031")
                .productName("Personal Loan")
                .status(ACTIVE_STATUS)
                .active(false)
                .currency(CURRENCY)
                .build();
    }

    private static SavingsAccountQueryData savingsAccount(BigDecimal balance, BigDecimal availableBalance) {
        return SavingsAccountQueryData.builder()
                .id(SAVINGS_ID)
                .balance(balance)
                .availableBalance(availableBalance)
                .build();
    }

    private static OpenBankingBalanceQueryData balance(OpenBankingAccountId.Type type, Long fineractId, String balanceType,
            BigDecimal amount) {
        return OpenBankingBalanceQueryData.builder()
                .accountId(OpenBankingAccountId.compose(type, fineractId))
                .type(balanceType)
                .amount(amount)
                .currency(CURRENCY)
                .build();
    }
}
