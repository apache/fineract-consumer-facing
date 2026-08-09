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

package org.apache.fineract.consumer.infrastructure.access.data;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ActionPolicies {

    private ActionPolicies() {
    }

    private static final Set<String> CONSUMER_ONLY =
            Set.of(AuthenticationConstants.SCOPE_CONSUMER_FULL);
    private static final Set<String> CONSUMER_OR_OPENBANKING =
            Set.of(AuthenticationConstants.SCOPE_CONSUMER_FULL,
                    AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ);
    private static final Set<String> OPENBANKING_ONLY =
            Set.of(AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ);
    private static final Set<String> OPENBANKING_CONSENTS_ONLY =
            Set.of(AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS);

    private static final Map<ConsumerAction, ActionPolicy> POLICIES = Stream.of(
            unowned(ConsumerAction.SAVINGS_LIST, CONSUMER_OR_OPENBANKING),
            unowned(ConsumerAction.SAVINGS_APPLICATION_TEMPLATE_VIEW, CONSUMER_ONLY),
            unowned(ConsumerAction.LOANS_LIST, CONSUMER_OR_OPENBANKING),
            unowned(ConsumerAction.LOAN_SCHEDULE_CALCULATE, CONSUMER_ONLY),
            unowned(ConsumerAction.LOAN_APPLICATION_TEMPLATE_VIEW, CONSUMER_ONLY),
            unowned(ConsumerAction.LOAN_APPLICATION_SUBMIT, CONSUMER_ONLY),
            unowned(ConsumerAction.BENEFICIARY_LIST, CONSUMER_ONLY),
            unowned(ConsumerAction.BENEFICIARY_ADD, CONSUMER_ONLY),
            unowned(ConsumerAction.BENEFICIARY_MODIFY, CONSUMER_ONLY),
            unowned(ConsumerAction.BENEFICIARY_DELETE, CONSUMER_ONLY),
            unowned(ConsumerAction.SUMMARY_VIEW, CONSUMER_ONLY),
            unowned(ConsumerAction.USER_CHARGES_LIST, CONSUMER_ONLY),
            unowned(ConsumerAction.USER_OBLIGEES_VIEW, CONSUMER_ONLY),
            unowned(ConsumerAction.OPENBANKING_CONSENT_VIEW, CONSUMER_ONLY),
            unowned(ConsumerAction.OPENBANKING_CONSENT_REVOKE, CONSUMER_ONLY),
            unowned(ConsumerAction.OPENBANKING_ACCOUNTS_VIEW, OPENBANKING_ONLY),
            unownedKycNonrequired(ConsumerAction.USER_PROFILE_VIEW, CONSUMER_ONLY),
            unownedKycNonrequired(ConsumerAction.USER_IMAGE_VIEW, CONSUMER_ONLY),
            unownedKycNonrequired(ConsumerAction.USER_PASSWORD_CHANGE, CONSUMER_ONLY),
            unownedKycNonrequired(ConsumerAction.AUDIT_EVENT_SUBMIT, CONSUMER_ONLY),
            unownedKycNonrequired(ConsumerAction.OPENBANKING_TPP_CONSENT_CREATE, OPENBANKING_CONSENTS_ONLY),
            unownedKycNonrequired(ConsumerAction.OPENBANKING_TPP_CONSENT_VIEW, OPENBANKING_CONSENTS_ONLY),
            owned(ConsumerAction.SAVINGS_VIEW, ResourceType.SAVINGS, CONSUMER_OR_OPENBANKING),
            owned(ConsumerAction.LOANS_VIEW, ResourceType.LOANS, CONSUMER_OR_OPENBANKING),
            owned(ConsumerAction.LOAN_APPLICATION_MODIFY, ResourceType.LOANS, CONSUMER_ONLY),
            owned(ConsumerAction.LOAN_APPLICATION_WITHDRAW, ResourceType.LOANS, CONSUMER_ONLY),
            owned(ConsumerAction.TRANSFER_EXECUTE, ResourceType.SAVINGS, CONSUMER_ONLY))
            .collect(Collectors.toUnmodifiableMap(ActionPolicy::getAction, Function.identity()));

    public static Optional<ActionPolicy> forAction(ConsumerAction action) {
        return Optional.ofNullable(POLICIES.get(action));
    }

    private static ActionPolicy owned(ConsumerAction action, ResourceType ownership, Set<String> allowedScopes) {
        return ActionPolicy.builder()
                .action(action)
                .allowedScopes(allowedScopes)
                .requiresKycVerified(true)
                .ownership(ownership)
                .build();
    }

    private static ActionPolicy unowned(ConsumerAction action, Set<String> allowedScopes) {
        return ActionPolicy.builder()
                .action(action)
                .allowedScopes(allowedScopes)
                .requiresKycVerified(true)
                .build();
    }

    private static ActionPolicy unownedKycNonrequired(ConsumerAction action, Set<String> allowedScopes) {
        return ActionPolicy.builder()
                .action(action)
                .allowedScopes(allowedScopes)
                .requiresKycVerified(false)
                .build();
    }
}
