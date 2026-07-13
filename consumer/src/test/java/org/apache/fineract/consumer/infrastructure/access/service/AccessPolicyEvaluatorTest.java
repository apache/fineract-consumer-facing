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

package org.apache.fineract.consumer.infrastructure.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.access.data.ActionPolicies;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.data.ResourceType;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessKycRequiredException;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.infrastructure.web.UserClientResolver;
import org.apache.fineract.consumer.loans.command.exception.LoanCommandAccessDeniedException;
import org.apache.fineract.consumer.loans.query.exception.LoanQueryAccessDeniedException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandAccessDeniedException;
import org.apache.fineract.consumer.savings.query.exception.SavingsQueryAccessDeniedException;
import org.apache.fineract.consumer.transfers.command.exception.TransferAccessDeniedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AccessPolicyEvaluatorTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long CLIENT_ID = 42L;
    private static final Long OWNED_SAVINGS_ID = 7L;
    private static final Long OWNED_LOAN_ID = 77L;
    private static final Long FOREIGN_ACCOUNT_ID = 999L;

    @Mock
    private ClientApi clientApi;

    @Mock
    private UserClientResolver userClientResolver;

    @InjectMocks
    private AccessPolicyEvaluator evaluator;

    @Test
    void everyActionHasAPolicyEntry() {
        for (ConsumerAction action : ConsumerAction.values()) {
            assertThat(ActionPolicies.forAction(action))
                    .as("policy table entry for %s", action)
                    .isPresent();
        }
    }

    @Test
    void authorizePassesWhenScopeKycAndOwnershipSatisfied() {
        stubOwnedAccounts();

        assertThatCode(() -> evaluator.authorize(validJwt(), ConsumerAction.SAVINGS_VIEW, OWNED_SAVINGS_ID))
                .doesNotThrowAnyException();
    }

    @Test
    void authorizeRejectsWrongScopeBeforeAnyOwnershipLookup() {
        Jwt jwt = jwt("openbanking:read", true);

        assertThatThrownBy(() -> evaluator.authorize(jwt, ConsumerAction.SAVINGS_VIEW, OWNED_SAVINGS_ID))
                .isInstanceOf(AccessScopeInsufficientException.class)
                .hasFieldOrPropertyWithValue("code", AccessScopeInsufficientException.CODE);
        verify(clientApi, never()).retrieveAllClientAccounts(anyLong());
    }

    @Test
    void authorizeRejectsMissingScopeClaim() {
        Jwt jwt = jwt(null, true);

        assertThatThrownBy(() -> evaluator.authorize(jwt, ConsumerAction.LOANS_VIEW, OWNED_LOAN_ID))
                .isInstanceOf(AccessScopeInsufficientException.class);
    }

    @Test
    void authorizeRejectsKycNotVerified() {
        Jwt jwt = jwt(AuthenticationConstants.SCOPE_CONSUMER_FULL, false);

        assertThatThrownBy(() -> evaluator.authorize(jwt, ConsumerAction.TRANSFER_EXECUTE, OWNED_SAVINGS_ID))
                .isInstanceOf(AccessKycRequiredException.class)
                .hasFieldOrPropertyWithValue("code", AccessKycRequiredException.CODE);
        verify(clientApi, never()).retrieveAllClientAccounts(anyLong());
    }

    @Test
    void authorizeRejectsMissingKycClaim() {
        Jwt jwt = jwt(AuthenticationConstants.SCOPE_CONSUMER_FULL, null);

        assertThatThrownBy(() -> evaluator.authorize(jwt, ConsumerAction.LOANS_VIEW, OWNED_LOAN_ID))
                .isInstanceOf(AccessKycRequiredException.class);
    }

    @Test
    void authorizeDeniesNonOwnedSavingsWithQuerySideException() {
        stubOwnedAccounts();

        assertThatThrownBy(() -> evaluator.authorize(validJwt(), ConsumerAction.SAVINGS_VIEW, FOREIGN_ACCOUNT_ID))
                .isInstanceOf(SavingsQueryAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", SavingsQueryAccessDeniedException.CODE);
    }

    @Test
    void authorizeDeniesNonOwnedSavingsChargeWithCommandSideException() {
        stubOwnedAccounts();

        assertThatThrownBy(() -> evaluator.authorize(validJwt(), ConsumerAction.SAVINGS_CHARGE_PAY, FOREIGN_ACCOUNT_ID))
                .isInstanceOf(SavingsCommandAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", SavingsCommandAccessDeniedException.CODE);
    }

    @Test
    void authorizeDeniesNonOwnedLoanWithQuerySideException() {
        stubOwnedAccounts();

        assertThatThrownBy(() -> evaluator.authorize(validJwt(), ConsumerAction.LOANS_VIEW, FOREIGN_ACCOUNT_ID))
                .isInstanceOf(LoanQueryAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", LoanQueryAccessDeniedException.CODE);
    }

    @Test
    void authorizeDeniesNonOwnedLoanChargeWithCommandSideException() {
        stubOwnedAccounts();

        assertThatThrownBy(() -> evaluator.authorize(validJwt(), ConsumerAction.LOAN_CHARGE_PAY, FOREIGN_ACCOUNT_ID))
                .isInstanceOf(LoanCommandAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", LoanCommandAccessDeniedException.CODE);
    }

    @Test
    void authorizeDeniesNonOwnedTransferSource() {
        stubOwnedAccounts();

        assertThatThrownBy(() -> evaluator.authorize(validJwt(), ConsumerAction.TRANSFER_EXECUTE, FOREIGN_ACCOUNT_ID))
                .isInstanceOf(TransferAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", TransferAccessDeniedException.CODE);
    }

    @Test
    void authorizePassesUnownedActionWithoutOwnershipLookup() {
        assertThatCode(() -> evaluator.authorize(validJwt(), ConsumerAction.BENEFICIARY_ADD))
                .doesNotThrowAnyException();
        verify(clientApi, never()).retrieveAllClientAccounts(anyLong());
    }

    @Test
    void authorizeRejectsWrongScopeOnUnownedAction() {
        Jwt jwt = jwt("openbanking:read", true);

        assertThatThrownBy(() -> evaluator.authorize(jwt, ConsumerAction.BENEFICIARY_ADD))
                .isInstanceOf(AccessScopeInsufficientException.class);
    }

    @Test
    void authorizeRejectsKycNotVerifiedOnUnownedAction() {
        Jwt jwt = jwt(AuthenticationConstants.SCOPE_CONSUMER_FULL, false);

        assertThatThrownBy(() -> evaluator.authorize(jwt, ConsumerAction.BENEFICIARY_ADD))
                .isInstanceOf(AccessKycRequiredException.class);
    }

    @Test
    void authorizeDeniesNullResourceIdWhenOwnershipRequired() {
        when(userClientResolver.resolveClientId(validJwt())).thenReturn(CLIENT_ID);

        assertThatThrownBy(() -> evaluator.authorize(validJwt(), ConsumerAction.SAVINGS_VIEW))
                .isInstanceOf(SavingsQueryAccessDeniedException.class);
        verify(clientApi, never()).retrieveAllClientAccounts(anyLong());
    }

    @Test
    void ownsResourceReturnsTrueForOwnedAccounts() {
        stubOwnedAccounts();

        assertThat(evaluator.ownsResource(validJwt(), ResourceType.SAVINGS, OWNED_SAVINGS_ID)).isTrue();
        assertThat(evaluator.ownsResource(validJwt(), ResourceType.LOANS, OWNED_LOAN_ID)).isTrue();
    }

    @Test
    void ownsResourceReturnsFalseForForeignAccounts() {
        stubOwnedAccounts();

        assertThat(evaluator.ownsResource(validJwt(), ResourceType.SAVINGS, FOREIGN_ACCOUNT_ID)).isFalse();
        assertThat(evaluator.ownsResource(validJwt(), ResourceType.LOANS, FOREIGN_ACCOUNT_ID)).isFalse();
    }

    private void stubOwnedAccounts() {
        when(userClientResolver.resolveClientId(validJwt())).thenReturn(CLIENT_ID);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenReturn(new GetClientsClientIdAccountsResponse()
                .savingsAccounts(Set.of(new GetClientsSavingsAccounts().id(OWNED_SAVINGS_ID)))
                .loanAccounts(Set.of(new GetClientsLoanAccounts().id(OWNED_LOAN_ID))));
    }

    private static Jwt validJwt() {
        return jwt(AuthenticationConstants.SCOPE_CONSUMER_FULL, true);
    }

    private static Jwt jwt(String scope, Boolean kycVerified) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString());
        if (scope != null) {
            builder.claim(JwtClaims.SCOPE, scope);
        }
        if (kycVerified != null) {
            builder.claim(JwtClaims.KYC_VERIFIED, kycVerified);
        }
        return builder.build();
    }
}
