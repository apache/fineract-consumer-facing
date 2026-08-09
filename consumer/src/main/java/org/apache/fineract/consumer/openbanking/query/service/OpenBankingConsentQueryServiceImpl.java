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

package org.apache.fineract.consumer.openbanking.query.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingConsentQueryData;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingTppConsentQueryData;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingUserConsentQueryData;
import org.apache.fineract.consumer.openbanking.query.domain.OpenBankingConsentQueryEntity;
import org.apache.fineract.consumer.openbanking.query.exception.OpenBankingConsentQueryNotFoundException;
import org.apache.fineract.consumer.openbanking.query.repository.OpenBankingConsentQueryRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OpenBankingConsentQueryServiceImpl implements OpenBankingConsentQueryService {

    private final OpenBankingConsentQueryRepository openBankingConsentQueryRepository;
    private final AccessPolicyEvaluator accessPolicyEvaluator;

    @Override
    @Transactional(readOnly = true)
    public Optional<OpenBankingConsentQueryData> findConsent(UUID consentId) {
        return openBankingConsentQueryRepository.findById(consentId).map(OpenBankingConsentQueryServiceImpl::toQueryData);
    }

    @Override
    @Transactional(readOnly = true)
    public OpenBankingTppConsentQueryData getConsentForTppClient(Jwt jwt, UUID consentId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.OPENBANKING_TPP_CONSENT_VIEW);
        String tppClientId = jwt.getSubject();
        return openBankingConsentQueryRepository.findById(consentId)
                .filter(consent -> consent.getTppClientId().equals(tppClientId))
                .map(OpenBankingConsentQueryServiceImpl::toTppQueryData)
                .orElseThrow(OpenBankingConsentQueryNotFoundException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpenBankingUserConsentQueryData> listConsentsForUser(Jwt jwt) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.OPENBANKING_CONSENT_VIEW);
        UUID userId = UUID.fromString(jwt.getSubject());
        return openBankingConsentQueryRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(OpenBankingConsentQueryServiceImpl::toUserQueryData)
                .toList();
    }

    private static OpenBankingConsentQueryData toQueryData(OpenBankingConsentQueryEntity entity) {
        return OpenBankingConsentQueryData.builder()
                .consentId(entity.getId())
                .tppClientId(entity.getTppClientId())
                .userId(entity.getUserId())
                .permissions(entity.getPermissions())
                .status(entity.getStatus())
                .creationDateTime(entity.getCreatedAt())
                .expirationDateTime(entity.getExpiresAt())
                .statusUpdateDateTime(entity.getStatusUpdatedAt())
                .build();
    }

    private static OpenBankingTppConsentQueryData toTppQueryData(OpenBankingConsentQueryEntity entity) {
        return OpenBankingTppConsentQueryData.builder()
                .consentId(entity.getId())
                .permissions(entity.getPermissions())
                .status(entity.getStatus())
                .creationDateTime(entity.getCreatedAt())
                .expirationDateTime(entity.getExpiresAt())
                .statusUpdateDateTime(entity.getStatusUpdatedAt())
                .build();
    }

    private static OpenBankingUserConsentQueryData toUserQueryData(OpenBankingConsentQueryEntity entity) {
        return OpenBankingUserConsentQueryData.builder()
                .consentId(entity.getId())
                .tppClientId(entity.getTppClientId())
                .permissions(entity.getPermissions())
                .status(entity.getStatus())
                .creationDateTime(entity.getCreatedAt())
                .expirationDateTime(entity.getExpiresAt())
                .build();
    }
}
