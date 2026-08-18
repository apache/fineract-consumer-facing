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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.configs.OAuth2ConsumerProperties;
import org.apache.fineract.consumer.infrastructure.oauth2.domain.RegisteredTpp;
import org.apache.fineract.consumer.infrastructure.oauth2.repository.RegisteredTppRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

@ExtendWith(MockitoExtension.class)
class RegisteredTppClientAdapterTest {

    private static final UUID TPP_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-0000000000aa");
    private static final String CLIENT_ID = "demo-tpp";
    private static final String CLIENT_SECRET_HASH = "{bcrypt}$2y$10$piiChDy1OaMQggEym1p4J.0npIQfqpgeUChoB8j8oOt/oIX0P19Q.";
    private static final String CLIENT_NAME = "Demo TPP";
    private static final String REDIRECT_URI = "https://tpp.example/callback";
    private static final String SCOPES =
            AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS + "," + AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ;
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofHours(12);

    @Mock
    private RegisteredTppRepository registeredTppRepository;

    private final OAuth2ConsumerProperties oauth2Properties = new OAuth2ConsumerProperties(
            ACCESS_TOKEN_TTL, REFRESH_TOKEN_TTL, Duration.ofDays(90), "http://localhost:4200", "ob-key");

    @Test
    void activeRowMapsToRegisteredClientIdentityAndCredentials() {
        RegisteredClient client = findByClientId(activeTpp(SCOPES));

        assertThat(client.getId()).isEqualTo(TPP_ID.toString());
        assertThat(client.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(client.getClientSecret()).isEqualTo(CLIENT_SECRET_HASH);
        assertThat(client.getClientName()).isEqualTo(CLIENT_NAME);
        assertThat(client.getRedirectUris()).containsExactly(REDIRECT_URI);
        assertThat(client.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
    }

    @Test
    void allThreeGrantTypesRegistered() {
        assertThat(findByClientId(activeTpp(SCOPES)).getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.CLIENT_CREDENTIALS,
                AuthorizationGrantType.AUTHORIZATION_CODE,
                AuthorizationGrantType.REFRESH_TOKEN);
    }

    @Test
    void pkceAndUserConsentAreRequired() {
        RegisteredClient client = findByClientId(activeTpp(SCOPES));

        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isTrue();
    }

    @Test
    void tokenTtlsComeFromPropertiesAndRefreshTokensRotate() {
        RegisteredClient client = findByClientId(activeTpp(SCOPES));

        assertThat(client.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(ACCESS_TOKEN_TTL);
        assertThat(client.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(REFRESH_TOKEN_TTL);
        assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
    }

    @Test
    void commaSeparatedScopesRoundTripFromTheRow() {
        assertThat(findByClientId(activeTpp(SCOPES)).getScopes()).containsExactlyInAnyOrder(
                AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS,
                AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ);
    }

    @Test
    void scopesAreTrimmedAndBlankEntriesDropped() {
        String paddedScopes = " " + AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS + " , ,"
                + AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ + " ";

        assertThat(findByClientId(activeTpp(paddedScopes)).getScopes()).containsExactlyInAnyOrder(
                AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS,
                AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ);
    }

    @Test
    void findByIdResolvesTheSameClientAsFindByClientId() {
        when(registeredTppRepository.findById(TPP_ID)).thenReturn(Optional.of(activeTpp(SCOPES)));

        RegisteredClient client = adapter().findById(TPP_ID.toString());

        assertThat(client).isNotNull();
        assertThat(client.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void revokedRowIsInvisibleToBothFinders() {
        RegisteredTpp revoked = activeTpp(SCOPES);
        revoked.revoke();
        when(registeredTppRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(revoked));
        when(registeredTppRepository.findById(TPP_ID)).thenReturn(Optional.of(revoked));

        assertThat(adapter().findByClientId(CLIENT_ID)).isNull();
        assertThat(adapter().findById(TPP_ID.toString())).isNull();
    }

    @Test
    void unknownClientIdIsAMiss() {
        when(registeredTppRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());

        assertThat(adapter().findByClientId(CLIENT_ID)).isNull();
    }

    @Test
    void staleNonUuidAuthorizationRowIdIsAMissRatherThanAnError() {
        assertThat(adapter().findById(CLIENT_ID)).isNull();

        verifyNoInteractions(registeredTppRepository);
    }

    @Test
    void saveIsRejectedBecauseDynamicClientRegistrationIsNotEnabled() {
        RegisteredClient client = findByClientId(activeTpp(SCOPES));

        assertThatThrownBy(() -> adapter().save(client)).isInstanceOf(UnsupportedOperationException.class);
    }

    private RegisteredClient findByClientId(RegisteredTpp tpp) {
        when(registeredTppRepository.findByClientId(CLIENT_ID)).thenReturn(Optional.of(tpp));
        RegisteredClient client = adapter().findByClientId(CLIENT_ID);
        assertThat(client).isNotNull();
        return client;
    }

    private RegisteredTppClientAdapter adapter() {
        return new RegisteredTppClientAdapter(registeredTppRepository, oauth2Properties);
    }

    private static RegisteredTpp activeTpp(String scopes) {
        return RegisteredTpp.onboard(TPP_ID, CLIENT_ID, CLIENT_SECRET_HASH, CLIENT_NAME, REDIRECT_URI, scopes);
    }
}
