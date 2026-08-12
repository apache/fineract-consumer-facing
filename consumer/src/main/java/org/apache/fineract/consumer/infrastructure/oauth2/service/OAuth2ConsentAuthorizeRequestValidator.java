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
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2ConsentData;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2ConsentLookupPort;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;

@RequiredArgsConstructor
public class OAuth2ConsentAuthorizeRequestValidator
        implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {

    private static final String CONSENT_ID_ERROR_DESCRIPTION =
            "OAuth 2.0 Parameter: " + OAuth2Constants.CONSENT_ID;

    private final OAuth2ConsentLookupPort consentLookup;

    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken authentication = context.getAuthentication();
        UUID consentId = OAuth2ConsentIdExtractor
                .parse(authentication.getAdditionalParameters().get(OAuth2Constants.CONSENT_ID));
        if (consentId == null || !isAuthorizable(consentId, context.getRegisteredClient().getClientId())) {
            throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                    new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, CONSENT_ID_ERROR_DESCRIPTION, null),
                    authentication);
        }
    }

    private boolean isAuthorizable(UUID consentId, String requestingTppClientId) {
        OAuth2ConsentData consent = consentLookup.findConsent(consentId).orElse(null);
        return consent != null
                && consent.getTppClientId().equals(requestingTppClientId)
                && consent.isAwaitingAuthorisation()
                && consent.getExpiresAt().isAfter(Instant.now());
    }
}
