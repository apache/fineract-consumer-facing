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

package org.apache.fineract.consumer.savings.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.OwnedAccountsCache;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsAccountIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsAccountIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutSavingsAccountsAccountIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutSavingsAccountsAccountIdResponse;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyDeriver;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyHolder;
import org.apache.fineract.consumer.savings.command.data.ModifySavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.SavingsApplicationCommandData;
import org.apache.fineract.consumer.savings.command.data.SubmitSavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.WithdrawSavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandAccessDeniedException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandInProgressException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandUpstreamUnavailableException;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.data.UserStatus;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class SavingsCommandServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long CLIENT_ID = 42L;
    private static final Long SAVINGS_ID = 5L;
    private static final String EMAIL = "user@test.com";
    private static final String IDEMPOTENCY_KEY = "savings-op-key-1";

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private OwnedAccountsCache ownedAccountsCache;

    @Mock
    private IdempotencyKeyHolder idempotencyKeyHolder;

    @Mock
    private SavingsAccountApi savingsAccountApi;

    @InjectMocks
    private SavingsCommandServiceImpl service;

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .claim("scope", "read")
                .build();
    }

    private static UserQueryData user() {
        return UserQueryData.builder()
                .id(1L)
                .publicId(PUBLIC_ID)
                .fineractClientId(CLIENT_ID)
                .email(EMAIL)
                .status(UserStatus.BOUND)
                .build();
    }

    private static SubmitSavingsApplicationCommand submitCommand() {
        return SubmitSavingsApplicationCommand.builder()
                .productId(1L)
                .submittedOnDate(LocalDate.of(2026, 7, 1))
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();
    }

    @Test
    void submitAuthorizesAndMapsFieldsCorrectly() {
        Jwt jwt = jwt();
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(savingsAccountApi.submitSavingsApplication(any()))
                .thenReturn(new PostSavingsAccountsResponse().savingsId(SAVINGS_ID).resourceId(99L));

        SavingsApplicationCommandData data = service.submitApplication(jwt, submitCommand());

        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.SAVINGS_APPLICATION_SUBMIT);
        verify(ownedAccountsCache).evict(CLIENT_ID);
        assertThat(data.getSavingsId()).isEqualTo(SAVINGS_ID);
        assertThat(data.getResourceId()).isEqualTo(99L);
        assertThat(data.getClientId()).isEqualTo(CLIENT_ID);

        ArgumentCaptor<PostSavingsAccountsRequest> captor = ArgumentCaptor.forClass(PostSavingsAccountsRequest.class);
        verify(savingsAccountApi).submitSavingsApplication(captor.capture());
        PostSavingsAccountsRequest captured = captor.getValue();
        assertThat(captured.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(captured.getProductId()).isEqualTo(1L);
        assertThat(captured.getSubmittedOnDate()).isEqualTo("2026-07-01");
        assertThat(captured.getLocale()).isEqualTo("en");
        assertThat(captured.getDateFormat()).isEqualTo("yyyy-MM-dd");
    }

    @Test
    void submitDoesNotEvictOwnershipCacheWhenUpstreamFails() {
        Jwt jwt = jwt();
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(savingsAccountApi.submitSavingsApplication(any()))
                .thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> service.submitApplication(jwt, submitCommand()))
                .isInstanceOf(SavingsCommandUpstreamUnavailableException.class);

        verify(ownedAccountsCache, never()).evict(any());
    }

    @Test
    void submitSetsDerivedIdempotencyKeyBeforeUpstreamCallAndClearsAfter() {
        Jwt jwt = jwt();
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(savingsAccountApi.submitSavingsApplication(any()))
                .thenReturn(new PostSavingsAccountsResponse().savingsId(SAVINGS_ID).resourceId(99L));

        service.submitApplication(jwt, submitCommand());

        InOrder inOrder = inOrder(idempotencyKeyHolder, savingsAccountApi);
        inOrder.verify(idempotencyKeyHolder).set(IdempotencyKeyDeriver.derive(PUBLIC_ID, IDEMPOTENCY_KEY));
        inOrder.verify(savingsAccountApi).submitSavingsApplication(any());
        inOrder.verify(idempotencyKeyHolder).clear();
    }

    @Test
    void submitClearsIdempotencyKeyWhenUpstreamFails() {
        Jwt jwt = jwt();
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(savingsAccountApi.submitSavingsApplication(any()))
                .thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> service.submitApplication(jwt, submitCommand()))
                .isInstanceOf(SavingsCommandUpstreamUnavailableException.class);

        verify(idempotencyKeyHolder).clear();
    }

    @Test
    void submitTranslatesInProgressReplayToSavingsCommandInProgressException() {
        Jwt jwt = jwt();
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        FeignException inProgress = mock(FeignException.class);
        when(inProgress.status()).thenReturn(HttpStatus.TOO_EARLY.value());
        when(savingsAccountApi.submitSavingsApplication(any())).thenThrow(inProgress);

        assertThatThrownBy(() -> service.submitApplication(jwt, submitCommand()))
                .isInstanceOf(SavingsCommandInProgressException.class)
                .hasFieldOrPropertyWithValue("code", SavingsCommandInProgressException.CODE);

        verify(idempotencyKeyHolder).clear();
    }

    @Test
    void submitRejectsWhenAccessDenied() {
        Jwt jwt = jwt();
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(jwt, ConsumerAction.SAVINGS_APPLICATION_SUBMIT);

        assertThatThrownBy(() -> service.submitApplication(jwt, submitCommand()))
                .isInstanceOf(AccessScopeInsufficientException.class);

        verifyNoInteractions(savingsAccountApi);
    }

    @Test
    void modifySetsDerivedIdempotencyKeyAndMapsFieldsCorrectly() {
        Jwt jwt = jwt();
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(savingsAccountApi.updateSavingsAccount(eq(SAVINGS_ID), any(), isNull()))
                .thenReturn(new PutSavingsAccountsAccountIdResponse().resourceId(99L).savingsId(SAVINGS_ID));

        SavingsApplicationCommandData data = service.modifyApplication(jwt, ModifySavingsApplicationCommand.builder()
                .savingsId(SAVINGS_ID)
                .productId(2L)
                .submittedOnDate(LocalDate.of(2026, 7, 2))
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build());

        InOrder inOrder = inOrder(idempotencyKeyHolder, savingsAccountApi);
        inOrder.verify(idempotencyKeyHolder).set(IdempotencyKeyDeriver.derive(PUBLIC_ID, IDEMPOTENCY_KEY));
        inOrder.verify(savingsAccountApi).updateSavingsAccount(eq(SAVINGS_ID), any(), isNull());
        inOrder.verify(idempotencyKeyHolder).clear();

        verify(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.SAVINGS_APPLICATION_MODIFY), eq(SAVINGS_ID), any());

        ArgumentCaptor<PutSavingsAccountsAccountIdRequest> captor = ArgumentCaptor.forClass(PutSavingsAccountsAccountIdRequest.class);
        verify(savingsAccountApi).updateSavingsAccount(eq(SAVINGS_ID), captor.capture(), isNull());
        PutSavingsAccountsAccountIdRequest captured = captor.getValue();
        assertThat(captured.getProductId()).isEqualTo(2L);
        assertThat(captured.getSubmittedOnDate()).isEqualTo("2026-07-02");
        assertThat(captured.getLocale()).isEqualTo("en");
        assertThat(captured.getDateFormat()).isEqualTo("yyyy-MM-dd");

        assertThat(data.getSavingsId()).isEqualTo(SAVINGS_ID);
        assertThat(data.getResourceId()).isEqualTo(99L);
        assertThat(data.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void modifyRejectsWhenAccessDenied() {
        Jwt jwt = jwt();
        doThrow(new SavingsCommandAccessDeniedException())
                .when(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.SAVINGS_APPLICATION_MODIFY), eq(SAVINGS_ID), any());

        ModifySavingsApplicationCommand cmd = ModifySavingsApplicationCommand.builder()
                .savingsId(SAVINGS_ID)
                .productId(2L)
                .submittedOnDate(LocalDate.of(2026, 7, 2))
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();

        assertThatThrownBy(() -> service.modifyApplication(jwt, cmd))
                .isInstanceOf(SavingsCommandAccessDeniedException.class);

        verifyNoInteractions(savingsAccountApi);
    }

    @Test
    void withdrawSetsDerivedIdempotencyKeyAndMapsFieldsCorrectly() {
        Jwt jwt = jwt();
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(savingsAccountApi.handleCommandsSavingsAccount(eq(SAVINGS_ID), any(), eq(SavingsCommandService.WITHDRAW_COMMAND)))
                .thenReturn(new PostSavingsAccountsAccountIdResponse().resourceId(99L));

        SavingsApplicationCommandData data = service.withdrawApplication(jwt, WithdrawSavingsApplicationCommand.builder()
                .savingsId(SAVINGS_ID)
                .withdrawnOnDate(LocalDate.of(2026, 7, 3))
                .note("Not needed")
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build());

        InOrder inOrder = inOrder(idempotencyKeyHolder, savingsAccountApi);
        inOrder.verify(idempotencyKeyHolder).set(IdempotencyKeyDeriver.derive(PUBLIC_ID, IDEMPOTENCY_KEY));
        inOrder.verify(savingsAccountApi).handleCommandsSavingsAccount(eq(SAVINGS_ID), any(), eq(SavingsCommandService.WITHDRAW_COMMAND));
        inOrder.verify(idempotencyKeyHolder).clear();

        verify(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.SAVINGS_APPLICATION_WITHDRAW), eq(SAVINGS_ID), any());

        ArgumentCaptor<PostSavingsAccountsAccountIdRequest> captor = ArgumentCaptor.forClass(PostSavingsAccountsAccountIdRequest.class);
        verify(savingsAccountApi).handleCommandsSavingsAccount(eq(SAVINGS_ID), captor.capture(), eq(SavingsCommandService.WITHDRAW_COMMAND));
        PostSavingsAccountsAccountIdRequest captured = captor.getValue();
        assertThat(captured.getWithdrawnOnDate()).isEqualTo("2026-07-03");
        assertThat(captured.getNote()).isEqualTo("Not needed");
        assertThat(captured.getLocale()).isEqualTo("en");
        assertThat(captured.getDateFormat()).isEqualTo("yyyy-MM-dd");

        assertThat(data.getSavingsId()).isEqualTo(SAVINGS_ID);
        assertThat(data.getResourceId()).isEqualTo(99L);
        assertThat(data.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void withdrawRejectsWhenAccessDenied() {
        Jwt jwt = jwt();
        doThrow(new SavingsCommandAccessDeniedException())
                .when(accessPolicyEvaluator).authorize(eq(jwt), eq(ConsumerAction.SAVINGS_APPLICATION_WITHDRAW), eq(SAVINGS_ID), any());

        WithdrawSavingsApplicationCommand cmd = WithdrawSavingsApplicationCommand.builder()
                .savingsId(SAVINGS_ID)
                .withdrawnOnDate(LocalDate.of(2026, 7, 3))
                .note("Not needed")
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();

        assertThatThrownBy(() -> service.withdrawApplication(jwt, cmd))
                .isInstanceOf(SavingsCommandAccessDeniedException.class);

        verifyNoInteractions(savingsAccountApi);
    }
}
