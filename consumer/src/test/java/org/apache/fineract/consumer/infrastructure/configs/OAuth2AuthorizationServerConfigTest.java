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

package org.apache.fineract.consumer.infrastructure.configs;

import java.time.Duration;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.configs.JwtProperties;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AuthorizationServerConfigTest {

    private static final String TPP_CLIENT_ID = "demo-tpp";
    private static final String TPP_CLIENT_SECRET = "tpp-secret";
    private static final String REDIRECT_URI = "https://tpp.example/callback";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(5);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofHours(12);
    private static final String ISSUER = "https://bff.example";

    private final OAuth2AuthorizationServerConfig config = new OAuth2AuthorizationServerConfig();

    private final OAuth2ConsumerProperties properties = new OAuth2ConsumerProperties(
            new OAuth2ConsumerProperties.TppClient(TPP_CLIENT_ID, TPP_CLIENT_SECRET, REDIRECT_URI),
            ACCESS_TOKEN_TTL, REFRESH_TOKEN_TTL, Duration.ofDays(90), "http://localhost:4200", "ob-key");

    @Test
    void demoTppRegisteredWithClientIdAndRedirectUriFromProperties() {
        RegisteredClient client = demoTpp();

        assertThat(client.getClientId()).isEqualTo(TPP_CLIENT_ID);
        assertThat(client.getClientSecret()).startsWith("{bcrypt}").doesNotContain(TPP_CLIENT_SECRET);
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .matches(TPP_CLIENT_SECRET, client.getClientSecret())).isTrue();
        assertThat(client.getRedirectUris()).containsExactly(REDIRECT_URI);
        assertThat(client.getClientAuthenticationMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
    }

    @Test
    void allThreeGrantTypesRegistered() {
        assertThat(demoTpp().getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
                AuthorizationGrantType.CLIENT_CREDENTIALS,
                AuthorizationGrantType.AUTHORIZATION_CODE,
                AuthorizationGrantType.REFRESH_TOKEN);
    }

    @Test
    void pkceAndUserConsentAreRequired() {
        RegisteredClient client = demoTpp();

        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isTrue();
    }

    @Test
    void tokenTtlsComeFromPropertiesAndRefreshTokensRotate() {
        RegisteredClient client = demoTpp();

        assertThat(client.getTokenSettings().getAccessTokenTimeToLive()).isEqualTo(ACCESS_TOKEN_TTL);
        assertThat(client.getTokenSettings().getRefreshTokenTimeToLive()).isEqualTo(REFRESH_TOKEN_TTL);
        assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
    }

    @Test
    void openBankingScopesRegistered() {
        assertThat(demoTpp().getScopes()).containsExactlyInAnyOrder(
                AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS,
                AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ);
    }

    @Test
    void authorizationServerSettingsUseApiV1PathsAndConfiguredIssuer() {
        AuthorizationServerSettings settings =
                config.authorizationServerSettings(new JwtProperties(null, ISSUER));

        assertThat(settings.getIssuer()).isEqualTo(ISSUER);
        assertThat(settings.getAuthorizationEndpoint())
                .isEqualTo(OAuth2Constants.AUTHORIZATION_ENDPOINT);
        assertThat(settings.getTokenEndpoint()).isEqualTo(OAuth2Constants.TOKEN_ENDPOINT);
        assertThat(settings.getJwkSetEndpoint()).isEqualTo(OAuth2Constants.JWK_SET_ENDPOINT);
        assertThat(settings.getTokenRevocationEndpoint())
                .isEqualTo(OAuth2Constants.TOKEN_REVOCATION_ENDPOINT);
        assertThat(settings.getTokenIntrospectionEndpoint())
                .isEqualTo(OAuth2Constants.TOKEN_INTROSPECTION_ENDPOINT);
    }

    private RegisteredClient demoTpp() {
        RegisteredClient client = config.registeredClientRepository(properties).findByClientId(TPP_CLIENT_ID);
        assertThat(client).isNotNull();
        return client;
    }
}
