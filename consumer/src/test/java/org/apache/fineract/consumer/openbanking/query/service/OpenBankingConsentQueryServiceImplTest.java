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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.openbanking.query.data.ConsentStatus;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingConsentQueryData;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingPermission;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingTppConsentQueryData;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingUserConsentQueryData;
import org.apache.fineract.consumer.openbanking.query.domain.OpenBankingConsentQueryEntity;
import org.apache.fineract.consumer.openbanking.query.exception.OpenBankingConsentQueryNotFoundException;
import org.apache.fineract.consumer.openbanking.query.repository.OpenBankingConsentQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class OpenBankingConsentQueryServiceImplTest {

    private static final String TPP_CLIENT_ID = "demo-tpp";
    private static final UUID CONSENT_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final UUID NEWER_CONSENT_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000002");
    private static final UUID USER_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000003");
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-01T10:00:00Z");

    @Mock
    private OpenBankingConsentQueryRepository openBankingConsentQueryRepository;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @InjectMocks
    private OpenBankingConsentQueryServiceImpl service;

    private static Jwt jwtWithSubject(String subject) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .build();
    }

    @Test
    void findConsentMapsEntityToQueryData() {
        when(openBankingConsentQueryRepository.findById(CONSENT_ID))
                .thenReturn(Optional.of(entity(CONSENT_ID, CREATED_AT)));

        assertThat(service.findConsent(CONSENT_ID)).contains(expectedData(CONSENT_ID, CREATED_AT));
    }

    @Test
    void findConsentReturnsEmptyWhenUnknown() {
        when(openBankingConsentQueryRepository.findById(CONSENT_ID)).thenReturn(Optional.empty());

        assertThat(service.findConsent(CONSENT_ID)).isEmpty();
    }

    @Test
    void getConsentForTppClientReturnsOwnConsent() {
        when(openBankingConsentQueryRepository.findById(CONSENT_ID))
                .thenReturn(Optional.of(entity(CONSENT_ID, CREATED_AT)));

        assertThat(service.getConsentForTppClient(jwtWithSubject(TPP_CLIENT_ID), CONSENT_ID))
                .isEqualTo(expectedTppData(CONSENT_ID, CREATED_AT));
    }

    @Test
    void getConsentForTppClientShapesForeignConsentAsNotFound() {
        when(openBankingConsentQueryRepository.findById(CONSENT_ID))
                .thenReturn(Optional.of(entity(CONSENT_ID, CREATED_AT)));

        assertThatThrownBy(() -> service.getConsentForTppClient(jwtWithSubject("other-tpp"), CONSENT_ID))
                .isInstanceOf(OpenBankingConsentQueryNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", OpenBankingConsentQueryNotFoundException.CODE);
    }

    @Test
    void getConsentForTppClientUnknownConsentIsNotFound() {
        when(openBankingConsentQueryRepository.findById(CONSENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConsentForTppClient(jwtWithSubject(TPP_CLIENT_ID), CONSENT_ID))
                .isInstanceOf(OpenBankingConsentQueryNotFoundException.class);
    }

    @Test
    void getConsentForTppClientDeniedByPolicyNeverTouchesRepository() {
        Jwt jwt = jwtWithSubject(TPP_CLIENT_ID);
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(jwt, ConsumerAction.OPENBANKING_TPP_CONSENT_VIEW);

        assertThatThrownBy(() -> service.getConsentForTppClient(jwt, CONSENT_ID))
                .isInstanceOf(AccessScopeInsufficientException.class);
        verify(openBankingConsentQueryRepository, never()).findById(any());
    }

    @Test
    void listConsentsForUserPreservesNewestFirstRepositoryOrder() {
        Instant newerCreatedAt = CREATED_AT.plus(1, ChronoUnit.DAYS);
        when(openBankingConsentQueryRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(entity(NEWER_CONSENT_ID, newerCreatedAt), entity(CONSENT_ID, CREATED_AT)));

        assertThat(service.listConsentsForUser(jwtWithSubject(USER_ID.toString()))).containsExactly(
                expectedUserData(NEWER_CONSENT_ID, newerCreatedAt),
                expectedUserData(CONSENT_ID, CREATED_AT));
    }

    @Test
    void listConsentsForUserReturnsEmptyWhenNoneExist() {
        when(openBankingConsentQueryRepository.findAllByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        assertThat(service.listConsentsForUser(jwtWithSubject(USER_ID.toString()))).isEmpty();
    }

    @Test
    void listConsentsForUserDeniedByPolicyNeverTouchesRepository() {
        Jwt jwt = jwtWithSubject(USER_ID.toString());
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(jwt, ConsumerAction.OPENBANKING_CONSENT_VIEW);

        assertThatThrownBy(() -> service.listConsentsForUser(jwt))
                .isInstanceOf(AccessScopeInsufficientException.class);
        verify(openBankingConsentQueryRepository, never()).findAllByUserIdOrderByCreatedAtDesc(any());
    }

    private static OpenBankingConsentQueryEntity entity(UUID consentId, Instant createdAt) {
        return OpenBankingConsentQueryEntity.of(consentId, TPP_CLIENT_ID, USER_ID,
                EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC, OpenBankingPermission.READ_BALANCES),
                ConsentStatus.AUTHORISED, createdAt, EXPIRES_AT, createdAt);
    }

    private static OpenBankingConsentQueryData expectedData(UUID consentId, Instant createdAt) {
        return OpenBankingConsentQueryData.builder()
                .consentId(consentId)
                .tppClientId(TPP_CLIENT_ID)
                .userId(USER_ID)
                .permissions(EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC, OpenBankingPermission.READ_BALANCES))
                .status(ConsentStatus.AUTHORISED)
                .creationDateTime(createdAt)
                .expirationDateTime(EXPIRES_AT)
                .statusUpdateDateTime(createdAt)
                .build();
    }

    private static OpenBankingTppConsentQueryData expectedTppData(UUID consentId, Instant createdAt) {
        return OpenBankingTppConsentQueryData.builder()
                .consentId(consentId)
                .permissions(EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC, OpenBankingPermission.READ_BALANCES))
                .status(ConsentStatus.AUTHORISED)
                .creationDateTime(createdAt)
                .expirationDateTime(EXPIRES_AT)
                .statusUpdateDateTime(createdAt)
                .build();
    }

    private static OpenBankingUserConsentQueryData expectedUserData(UUID consentId, Instant createdAt) {
        return OpenBankingUserConsentQueryData.builder()
                .consentId(consentId)
                .tppClientId(TPP_CLIENT_ID)
                .permissions(EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC, OpenBankingPermission.READ_BALANCES))
                .status(ConsentStatus.AUTHORISED)
                .creationDateTime(createdAt)
                .expirationDateTime(EXPIRES_AT)
                .build();
    }
}
