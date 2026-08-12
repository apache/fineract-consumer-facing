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

package org.apache.fineract.consumer.infrastructure.oauth2.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2ConsentData;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2ConsentLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2ConsentAuthorizeRequestValidatorTest {

    private static final String TPP_CLIENT_ID = "demo-tpp";
    private static final String OTHER_TPP_CLIENT_ID = "other-tpp";
    private static final String REDIRECT_URI = "https://tpp.example/callback";
    private static final UUID CONSENT_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Instant FUTURE = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Instant PAST = Instant.now().minus(1, ChronoUnit.MINUTES);

    @Mock
    private OAuth2ConsentLookupPort consentLookup;

    private OAuth2ConsentAuthorizeRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new OAuth2ConsentAuthorizeRequestValidator(consentLookup);
    }

    @Test
    void awaitingUnexpiredConsentOfRequestingTppClientPasses() {
        when(consentLookup.findConsent(CONSENT_ID))
                .thenReturn(Optional.of(consent(TPP_CLIENT_ID, true, FUTURE)));

        assertThatCode(() -> validator.accept(context(CONSENT_ID.toString()))).doesNotThrowAnyException();
    }

    @Test
    void missingConsentIdParameterIsRejected() {
        assertRejected(context(null));
        verify(consentLookup, never()).findConsent(any());
    }

    @Test
    void malformedConsentIdIsRejected() {
        assertRejected(context("not-a-uuid"));
        verify(consentLookup, never()).findConsent(any());
    }

    @Test
    void unknownConsentIsRejected() {
        when(consentLookup.findConsent(CONSENT_ID)).thenReturn(Optional.empty());

        assertRejected(context(CONSENT_ID.toString()));
    }

    @Test
    void otherTppClientsConsentIsRejected() {
        when(consentLookup.findConsent(CONSENT_ID))
                .thenReturn(Optional.of(consent(OTHER_TPP_CLIENT_ID, true, FUTURE)));

        assertRejected(context(CONSENT_ID.toString()));
    }

    @Test
    void nonAwaitingConsentIsRejected() {
        when(consentLookup.findConsent(CONSENT_ID))
                .thenReturn(Optional.of(consent(TPP_CLIENT_ID, false, FUTURE)));

        assertRejected(context(CONSENT_ID.toString()));
    }

    @Test
    void expiredConsentIsRejected() {
        when(consentLookup.findConsent(CONSENT_ID))
                .thenReturn(Optional.of(consent(TPP_CLIENT_ID, true, PAST)));

        assertRejected(context(CONSENT_ID.toString()));
    }

    private void assertRejected(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        assertThatThrownBy(() -> validator.accept(context))
                .isInstanceOfSatisfying(OAuth2AuthorizationCodeRequestAuthenticationException.class, exception -> {
                    assertThat(exception.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST);
                    assertThat(exception.getError().getDescription())
                            .contains(OAuth2Constants.CONSENT_ID);
                });
    }

    private static OAuth2ConsentData consent(String tppClientId, boolean awaitingAuthorisation,
            Instant expiresAt) {
        return OAuth2ConsentData.builder()
                .tppClientId(tppClientId)
                .expiresAt(expiresAt)
                .awaitingAuthorisation(awaitingAuthorisation)
                .authorised(!awaitingAuthorisation)
                .build();
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationContext context(String rawConsentId) {
        Map<String, Object> additionalParameters = rawConsentId == null
                ? Map.of()
                : Map.of(OAuth2Constants.CONSENT_ID, rawConsentId);
        OAuth2AuthorizationCodeRequestAuthenticationToken authentication =
                new OAuth2AuthorizationCodeRequestAuthenticationToken(
                        "https://bff.example" + OAuth2Constants.AUTHORIZATION_ENDPOINT,
                        TPP_CLIENT_ID,
                        new TestingAuthenticationToken("user", "n/a"),
                        REDIRECT_URI,
                        "state",
                        Set.of(AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ),
                        additionalParameters);
        return OAuth2AuthorizationCodeRequestAuthenticationContext.with(authentication)
                .registeredClient(RegisteredClient.withId("registered-client-id")
                        .clientId(TPP_CLIENT_ID)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri(REDIRECT_URI)
                        .build())
                .build();
    }
}
