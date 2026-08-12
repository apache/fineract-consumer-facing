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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2ConsentData;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2ConsentLookupPort;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;

@RequiredArgsConstructor
public class OAuth2RefreshTokenConsentValidator implements AuthenticationProvider {

    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2ConsentLookupPort consentLookup;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2RefreshTokenAuthenticationToken refreshAuthentication =
                (OAuth2RefreshTokenAuthenticationToken) authentication;
        OAuth2Authorization authorization = authorizationService
                .findByToken(refreshAuthentication.getRefreshToken(), OAuth2TokenType.REFRESH_TOKEN);
        if (authorization != null) {
            requireAuthorisedConsent(authorization);
        }
        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    private void requireAuthorisedConsent(OAuth2Authorization authorization) {
        UUID consentId = OAuth2ConsentIdExtractor.consentIdOf(authorization);
        OAuth2ConsentData consent = consentId != null
                ? consentLookup.findConsent(consentId).orElse(null)
                : null;
        if (consent == null
                || !consent.isAuthorised()
                || !consent.getExpiresAt().isAfter(Instant.now())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
    }
}
