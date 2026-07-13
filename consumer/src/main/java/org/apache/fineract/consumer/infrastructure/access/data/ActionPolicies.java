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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.exception.AbstractConsumerException;
import org.apache.fineract.consumer.loans.command.exception.LoanCommandAccessDeniedException;
import org.apache.fineract.consumer.loans.query.exception.LoanQueryAccessDeniedException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandAccessDeniedException;
import org.apache.fineract.consumer.savings.query.exception.SavingsQueryAccessDeniedException;
import org.apache.fineract.consumer.transfers.command.exception.TransferAccessDeniedException;

public final class ActionPolicies {

    private ActionPolicies() {
    }

    private static final Map<ConsumerAction, ActionPolicy> POLICIES = Stream.of(
            unowned(ConsumerAction.SAVINGS_LIST),
            unowned(ConsumerAction.SAVINGS_APPLICATION_TEMPLATE_VIEW),
            unowned(ConsumerAction.LOANS_LIST),
            unowned(ConsumerAction.LOAN_SCHEDULE_CALCULATE),
            unowned(ConsumerAction.LOAN_APPLICATION_TEMPLATE_VIEW),
            unowned(ConsumerAction.LOAN_APPLICATION_SUBMIT),
            unowned(ConsumerAction.BENEFICIARY_LIST),
            unowned(ConsumerAction.BENEFICIARY_ADD),
            unowned(ConsumerAction.BENEFICIARY_MODIFY),
            unowned(ConsumerAction.BENEFICIARY_DELETE),
            owned(ConsumerAction.SAVINGS_VIEW, ResourceType.SAVINGS, SavingsQueryAccessDeniedException::new),
            owned(ConsumerAction.SAVINGS_CHARGE_PAY, ResourceType.SAVINGS, SavingsCommandAccessDeniedException::new),
            owned(ConsumerAction.LOANS_VIEW, ResourceType.LOANS, LoanQueryAccessDeniedException::new),
            owned(ConsumerAction.LOAN_CHARGE_PAY, ResourceType.LOANS, LoanCommandAccessDeniedException::new),
            owned(ConsumerAction.LOAN_APPLICATION_MODIFY, ResourceType.LOANS, LoanCommandAccessDeniedException::new),
            owned(ConsumerAction.LOAN_APPLICATION_WITHDRAW, ResourceType.LOANS, LoanCommandAccessDeniedException::new),
            owned(ConsumerAction.TRANSFER_EXECUTE, ResourceType.SAVINGS, TransferAccessDeniedException::new))
            .collect(Collectors.toUnmodifiableMap(ActionPolicy::getAction, Function.identity()));

    public static Optional<ActionPolicy> forAction(ConsumerAction action) {
        return Optional.ofNullable(POLICIES.get(action));
    }

    private static ActionPolicy owned(ConsumerAction action, ResourceType ownership,
            Supplier<AbstractConsumerException> denialException) {
        return ActionPolicy.builder()
                .action(action)
                .requiredScope(AuthenticationConstants.SCOPE_CONSUMER_FULL)
                .requiresKycVerified(true)
                .ownership(ownership)
                .denialException(denialException)
                .build();
    }

    private static ActionPolicy unowned(ConsumerAction action) {
        return ActionPolicy.builder()
                .action(action)
                .requiredScope(AuthenticationConstants.SCOPE_CONSUMER_FULL)
                .requiresKycVerified(true)
                .build();
    }
}
