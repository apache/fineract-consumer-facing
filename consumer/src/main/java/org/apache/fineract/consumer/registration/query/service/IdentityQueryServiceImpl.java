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

package org.apache.fineract.consumer.registration.query.service;

import feign.FeignException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientIdentifierApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.ClientIdentifierData;
import org.apache.fineract.consumer.infrastructure.query.Query;
import org.apache.fineract.consumer.registration.query.data.IdentityVerificationQuery;
import org.apache.fineract.consumer.registration.query.data.IdentityVerificationQueryData;
import org.apache.fineract.consumer.registration.query.exception.IdentityVerificationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdentityQueryServiceImpl implements IdentityQueryService {

    private final ClientIdentifierApi identifiersClient;

    @Override
    @Query
    public IdentityVerificationQueryData verifyIdentity(IdentityVerificationQuery query) {
        String typeName = query.getDocumentTypeName();
        String normalizedKey = normalize(query.getDocumentKey());
        return fetchIdentifiers(query.getFineractClientId()).stream()
                .filter(i -> matchesDocumentType(i, typeName))
                .filter(i -> matchesNormalizedKey(i, normalizedKey))
                .findFirst()
                .map(i -> IdentityVerificationQueryData.verified(lastFour(i.getDocumentKey())))
                .orElseGet(IdentityVerificationQueryData::denied);
    }

    private List<ClientIdentifierData> fetchIdentifiers(Long clientId) {
        try {
            List<ClientIdentifierData> result = identifiersClient.retrieveAllClientIdentifiers(clientId);
            return result == null ? List.of() : result;
        } catch (FeignException.NotFound e) {
            return List.of();
        } catch (FeignException e) {
            throw new IdentityVerificationException(e);
        }
    }

    private static boolean matchesDocumentType(ClientIdentifierData i, String typeName) {
        return i.getDocumentType() != null && typeName.equals(i.getDocumentType().getName());
    }

    private static boolean matchesNormalizedKey(ClientIdentifierData i, String normalizedKey) {
        return i.getDocumentKey() != null && normalize(i.getDocumentKey()).equals(normalizedKey);
    }

    private static String normalize(String input) {
        return input.trim().replace("-", "").replace(" ", "").toUpperCase();
    }

    private static String lastFour(String value) {
        String normalized = normalize(value);
        return normalized.length() < 4 ? null : normalized.substring(normalized.length() - 4);
    }
}
