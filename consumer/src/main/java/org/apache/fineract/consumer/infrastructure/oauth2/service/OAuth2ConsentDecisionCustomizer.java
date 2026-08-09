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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.NonTransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2ConsentRecorderPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationContext;

@RequiredArgsConstructor
public class OAuth2ConsentDecisionCustomizer
        implements Consumer<OAuth2AuthorizationConsentAuthenticationContext> {

    private static final String DETAIL_CONSENT_ID = "consentId";
    private static final String DETAIL_TPP_CLIENT_ID = "tppClientId";
    private static final String DETAIL_SCOPES = "scopes";
    private static final String DETAIL_USER_PUBLIC_ID = "userPublicId";

    private final OAuth2ConsentRecorderPort consentRecorder;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void accept(OAuth2AuthorizationConsentAuthenticationContext context) {
        UUID consentId = OAuth2ConsentIdExtractor.consentIdOf(context.getAuthorization());
        if (consentId == null) {
            return;
        }
        UUID userId = UUID.fromString(context.getAuthorization().getPrincipalName());
        Set<GrantedAuthority> approvedAuthorities = new HashSet<>();
        context.getAuthorizationConsent().authorities(approvedAuthorities::addAll);
        if (approvedAuthorities.isEmpty()) {
            consentRecorder.reject(consentId, userId);
            publishConsentDenied(consentId, context);
        } else {
            consentRecorder.authorise(consentId, userId);
            publishConsentGranted(consentId, context);
        }
    }

    private void publishConsentGranted(UUID consentId, OAuth2AuthorizationConsentAuthenticationContext context) {
        Map<String, Object> details = baseDetails(consentId, context);
        details.put(DETAIL_SCOPES, context.getAuthorizationConsent().build().getScopes());
        eventPublisher.publishEvent(NonTransactionalAuditEvent.of(AuditEventType.CONSENT_GRANTED,
                null, false, null, details));
    }

    private void publishConsentDenied(UUID consentId, OAuth2AuthorizationConsentAuthenticationContext context) {
        eventPublisher.publishEvent(NonTransactionalAuditEvent.of(AuditEventType.CONSENT_DENIED,
                null, false, null, baseDetails(consentId, context)));
    }

    private static Map<String, Object> baseDetails(UUID consentId,
            OAuth2AuthorizationConsentAuthenticationContext context) {
        Map<String, Object> details = new HashMap<>();
        details.put(DETAIL_CONSENT_ID, consentId.toString());
        details.put(DETAIL_TPP_CLIENT_ID, context.getRegisteredClient().getClientId());
        details.put(DETAIL_USER_PUBLIC_ID, context.getAuthorization().getPrincipalName());
        return details;
    }
}
