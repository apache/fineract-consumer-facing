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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.configs.OAuth2ConsumerProperties;
import org.apache.fineract.consumer.infrastructure.oauth2.domain.RegisteredTpp;
import org.apache.fineract.consumer.infrastructure.oauth2.repository.RegisteredTppRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegisteredTppClientAdapter implements RegisteredClientRepository {

    private static final String SCOPE_DELIMITER = ",";

    private final RegisteredTppRepository registeredTppRepository;
    private final OAuth2ConsumerProperties oauth2Properties;

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException(
                "registered_tpps rows are onboarded out-of-band; dynamic client registration is not enabled");
    }

    @Override
    public RegisteredClient findById(String id) {
        return parseTppId(id)
                .flatMap(registeredTppRepository::findById)
                .filter(RegisteredTpp::isActive)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return registeredTppRepository.findByClientId(clientId)
                .filter(RegisteredTpp::isActive)
                .map(this::toRegisteredClient)
                .orElse(null);
    }

    private RegisteredClient toRegisteredClient(RegisteredTpp tpp) {
        return RegisteredClient.withId(tpp.getId().toString())
                .clientId(tpp.getClientId())
                .clientSecret(tpp.getClientSecretHash())
                .clientName(tpp.getClientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(tpp.getRedirectUri())
                .scopes(scopes -> scopes.addAll(parseScopes(tpp.getScopes())))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(true)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(oauth2Properties.getAccessTokenTtl())
                        .refreshTokenTimeToLive(oauth2Properties.getRefreshTokenTtl())
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }

    private static Optional<UUID> parseTppId(String id) {
        try {
            return Optional.ofNullable(id).map(UUID::fromString);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Set<String> parseScopes(String scopes) {
        return Arrays.stream(scopes.split(SCOPE_DELIMITER))
                .map(String::trim)
                .filter(scope -> !scope.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
