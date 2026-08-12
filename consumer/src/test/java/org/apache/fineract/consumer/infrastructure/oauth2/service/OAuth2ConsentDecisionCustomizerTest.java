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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.NonTransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2ConsentRecorderPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class OAuth2ConsentDecisionCustomizerTest {

    private static final String REGISTERED_CLIENT_ID = "registered-client-id";
    private static final String TPP_CLIENT_ID = "demo-tpp";
    private static final String REDIRECT_URI = "https://tpp.example/callback";
    private static final String STATE = "state-value";
    private static final UUID CONSENT_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("9b7d4c2a-0000-4000-8000-000000000002");
    private static final Set<String> APPROVED_SCOPES = Set.of(
            AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS,
            AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ);

    @Mock
    private OAuth2ConsentRecorderPort consentRecorder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void approvedScopesAuthoriseConsentAndPublishConsentGranted() {
        customizer().accept(context(authorizationCarryingConsentId(), APPROVED_SCOPES));

        verify(consentRecorder).authorise(CONSENT_ID, USER_ID);
        verifyNoMoreInteractions(consentRecorder);
        NonTransactionalAuditEvent event = capturedEvent();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.CONSENT_GRANTED);
        assertThat(event.getDetails())
                .containsEntry("consentId", CONSENT_ID.toString())
                .containsEntry("tppClientId", TPP_CLIENT_ID)
                .containsEntry("scopes", APPROVED_SCOPES)
                .containsEntry("userPublicId", USER_ID.toString());
    }

    @Test
    void noApprovedScopesRejectConsentAndPublishConsentDenied() {
        customizer().accept(context(authorizationCarryingConsentId(), Set.of()));

        verify(consentRecorder).reject(CONSENT_ID, USER_ID);
        verifyNoMoreInteractions(consentRecorder);
        NonTransactionalAuditEvent event = capturedEvent();
        assertThat(event.getEventType()).isEqualTo(AuditEventType.CONSENT_DENIED);
        assertThat(event.getDetails())
                .containsEntry("consentId", CONSENT_ID.toString())
                .containsEntry("tppClientId", TPP_CLIENT_ID)
                .containsEntry("userPublicId", USER_ID.toString())
                .doesNotContainKey("scopes");
    }

    @Test
    void grantWithoutConsentIdIsStoreOnly() {
        customizer().accept(context(authorizationWithoutConsentId(), APPROVED_SCOPES));

        verifyNoInteractions(consentRecorder, eventPublisher);
    }

    @Test
    void denialWithoutConsentIdIsIgnored() {
        customizer().accept(context(authorizationWithoutConsentId(), Set.of()));

        verifyNoInteractions(consentRecorder, eventPublisher);
    }

    private OAuth2ConsentDecisionCustomizer customizer() {
        return new OAuth2ConsentDecisionCustomizer(consentRecorder, eventPublisher);
    }

    private NonTransactionalAuditEvent capturedEvent() {
        ArgumentCaptor<NonTransactionalAuditEvent> captor =
                ArgumentCaptor.forClass(NonTransactionalAuditEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    private static OAuth2AuthorizationConsentAuthenticationContext context(OAuth2Authorization authorization,
            Set<String> approvedScopes) {
        OAuth2AuthorizationConsentAuthenticationToken consentAuthentication =
                new OAuth2AuthorizationConsentAuthenticationToken(
                        "https://bff.example" + OAuth2Constants.AUTHORIZATION_ENDPOINT,
                        TPP_CLIENT_ID, new TestingAuthenticationToken(USER_ID.toString(), "n/a"),
                        STATE, approvedScopes, Map.of());
        OAuth2AuthorizationConsent.Builder consentBuilder =
                OAuth2AuthorizationConsent.withId(REGISTERED_CLIENT_ID, USER_ID.toString());
        approvedScopes.forEach(consentBuilder::scope);
        return OAuth2AuthorizationConsentAuthenticationContext.with(consentAuthentication)
                .authorizationConsent(consentBuilder)
                .registeredClient(registeredClient())
                .authorization(authorization)
                .authorizationRequest(authorizationRequest(false))
                .build();
    }

    private static OAuth2Authorization authorizationCarryingConsentId() {
        return authorization(authorizationRequest(true));
    }

    private static OAuth2Authorization authorizationWithoutConsentId() {
        return authorization(authorizationRequest(false));
    }

    private static OAuth2Authorization authorization(OAuth2AuthorizationRequest authorizationRequest) {
        return OAuth2Authorization.withRegisteredClient(registeredClient())
                .principalName(USER_ID.toString())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(OAuth2AuthorizationRequest.class.getName(), authorizationRequest)
                .build();
    }

    private static OAuth2AuthorizationRequest authorizationRequest(boolean withConsentId) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://bff.example" + OAuth2Constants.AUTHORIZATION_ENDPOINT)
                .clientId(TPP_CLIENT_ID)
                .redirectUri(REDIRECT_URI)
                .additionalParameters(parameters -> {
                    if (withConsentId) {
                        parameters.put(OAuth2Constants.CONSENT_ID, CONSENT_ID.toString());
                    }
                })
                .build();
    }

    private static RegisteredClient registeredClient() {
        return RegisteredClient.withId(REGISTERED_CLIENT_ID)
                .clientId(TPP_CLIENT_ID)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(REDIRECT_URI)
                .build();
    }
}
