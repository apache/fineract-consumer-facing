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
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2ConsentData;
import org.apache.fineract.consumer.openbanking.query.data.ConsentStatus;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingConsentQueryData;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingPermission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OpenBankingConsentLookupAdapterTest {

    private static final String TPP_CLIENT_ID = "demo-tpp";
    private static final UUID CONSENT_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-01T10:00:00Z");

    @Mock
    private OpenBankingConsentQueryService openBankingConsentQueryService;

    @InjectMocks
    private OpenBankingConsentLookupAdapter adapter;

    @Test
    void awaitingConsentMapsTppClientIdExpiryAndAwaitingFlag() {
        when(openBankingConsentQueryService.findConsent(CONSENT_ID))
                .thenReturn(Optional.of(consent(ConsentStatus.AWAITING_AUTHORISATION)));

        assertThat(adapter.findConsent(CONSENT_ID)).contains(OAuth2ConsentData.builder()
                .tppClientId(TPP_CLIENT_ID)
                .expiresAt(EXPIRES_AT)
                .awaitingAuthorisation(true)
                .authorised(false)
                .build());
    }

    @Test
    void authorisedConsentMapsAuthorisedFlag() {
        when(openBankingConsentQueryService.findConsent(CONSENT_ID))
                .thenReturn(Optional.of(consent(ConsentStatus.AUTHORISED)));

        assertThat(adapter.findConsent(CONSENT_ID)).contains(OAuth2ConsentData.builder()
                .tppClientId(TPP_CLIENT_ID)
                .expiresAt(EXPIRES_AT)
                .awaitingAuthorisation(false)
                .authorised(true)
                .build());
    }

    @Test
    void rejectedConsentMapsBothFlagsFalse() {
        when(openBankingConsentQueryService.findConsent(CONSENT_ID))
                .thenReturn(Optional.of(consent(ConsentStatus.REJECTED)));

        OAuth2ConsentData data = adapter.findConsent(CONSENT_ID).orElseThrow();
        assertThat(data.isAwaitingAuthorisation()).isFalse();
        assertThat(data.isAuthorised()).isFalse();
    }

    @Test
    void revokedConsentMapsBothFlagsFalse() {
        when(openBankingConsentQueryService.findConsent(CONSENT_ID))
                .thenReturn(Optional.of(consent(ConsentStatus.REVOKED)));

        OAuth2ConsentData data = adapter.findConsent(CONSENT_ID).orElseThrow();
        assertThat(data.isAwaitingAuthorisation()).isFalse();
        assertThat(data.isAuthorised()).isFalse();
    }

    @Test
    void unknownConsentMapsToEmpty() {
        when(openBankingConsentQueryService.findConsent(CONSENT_ID)).thenReturn(Optional.empty());

        assertThat(adapter.findConsent(CONSENT_ID)).isEmpty();
    }

    private static OpenBankingConsentQueryData consent(ConsentStatus status) {
        return OpenBankingConsentQueryData.builder()
                .consentId(CONSENT_ID)
                .tppClientId(TPP_CLIENT_ID)
                .userId(UUID.fromString("3f2c8a1e-0000-4000-8000-000000000002"))
                .permissions(EnumSet.of(OpenBankingPermission.READ_ACCOUNTS_BASIC))
                .status(status)
                .creationDateTime(Instant.parse("2026-08-01T10:00:00Z"))
                .expirationDateTime(EXPIRES_AT)
                .statusUpdateDateTime(Instant.parse("2026-08-01T10:00:00Z"))
                .build();
    }
}
