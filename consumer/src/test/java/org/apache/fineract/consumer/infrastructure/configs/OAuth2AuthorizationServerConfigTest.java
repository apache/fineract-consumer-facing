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

import org.apache.fineract.consumer.infrastructure.configs.JwtProperties;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import static org.assertj.core.api.Assertions.assertThat;

class OAuth2AuthorizationServerConfigTest {

    private static final String ISSUER = "https://bff.example";

    private final OAuth2AuthorizationServerConfig config = new OAuth2AuthorizationServerConfig();

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
}
