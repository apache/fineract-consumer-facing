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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.ActionPolicies;
import org.apache.fineract.consumer.infrastructure.access.data.ActionPolicy;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.data.ResourceType;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessKycRequiredException;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessPolicyMissingException;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.NonTransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.exception.AbstractConsumerException;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessPolicyEvaluator {

    private static final String DETAIL_ACTION = "action";
    private static final String DETAIL_RESOURCE_ID = "resourceId";
    private static final String DETAIL_REASON = "reason";
    private static final String DETAIL_USER_PUBLIC_ID = "userPublicId";
    private static final String REASON_POLICY_MISSING = "policy_missing";
    private static final String REASON_SCOPE_INSUFFICIENT = "scope_insufficient";
    private static final String REASON_KYC_REQUIRED = "kyc_required";
    private static final String REASON_OWNERSHIP_DENIED = "ownership_denied";
    private static final String SCOPE_SEPARATOR = " ";

    private final OwnedAccountsCache ownedAccountsCache;
    private final UserClientResolver userClientResolver;
    private final ApplicationEventPublisher eventPublisher;

    public void authorize(Jwt jwt, ConsumerAction action) {
        authorize(jwt, action, null, AccessPolicyMissingException::new);
    }

    public void authorize(Jwt jwt, ConsumerAction action, Long resourceId,
            Supplier<? extends AbstractConsumerException> onDenied) {
        ActionPolicy policy = ActionPolicies.forAction(action)
                .orElseThrow(() -> {
                    publishAccessDenied(jwt, action, resourceId, REASON_POLICY_MISSING);
                    return new AccessPolicyMissingException();
                });
        if (Collections.disjoint(scopesOf(jwt), policy.getAllowedScopes())) {
            publishAccessDenied(jwt, action, resourceId, REASON_SCOPE_INSUFFICIENT);
            throw new AccessScopeInsufficientException();
        }
        if (policy.isRequiresKycVerified()
                && !Boolean.TRUE.equals(jwt.getClaimAsBoolean(JwtClaims.KYC_VERIFIED))) {
            publishAccessDenied(jwt, action, resourceId, REASON_KYC_REQUIRED);
            throw new AccessKycRequiredException();
        }
        if (policy.getOwnership() != null && !ownsResource(jwt, policy.getOwnership(), resourceId)) {
            publishAccessDenied(jwt, action, resourceId, REASON_OWNERSHIP_DENIED);
            throw onDenied.get();
        }
    }

    private void publishAccessDenied(Jwt jwt, ConsumerAction action, Long resourceId, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put(DETAIL_ACTION, action.name());
        details.put(DETAIL_REASON, reason);
        details.put(DETAIL_USER_PUBLIC_ID, jwt.getSubject());
        if (resourceId != null) {
            details.put(DETAIL_RESOURCE_ID, resourceId);
        }
        eventPublisher.publishEvent(NonTransactionalAuditEvent.of(AuditEventType.ACCESS_DENIED,
                null, false, null, details));
    }

    public static boolean hasScope(Jwt jwt, String scope) {
        return scopesOf(jwt).contains(scope);
    }

    private static Set<String> scopesOf(Jwt jwt) {
        String scope = jwt.getClaimAsString(JwtClaims.SCOPE);
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scope.split(SCOPE_SEPARATOR))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toUnmodifiableSet());
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
        return ownedAccountsCache.ownedAccounts(clientId).getSavingsIds().contains(savingsId);
    }

    private boolean canAccessLoans(Long clientId, Long loanId) {
        if (loanId == null) {
            return false;
        }
        return ownedAccountsCache.ownedAccounts(clientId).getLoanIds().contains(loanId);
    }
}
