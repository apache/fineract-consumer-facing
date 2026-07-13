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

package org.apache.fineract.consumer.infrastructure.access.service;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.ActionPolicies;
import org.apache.fineract.consumer.infrastructure.access.data.ActionPolicy;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.data.ResourceType;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessKycRequiredException;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessPolicyMissingException;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.infrastructure.web.UserClientResolver;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessPolicyEvaluator {

    private final ClientApi clientApi;
    private final UserClientResolver userClientResolver;

    public void authorize(Jwt jwt, ConsumerAction action) {
        authorize(jwt, action, null);
    }

    public void authorize(Jwt jwt, ConsumerAction action, Long resourceId) {
        ActionPolicy policy = ActionPolicies.forAction(action)
                .orElseThrow(AccessPolicyMissingException::new);
        if (!policy.getRequiredScope().equals(jwt.getClaimAsString(JwtClaims.SCOPE))) {
            throw new AccessScopeInsufficientException();
        }
        if (policy.isRequiresKycVerified()
                && !Boolean.TRUE.equals(jwt.getClaimAsBoolean(JwtClaims.KYC_VERIFIED))) {
            throw new AccessKycRequiredException();
        }
        if (policy.getOwnership() != null && !ownsResource(jwt, policy.getOwnership(), resourceId)) {
            throw policy.getDenialException().get();
        }
    }

    public boolean ownsResource(Jwt jwt, ResourceType resourceType, Long resourceId) {
        Long clientId = userClientResolver.resolveClientId(jwt);
        return switch (resourceType) {
            case SAVINGS -> canAccessSavings(clientId, resourceId);
            case LOANS -> canAccessLoans(clientId, resourceId);
        };
    }

    private boolean canAccessSavings(Long clientId, Long savingsId) {
        if (savingsId == null) {
            return false;
        }
        GetClientsClientIdAccountsResponse accounts = clientApi.retrieveAllClientAccounts(clientId);
        if (accounts == null || accounts.getSavingsAccounts() == null) {
            return false;
        }
        Set<Long> ids = accounts.getSavingsAccounts().stream()
                .map(GetClientsSavingsAccounts::getId)
                .collect(Collectors.toSet());
        return ids.contains(savingsId);
    }

    private boolean canAccessLoans(Long clientId, Long loanId) {
        if (loanId == null) {
            return false;
        }
        GetClientsClientIdAccountsResponse accounts = clientApi.retrieveAllClientAccounts(clientId);
        if (accounts == null || accounts.getLoanAccounts() == null) {
            return false;
        }
        Set<Long> ids = accounts.getLoanAccounts().stream()
                .map(GetClientsLoanAccounts::getId)
                .collect(Collectors.toSet());
        return ids.contains(loanId);
    }
}
