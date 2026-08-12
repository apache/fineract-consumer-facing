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
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2ConsentData;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2ConsentLookupPort;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2RefreshTokenConsentValidatorTest {

    private static final String TPP_CLIENT_ID = "demo-tpp";
    private static final String REDIRECT_URI = "https://tpp.example/callback";
    private static final String REFRESH_TOKEN_VALUE = "refresh-token-value";
    private static final UUID CONSENT_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Instant FUTURE = Instant.now().plus(1, ChronoUnit.DAYS);
    private static final Instant PAST = Instant.now().minus(1, ChronoUnit.MINUTES);

    @Mock
    private AuthenticationProvider delegate;

    @Mock
    private OAuth2AuthorizationService authorizationService;

    @Mock
    private OAuth2ConsentLookupPort consentLookup;

    private OAuth2RefreshTokenConsentValidator provider;

    @BeforeEach
    void setUp() {
        provider = new OAuth2RefreshTokenConsentValidator(
                delegate, authorizationService, consentLookup);
    }

    @Test
    void authorisedUnexpiredConsentDelegatesToWrappedProvider() {
        OAuth2RefreshTokenAuthenticationToken refreshAuthentication = refreshAuthentication();
        Authentication delegateResult = new TestingAuthenticationToken("result", "n/a");
        when(authorizationService.findByToken(REFRESH_TOKEN_VALUE, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorizationCarryingConsentId());
        when(consentLookup.findConsent(CONSENT_ID))
                .thenReturn(Optional.of(consent(true, FUTURE)));
        when(delegate.authenticate(refreshAuthentication)).thenReturn(delegateResult);

        assertThat(provider.authenticate(refreshAuthentication)).isSameAs(delegateResult);
    }

    @Test
    void revokedOrRejectedConsentFailsWithInvalidGrant() {
        when(authorizationService.findByToken(REFRESH_TOKEN_VALUE, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorizationCarryingConsentId());
        when(consentLookup.findConsent(CONSENT_ID))
                .thenReturn(Optional.of(consent(false, FUTURE)));

        assertInvalidGrant(() -> provider.authenticate(refreshAuthentication()));
    }

    @Test
    void expiredConsentFailsWithInvalidGrant() {
        when(authorizationService.findByToken(REFRESH_TOKEN_VALUE, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorizationCarryingConsentId());
        when(consentLookup.findConsent(CONSENT_ID))
                .thenReturn(Optional.of(consent(true, PAST)));

        assertInvalidGrant(() -> provider.authenticate(refreshAuthentication()));
    }

    @Test
    void missingConsentRecordFailsWithInvalidGrant() {
        when(authorizationService.findByToken(REFRESH_TOKEN_VALUE, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorizationCarryingConsentId());
        when(consentLookup.findConsent(CONSENT_ID)).thenReturn(Optional.empty());

        assertInvalidGrant(() -> provider.authenticate(refreshAuthentication()));
    }

    @Test
    void authorizationWithoutConsentIdFailsWithInvalidGrant() {
        OAuth2Authorization authorizationWithoutConsent = OAuth2Authorization
                .withRegisteredClient(registeredClient())
                .principalName("user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();
        when(authorizationService.findByToken(REFRESH_TOKEN_VALUE, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorizationWithoutConsent);

        assertInvalidGrant(() -> provider.authenticate(refreshAuthentication()));
        verify(consentLookup, never()).findConsent(any());
    }

    @Test
    void unknownRefreshTokenDelegatesForStandardInvalidGrant() {
        OAuth2RefreshTokenAuthenticationToken refreshAuthentication = refreshAuthentication();
        when(authorizationService.findByToken(REFRESH_TOKEN_VALUE, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(null);

        provider.authenticate(refreshAuthentication);

        verify(delegate).authenticate(refreshAuthentication);
        verify(consentLookup, never()).findConsent(any());
    }

    @Test
    void supportsMirrorsDelegate() {
        when(delegate.supports(OAuth2RefreshTokenAuthenticationToken.class)).thenReturn(true);

        assertThat(provider.supports(OAuth2RefreshTokenAuthenticationToken.class)).isTrue();
    }

    private void assertInvalidGrant(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
                        assertThat(exception.getError().getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT));
        verify(delegate, never()).authenticate(any());
    }

    private static OAuth2RefreshTokenAuthenticationToken refreshAuthentication() {
        return new OAuth2RefreshTokenAuthenticationToken(REFRESH_TOKEN_VALUE,
                new TestingAuthenticationToken(TPP_CLIENT_ID, "n/a"), Set.of(), Map.of());
    }

    private static OAuth2ConsentData consent(boolean authorised, Instant expiresAt) {
        return OAuth2ConsentData.builder()
                .tppClientId(TPP_CLIENT_ID)
                .expiresAt(expiresAt)
                .awaitingAuthorisation(false)
                .authorised(authorised)
                .build();
    }

    private static OAuth2Authorization authorizationCarryingConsentId() {
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://bff.example" + OAuth2Constants.AUTHORIZATION_ENDPOINT)
                .clientId(TPP_CLIENT_ID)
                .redirectUri(REDIRECT_URI)
                .additionalParameters(parameters ->
                        parameters.put(OAuth2Constants.CONSENT_ID, CONSENT_ID.toString()))
                .build();
        return OAuth2Authorization.withRegisteredClient(registeredClient())
                .principalName("user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(OAuth2AuthorizationRequest.class.getName(), authorizationRequest)
                .build();
    }

    private static RegisteredClient registeredClient() {
        return RegisteredClient.withId("registered-client-id")
                .clientId(TPP_CLIENT_ID)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .build();
    }
}
