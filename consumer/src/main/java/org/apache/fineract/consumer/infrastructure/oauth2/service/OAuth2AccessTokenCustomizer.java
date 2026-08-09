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

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.PrincipalUserData;
import org.apache.fineract.consumer.infrastructure.access.repository.PrincipalUserLookupPort;
import org.apache.fineract.consumer.infrastructure.fineractclient.configs.FineractClientProperties;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.infrastructure.kyc.service.ClientStandingChecker;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@RequiredArgsConstructor
public class OAuth2AccessTokenCustomizer implements OAuth2TokenCustomizer<JwtEncodingContext> {

    private static final String SCOPE_SEPARATOR = " ";
    private static final String KYC_REVOKED_DESCRIPTION = "identity verification no longer valid";

    private final FineractClientProperties fineractClientProperties;
    private final PrincipalUserLookupPort principalUserLookup;
    private final ClientStandingChecker clientStandingChecker;

    @Override
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        context.getJwsHeader().algorithm(SignatureAlgorithm.ES256);
        JwtClaimsSet.Builder claims = context.getClaims();
        claims.claim(JwtClaims.PURPOSE, OAuth2Constants.OPENBANKING_PURPOSE_VALUE);
        claims.claim(JwtClaims.TENANT, fineractClientProperties.getTenantId());
        claims.claim(JwtClaims.SCOPE, String.join(SCOPE_SEPARATOR, context.getAuthorizedScopes()));
        if (isUserBoundGrant(context.getAuthorizationGrantType())) {
            UUID consentId = OAuth2ConsentIdExtractor.consentIdOf(context.getAuthorization());
            if (consentId != null) {
                claims.claim(OAuth2Constants.CONSENT_ID, consentId.toString());
            }
            stampKycVerified(claims, context.getPrincipal());
        }
    }

    private void stampKycVerified(JwtClaimsSet.Builder claims, Authentication principal) {
        PrincipalUserData user = resolveUser(principal);
        if (user == null || !user.isBound()) {
            return;
        }
        if (!clientStandingChecker.isActive(user.getFineractClientId())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_GRANT, KYC_REVOKED_DESCRIPTION, null));
        }
        claims.claim(JwtClaims.KYC_VERIFIED, true);
    }

    private PrincipalUserData resolveUser(Authentication principal) {
        if (principal == null) {
            return null;
        }
        UUID publicId;
        try {
            publicId = UUID.fromString(principal.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
        return principalUserLookup.findByPublicId(publicId).orElse(null);
    }

    private static boolean isUserBoundGrant(AuthorizationGrantType grantType) {
        return AuthorizationGrantType.AUTHORIZATION_CODE.equals(grantType)
                || AuthorizationGrantType.REFRESH_TOKEN.equals(grantType);
    }
}
