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

package org.apache.fineract.consumer.openbanking.command.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.TransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.configs.OAuth2ConsumerProperties;
import org.apache.fineract.consumer.openbanking.command.data.ConsentStatus;
import org.apache.fineract.consumer.openbanking.command.data.CreateOpenBankingConsentCommand;
import org.apache.fineract.consumer.openbanking.command.data.OpenBankingConsentCommandData;
import org.apache.fineract.consumer.openbanking.command.data.OpenBankingPermission;
import org.apache.fineract.consumer.openbanking.command.domain.OpenBankingConsent;
import org.apache.fineract.consumer.openbanking.command.exception.OpenBankingConsentCommandNotFoundException;
import org.apache.fineract.consumer.openbanking.command.exception.OpenBankingConsentPermissionInvalidException;
import org.apache.fineract.consumer.openbanking.command.repository.OpenBankingConsentCommandRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OpenBankingConsentCommandServiceImpl implements OpenBankingConsentCommandService {

    private static final String DETAIL_CONSENT_ID = "consentId";
    private static final String DETAIL_TPP_CLIENT_ID = "tppClientId";
    private static final String DETAIL_PERMISSIONS = "permissions";
    private static final String DETAIL_USER_PUBLIC_ID = "userPublicId";

    private final OpenBankingConsentCommandRepository openBankingConsentCommandRepository;
    private final OAuth2ConsumerProperties oauth2Properties;
    private final ApplicationEventPublisher eventPublisher;
    private final AccessPolicyEvaluator accessPolicyEvaluator;

    @Override
    @Transactional
    public OpenBankingConsentCommandData create(Jwt jwt, CreateOpenBankingConsentCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.OPENBANKING_TPP_CONSENT_CREATE);
        Set<OpenBankingPermission> permissions = command.getPermissions();
        if (permissions == null || permissions.isEmpty()) {
            throw new OpenBankingConsentPermissionInvalidException();
        }
        OpenBankingConsent consent = OpenBankingConsent.createAwaitingAuthorisation(UUID.randomUUID(),
                jwt.getSubject(), permissions, oauth2Properties.getConsentTtl());
        openBankingConsentCommandRepository.save(consent);
        publishConsentCreated(consent);
        return toCommandData(consent);
    }

    @Override
    @Transactional
    public void authorise(UUID consentId, UUID userId) {
        OpenBankingConsent consent = requireConsent(consentId);
        consent.authorise(userId);
        openBankingConsentCommandRepository.save(consent);
    }

    @Override
    @Transactional
    public void reject(UUID consentId, UUID userId) {
        OpenBankingConsent consent = requireConsent(consentId);
        consent.reject(userId);
        openBankingConsentCommandRepository.save(consent);
    }

    @Override
    @Transactional
    public OpenBankingConsentCommandData revoke(Jwt jwt, UUID consentId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.OPENBANKING_CONSENT_REVOKE);
        UUID userId = UUID.fromString(jwt.getSubject());
        OpenBankingConsent consent = openBankingConsentCommandRepository.findById(consentId)
                .filter(candidate -> userId.equals(candidate.getUserId()))
                .orElseThrow(OpenBankingConsentCommandNotFoundException::new);
        consent.revoke();
        openBankingConsentCommandRepository.save(consent);
        publishConsentRevoked(consent);
        return toCommandData(consent);
    }

    private static OpenBankingConsentCommandData toCommandData(OpenBankingConsent consent) {
        return OpenBankingConsentCommandData.builder()
                .consentId(consent.getId())
                .status(consent.getStatus())
                .permissions(consent.getPermissions())
                .creationDateTime(consent.getCreatedAt())
                .expirationDateTime(consent.getExpiresAt())
                .build();
    }

    private OpenBankingConsent requireConsent(UUID consentId) {
        return openBankingConsentCommandRepository.findById(consentId)
                .orElseThrow(OpenBankingConsentCommandNotFoundException::new);
    }

    private void publishConsentCreated(OpenBankingConsent consent) {
        Map<String, Object> details = new HashMap<>();
        details.put(DETAIL_CONSENT_ID, consent.getId().toString());
        details.put(DETAIL_TPP_CLIENT_ID, consent.getTppClientId());
        details.put(DETAIL_PERMISSIONS, permissionCodes(consent.getPermissions()));
        eventPublisher.publishEvent(TransactionalAuditEvent.of(AuditEventType.CONSENT_CREATED,
                null, false, null, details));
    }

    private void publishConsentRevoked(OpenBankingConsent consent) {
        Map<String, Object> details = new HashMap<>();
        details.put(DETAIL_CONSENT_ID, consent.getId().toString());
        details.put(DETAIL_TPP_CLIENT_ID, consent.getTppClientId());
        details.put(DETAIL_USER_PUBLIC_ID, consent.getUserId().toString());
        eventPublisher.publishEvent(TransactionalAuditEvent.of(AuditEventType.CONSENT_REVOKED,
                null, false, null, details));
    }

    private static Set<String> permissionCodes(Set<OpenBankingPermission> permissions) {
        return permissions.stream().map(OpenBankingPermission::getCode).collect(Collectors.toSet());
    }
}
