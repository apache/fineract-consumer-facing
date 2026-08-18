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

package org.apache.fineract.consumer.openbanking.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.TransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.configs.OAuth2ConsumerProperties;
import org.apache.fineract.consumer.openbanking.command.data.ConsentStatus;
import org.apache.fineract.consumer.openbanking.command.data.CreateOpenBankingConsentCommand;
import org.apache.fineract.consumer.openbanking.command.data.OpenBankingConsentCommandData;
import org.apache.fineract.consumer.openbanking.command.data.OpenBankingPermission;
import org.apache.fineract.consumer.openbanking.command.domain.OpenBankingConsent;
import org.apache.fineract.consumer.openbanking.command.exception.OpenBankingConsentCommandNotFoundException;
import org.apache.fineract.consumer.openbanking.command.exception.OpenBankingConsentNotRevocableException;
import org.apache.fineract.consumer.openbanking.command.exception.OpenBankingConsentPermissionInvalidException;
import org.apache.fineract.consumer.openbanking.command.repository.OpenBankingConsentCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class OpenBankingConsentCommandServiceImplTest {

    private static final String TPP_CLIENT_ID = "demo-tpp";
    private static final Duration CONSENT_TTL = Duration.ofDays(90);
    private static final UUID CONSENT_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000002");
    private static final UUID OTHER_USER_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000003");

    @Mock
    private OpenBankingConsentCommandRepository openBankingConsentCommandRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    private final OAuth2ConsumerProperties oauth2Properties = new OAuth2ConsumerProperties(
            Duration.ofMinutes(5), Duration.ofHours(12), CONSENT_TTL, "http://localhost:4200", "ob-key");

    private OpenBankingConsentCommandServiceImpl service() {
        return new OpenBankingConsentCommandServiceImpl(openBankingConsentCommandRepository, oauth2Properties, eventPublisher,
                accessPolicyEvaluator);
    }

    private static Jwt tppJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(TPP_CLIENT_ID)
                .build();
    }

    private static Jwt userJwt(UUID userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();
    }

    @Test
    void createPersistsAwaitingConsentWithTtlAndReturnsItsData() {
        OpenBankingConsentCommandData data = service().create(tppJwt(), CreateOpenBankingConsentCommand.builder()
                .permissions(EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC, OpenBankingPermission.READ_BALANCES))
                .build());

        ArgumentCaptor<OpenBankingConsent> savedCaptor = ArgumentCaptor.forClass(OpenBankingConsent.class);
        verify(openBankingConsentCommandRepository).save(savedCaptor.capture());
        OpenBankingConsent saved = savedCaptor.getValue();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTppClientId()).isEqualTo(TPP_CLIENT_ID);
        assertThat(saved.getStatus()).isEqualTo(ConsentStatus.AWAITING_AUTHORISATION);
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.getExpiresAt()).isEqualTo(saved.getCreatedAt().plus(CONSENT_TTL));
        assertThat(data).isEqualTo(OpenBankingConsentCommandData.builder()
                .consentId(saved.getId())
                .status(ConsentStatus.AWAITING_AUTHORISATION)
                .permissions(Set.of(OpenBankingPermission.READ_ACCOUNTS_BASIC, OpenBankingPermission.READ_BALANCES))
                .creationDateTime(saved.getCreatedAt())
                .expirationDateTime(saved.getExpiresAt())
                .build());
    }

    @Test
    void createPublishesConsentCreatedAuditEvent() {
        service().create(tppJwt(), CreateOpenBankingConsentCommand.builder()
                .permissions(EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC))
                .build());

        TransactionalAuditEvent event = capturedEvent();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.CONSENT_CREATED);
        assertThat(event.getUserId()).isNull();
        assertThat(event.isUnknownPrincipal()).isFalse();
        assertThat(event.getDetails())
                .containsEntry("tppClientId", TPP_CLIENT_ID)
                .containsEntry("permissions", Set.of(OpenBankingPermission.READ_ACCOUNTS_BASIC.getCode()))
                .containsKey("consentId");
    }

    @Test
    void createWithEmptyPermissionsIsInvalid() {
        CreateOpenBankingConsentCommand command = CreateOpenBankingConsentCommand.builder()
                .permissions(Set.of())
                .build();

        assertThatThrownBy(() -> service().create(tppJwt(), command))
                .isInstanceOf(OpenBankingConsentPermissionInvalidException.class)
                .hasFieldOrPropertyWithValue("code", OpenBankingConsentPermissionInvalidException.CODE);
        verify(openBankingConsentCommandRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void createWithNullPermissionsIsInvalid() {
        CreateOpenBankingConsentCommand command = CreateOpenBankingConsentCommand.builder()
                .build();

        assertThatThrownBy(() -> service().create(tppJwt(), command))
                .isInstanceOf(OpenBankingConsentPermissionInvalidException.class);
    }

    @Test
    void createDeniedByPolicyNeverTouchesRepository() {
        Jwt jwt = tppJwt();
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(jwt, ConsumerAction.OPENBANKING_TPP_CONSENT_CREATE);
        CreateOpenBankingConsentCommand command = CreateOpenBankingConsentCommand.builder()
                .permissions(EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC))
                .build();

        assertThatThrownBy(() -> service().create(jwt, command))
                .isInstanceOf(AccessScopeInsufficientException.class);
        verify(openBankingConsentCommandRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void authoriseBindsOwnerAndTransitionsToAuthorised() {
        OpenBankingConsent consent = awaitingConsent();
        when(openBankingConsentCommandRepository.findById(CONSENT_ID)).thenReturn(Optional.of(consent));

        service().authorise(CONSENT_ID, USER_ID);

        assertThat(consent.getStatus()).isEqualTo(ConsentStatus.AUTHORISED);
        assertThat(consent.getUserId()).isEqualTo(USER_ID);
        verify(openBankingConsentCommandRepository).save(consent);
    }

    @Test
    void authoriseUnknownConsentIsNotFound() {
        when(openBankingConsentCommandRepository.findById(CONSENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().authorise(CONSENT_ID, USER_ID))
                .isInstanceOf(OpenBankingConsentCommandNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", OpenBankingConsentCommandNotFoundException.CODE);
    }

    @Test
    void authoriseNonAwaitingConsentIsAProgrammingError() {
        OpenBankingConsent consent = awaitingConsent();
        consent.authorise(USER_ID);
        when(openBankingConsentCommandRepository.findById(CONSENT_ID)).thenReturn(Optional.of(consent));

        assertThatThrownBy(() -> service().authorise(CONSENT_ID, USER_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectBindsActingUserAndTransitionsToRejected() {
        OpenBankingConsent consent = awaitingConsent();
        when(openBankingConsentCommandRepository.findById(CONSENT_ID)).thenReturn(Optional.of(consent));

        service().reject(CONSENT_ID, USER_ID);

        assertThat(consent.getStatus()).isEqualTo(ConsentStatus.REJECTED);
        assertThat(consent.getUserId()).isEqualTo(USER_ID);
        verify(openBankingConsentCommandRepository).save(consent);
    }

    @Test
    void revokeTransitionsToRevokedAndPublishesAuditEvent() {
        OpenBankingConsent consent = authorisedConsent();
        when(openBankingConsentCommandRepository.findById(CONSENT_ID)).thenReturn(Optional.of(consent));

        OpenBankingConsentCommandData data = service().revoke(userJwt(USER_ID), CONSENT_ID);

        assertThat(consent.getStatus()).isEqualTo(ConsentStatus.REVOKED);
        verify(openBankingConsentCommandRepository).save(consent);
        assertThat(data).isEqualTo(OpenBankingConsentCommandData.builder()
                .consentId(consent.getId())
                .status(ConsentStatus.REVOKED)
                .permissions(Set.of(OpenBankingPermission.READ_ACCOUNTS_BASIC, OpenBankingPermission.READ_BALANCES))
                .creationDateTime(consent.getCreatedAt())
                .expirationDateTime(consent.getExpiresAt())
                .build());
        TransactionalAuditEvent event = capturedEvent();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.CONSENT_REVOKED);
        assertThat(event.getDetails())
                .containsEntry("consentId", consent.getId().toString())
                .containsEntry("tppClientId", TPP_CLIENT_ID)
                .containsEntry("userPublicId", USER_ID.toString());
    }

    @Test
    void revokeByNonOwnerIsShapedAsNotFound() {
        when(openBankingConsentCommandRepository.findById(CONSENT_ID)).thenReturn(Optional.of(authorisedConsent()));

        assertThatThrownBy(() -> service().revoke(userJwt(OTHER_USER_ID), CONSENT_ID))
                .isInstanceOf(OpenBankingConsentCommandNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", OpenBankingConsentCommandNotFoundException.CODE);
        verify(openBankingConsentCommandRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void revokeUnknownConsentIsNotFound() {
        when(openBankingConsentCommandRepository.findById(CONSENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().revoke(userJwt(USER_ID), CONSENT_ID))
                .isInstanceOf(OpenBankingConsentCommandNotFoundException.class);
    }

    @Test
    void revokeDeniedByPolicyNeverTouchesRepository() {
        Jwt jwt = userJwt(USER_ID);
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(jwt, ConsumerAction.OPENBANKING_CONSENT_REVOKE);

        assertThatThrownBy(() -> service().revoke(jwt, CONSENT_ID))
                .isInstanceOf(AccessScopeInsufficientException.class);
        verify(openBankingConsentCommandRepository, never()).findById(any());
        verify(openBankingConsentCommandRepository, never()).save(any());
    }

    @Test
    void revokeNonAuthorisedConsentIsNotRevocable() {
        OpenBankingConsent consent = awaitingConsent();
        consent.reject(USER_ID);
        when(openBankingConsentCommandRepository.findById(CONSENT_ID)).thenReturn(Optional.of(consent));

        assertThatThrownBy(() -> service().revoke(userJwt(USER_ID), CONSENT_ID))
                .isInstanceOf(OpenBankingConsentNotRevocableException.class)
                .hasFieldOrPropertyWithValue("code", OpenBankingConsentNotRevocableException.CODE);
        verify(openBankingConsentCommandRepository, never()).save(any());
    }

    private TransactionalAuditEvent capturedEvent() {
        ArgumentCaptor<TransactionalAuditEvent> captor = ArgumentCaptor.forClass(TransactionalAuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private static OpenBankingConsent awaitingConsent() {
        return OpenBankingConsent.createAwaitingAuthorisation(CONSENT_ID, TPP_CLIENT_ID,
                EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC, OpenBankingPermission.READ_BALANCES), CONSENT_TTL);
    }

    private static OpenBankingConsent authorisedConsent() {
        OpenBankingConsent consent = awaitingConsent();
        consent.authorise(USER_ID);
        return consent;
    }
}
