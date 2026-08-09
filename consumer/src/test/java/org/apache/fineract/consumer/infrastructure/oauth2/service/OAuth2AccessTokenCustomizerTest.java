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

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.access.data.PrincipalUserData;
import org.apache.fineract.consumer.infrastructure.access.repository.PrincipalUserLookupPort;
import org.apache.fineract.consumer.infrastructure.fineractclient.configs.FineractClientProperties;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.infrastructure.kyc.service.ClientStandingChecker;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

@ExtendWith(MockitoExtension.class)
class OAuth2AccessTokenCustomizerTest {

    private static final String TENANT_ID = "default";
    private static final String TPP_CLIENT_ID = "demo-tpp";
    private static final String REDIRECT_URI = "https://tpp.example/callback";
    private static final UUID CONSENT_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final UUID USER_PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000002");
    private static final Long USER_ID = 7L;
    private static final Long FINERACT_CLIENT_ID = 11L;

    @Mock
    private PrincipalUserLookupPort principalUserLookup;

    @Mock
    private ClientStandingChecker clientStandingChecker;

    private OAuth2AccessTokenCustomizer customizer;

    private final JwsHeader.Builder jwsHeaderBuilder = JwsHeader.with(SignatureAlgorithm.RS256);
    private final JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder().subject(USER_PUBLIC_ID.toString());

    @BeforeEach
    void setUp() {
        customizer = new OAuth2AccessTokenCustomizer(
                new FineractClientProperties("http://fineract", "mifos", "password", TENANT_ID),
                principalUserLookup, clientStandingChecker);
    }

    @Test
    void authorizationCodeAccessTokenGetsPurposeTenantScopeAndConsentId() {
        stubUser(unboundUser());

        customizer.customize(accessTokenContext(AuthorizationGrantType.AUTHORIZATION_CODE)
                .principal(userPrincipal())
                .authorization(authorizationCarryingConsentId())
                .build());

        JwtClaimsSet claims = claimsBuilder.build();
        assertThat(claims.getClaims())
                .containsEntry(JwtClaims.PURPOSE, OAuth2Constants.OPENBANKING_PURPOSE_VALUE)
                .containsEntry(JwtClaims.TENANT, TENANT_ID)
                .containsEntry(JwtClaims.SCOPE, AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS + " "
                        + AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ)
                .containsEntry(OAuth2Constants.CONSENT_ID, CONSENT_ID.toString())
                .doesNotContainKey(JwtClaims.KYC_VERIFIED);
    }

    @Test
    void accessTokenJwsHeaderPinnedToEs256() {
        stubUser(unboundUser());

        customizer.customize(accessTokenContext(AuthorizationGrantType.AUTHORIZATION_CODE)
                .principal(userPrincipal())
                .authorization(authorizationCarryingConsentId())
                .build());

        assertThat(jwsHeaderBuilder.build().getAlgorithm()).isEqualTo(SignatureAlgorithm.ES256);
    }

    @Test
    void kycVerifiedStampedWhenBoundClientIsActive() {
        stubUser(boundUser());
        when(clientStandingChecker.isActive(FINERACT_CLIENT_ID)).thenReturn(true);

        customizer.customize(accessTokenContext(AuthorizationGrantType.AUTHORIZATION_CODE)
                .principal(userPrincipal())
                .authorization(authorizationCarryingConsentId())
                .build());

        assertThat(claimsBuilder.build().getClaims()).containsEntry(JwtClaims.KYC_VERIFIED, true);
    }

    @Test
    void grantRejectedWhenBoundClientIsNotActive() {
        stubUser(boundUser());
        when(clientStandingChecker.isActive(FINERACT_CLIENT_ID)).thenReturn(false);

        JwtEncodingContext context = accessTokenContext(AuthorizationGrantType.AUTHORIZATION_CODE)
                .principal(userPrincipal())
                .authorization(authorizationCarryingConsentId())
                .build();

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
    }

    @Test
    void neverBoundUserGetsNoClaimAndNoStandingCheck() {
        stubUser(unboundUser());

        customizer.customize(accessTokenContext(AuthorizationGrantType.AUTHORIZATION_CODE)
                .principal(userPrincipal())
                .authorization(authorizationCarryingConsentId())
                .build());

        assertThat(claimsBuilder.build().getClaims()).doesNotContainKey(JwtClaims.KYC_VERIFIED);
        verifyNoInteractions(clientStandingChecker);
    }

    @Test
    void clientCredentialsTokenGetsNoConsentIdOrKycAndNoLookup() {
        customizer.customize(accessTokenContext(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .principal(userPrincipal())
                .build());

        JwtClaimsSet claims = claimsBuilder.build();
        assertThat(claims.getClaims())
                .containsEntry(JwtClaims.PURPOSE, OAuth2Constants.OPENBANKING_PURPOSE_VALUE)
                .containsEntry(JwtClaims.TENANT, TENANT_ID)
                .doesNotContainKey(OAuth2Constants.CONSENT_ID)
                .doesNotContainKey(JwtClaims.KYC_VERIFIED);
        verifyNoInteractions(principalUserLookup, clientStandingChecker);
    }

    @Test
    void refreshGrantRestampsConsentIdAndReChecksStanding() {
        stubUser(boundUser());
        when(clientStandingChecker.isActive(FINERACT_CLIENT_ID)).thenReturn(true);

        customizer.customize(accessTokenContext(AuthorizationGrantType.REFRESH_TOKEN)
                .principal(userPrincipal())
                .authorization(authorizationCarryingConsentId())
                .build());

        assertThat(claimsBuilder.build().getClaims())
                .containsEntry(OAuth2Constants.CONSENT_ID, CONSENT_ID.toString())
                .containsEntry(JwtClaims.KYC_VERIFIED, true);
    }

    @Test
    void consentIdOmittedWhenAuthorizationDoesNotCarryOne() {
        stubUser(unboundUser());
        OAuth2Authorization authorizationWithoutRequest = OAuth2Authorization
                .withRegisteredClient(registeredClient())
                .principalName(USER_PUBLIC_ID.toString())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();

        customizer.customize(accessTokenContext(AuthorizationGrantType.AUTHORIZATION_CODE)
                .principal(userPrincipal())
                .authorization(authorizationWithoutRequest)
                .build());

        assertThat(claimsBuilder.build().getClaims()).doesNotContainKey(OAuth2Constants.CONSENT_ID);
    }

    @Test
    void nonAccessTokensAreLeftUntouched() {
        customizer.customize(context(OAuth2TokenType.REFRESH_TOKEN, AuthorizationGrantType.AUTHORIZATION_CODE)
                .principal(userPrincipal())
                .authorization(authorizationCarryingConsentId())
                .build());

        assertThat(claimsBuilder.build().getClaims())
                .doesNotContainKey(JwtClaims.PURPOSE)
                .doesNotContainKey(JwtClaims.TENANT)
                .doesNotContainKey(JwtClaims.SCOPE)
                .doesNotContainKey(OAuth2Constants.CONSENT_ID);
        assertThat(jwsHeaderBuilder.build().getAlgorithm()).isEqualTo(SignatureAlgorithm.RS256);
        verifyNoInteractions(principalUserLookup, clientStandingChecker);
    }

    private void stubUser(PrincipalUserData user) {
        when(principalUserLookup.findByPublicId(USER_PUBLIC_ID)).thenReturn(Optional.of(user));
    }

    private static PrincipalUserData boundUser() {
        return new PrincipalUserData(USER_ID, FINERACT_CLIENT_ID, true);
    }

    private static PrincipalUserData unboundUser() {
        return new PrincipalUserData(USER_ID, FINERACT_CLIENT_ID, false);
    }

    private JwtEncodingContext.Builder accessTokenContext(AuthorizationGrantType grantType) {
        return context(OAuth2TokenType.ACCESS_TOKEN, grantType);
    }

    private JwtEncodingContext.Builder context(OAuth2TokenType tokenType, AuthorizationGrantType grantType) {
        Set<String> scopes = new LinkedHashSet<>();
        scopes.add(AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS);
        scopes.add(AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ);
        return JwtEncodingContext.with(jwsHeaderBuilder, claimsBuilder)
                .registeredClient(registeredClient())
                .tokenType(tokenType)
                .authorizationGrantType(grantType)
                .authorizedScopes(scopes);
    }

    private static Authentication userPrincipal() {
        return new TestingAuthenticationToken(USER_PUBLIC_ID.toString(), "n/a");
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
                .principalName(USER_PUBLIC_ID.toString())
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
