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

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2ConsentData;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2ConsentLookupPort;
import org.apache.fineract.consumer.openbanking.query.data.ConsentStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenBankingConsentLookupAdapter implements OAuth2ConsentLookupPort {

    private final OpenBankingConsentQueryService openBankingConsentQueryService;

    @Override
    public Optional<OAuth2ConsentData> findConsent(UUID consentId) {
        return openBankingConsentQueryService.findConsent(consentId)
                .map(consent -> OAuth2ConsentData.builder()
                        .tppClientId(consent.getTppClientId())
                        .expiresAt(consent.getExpirationDateTime())
                        .awaitingAuthorisation(consent.getStatus() == ConsentStatus.AWAITING_AUTHORISATION)
                        .authorised(consent.getStatus() == ConsentStatus.AUTHORISED)
                        .build());
    }
}
