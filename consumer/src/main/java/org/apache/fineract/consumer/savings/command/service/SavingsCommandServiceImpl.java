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

package org.apache.fineract.consumer.savings.command.service;

import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.OwnedAccountsCache;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsAccountIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsAccountIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutSavingsAccountsAccountIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutSavingsAccountsAccountIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.FineractCaller;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyDeriver;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyHolder;
import org.apache.fineract.consumer.savings.command.data.ModifySavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.SavingsApplicationCommandData;
import org.apache.fineract.consumer.savings.command.data.SubmitSavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.WithdrawSavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.exception.SavingsApplicationInvalidException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandAccessDeniedException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandInProgressException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandNotFoundException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandUpstreamUnavailableException;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsCommandServiceImpl implements SavingsCommandService {

    private static final String LOCALE = "en";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private final UserQueryService userQueryService;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final OwnedAccountsCache ownedAccountsCache;
    private final IdempotencyKeyHolder idempotencyKeyHolder;
    private final SavingsAccountApi savingsAccountApi;

    @Override
    public SavingsApplicationCommandData submitApplication(Jwt jwt, SubmitSavingsApplicationCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_APPLICATION_SUBMIT);
        Long clientId = resolveClientId(jwt);
        PostSavingsAccountsRequest request = buildSubmitRequest(command, clientId);
        PostSavingsAccountsResponse response = callWithIdempotency(jwt, command.getIdempotencyKey(),
                () -> savingsAccountApi.submitSavingsApplication(request));
        ownedAccountsCache.evict(clientId);
        return SavingsApplicationCommandData.builder()
                .savingsId(response.getSavingsId())
                .resourceId(response.getResourceId())
                .clientId(clientId)
                .build();
    }

    @Override
    public SavingsApplicationCommandData modifyApplication(Jwt jwt, ModifySavingsApplicationCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_APPLICATION_MODIFY, command.getSavingsId(),
                SavingsCommandAccessDeniedException::new);
        Long clientId = resolveClientId(jwt);
        PutSavingsAccountsAccountIdRequest request = buildModifyRequest(command);
        PutSavingsAccountsAccountIdResponse response = callWithIdempotency(jwt, command.getIdempotencyKey(),
                () -> savingsAccountApi.updateSavingsAccount(command.getSavingsId(), request, null));
        return SavingsApplicationCommandData.builder()
                .savingsId(command.getSavingsId())
                .resourceId(response.getResourceId())
                .clientId(clientId)
                .build();
    }

    @Override
    public SavingsApplicationCommandData withdrawApplication(Jwt jwt, WithdrawSavingsApplicationCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_APPLICATION_WITHDRAW, command.getSavingsId(),
                SavingsCommandAccessDeniedException::new);
        Long clientId = resolveClientId(jwt);
        PostSavingsAccountsAccountIdRequest request = new PostSavingsAccountsAccountIdRequest()
                .withdrawnOnDate(command.getWithdrawnOnDate().toString())
                .note(command.getNote())
                .dateFormat(DATE_FORMAT)
                .locale(LOCALE);
        PostSavingsAccountsAccountIdResponse response = callWithIdempotency(jwt, command.getIdempotencyKey(),
                () -> savingsAccountApi.handleCommandsSavingsAccount(command.getSavingsId(), request, WITHDRAW_COMMAND));
        return SavingsApplicationCommandData.builder()
                .savingsId(command.getSavingsId())
                .resourceId(response.getResourceId())
                .clientId(clientId)
                .build();
    }

    private Long resolveClientId(Jwt jwt) {
        UUID publicId = UUID.fromString(jwt.getSubject());
        UserQueryData user = userQueryService.findByPublicId(publicId);
        return user.getFineractClientId();
    }

    private <T> T callWithIdempotency(Jwt jwt, String rawKey, Supplier<T> upstream) {
        UUID publicId = UUID.fromString(jwt.getSubject());
        idempotencyKeyHolder.set(IdempotencyKeyDeriver.derive(publicId, rawKey));
        try {
            return call(upstream);
        } finally {
            idempotencyKeyHolder.clear();
        }
    }

    private <T> T call(Supplier<T> upstream) {
        return FineractCaller.call(upstream,
                e -> new SavingsCommandNotFoundException(),
                SavingsApplicationInvalidException::new,
                SavingsCommandInProgressException::new,
                SavingsCommandUpstreamUnavailableException::new);
    }

    private PostSavingsAccountsRequest buildSubmitRequest(SubmitSavingsApplicationCommand c, Long clientId) {
        return new PostSavingsAccountsRequest()
                .clientId(clientId)
                .productId(c.getProductId())
                .submittedOnDate(c.getSubmittedOnDate().toString())
                .dateFormat(DATE_FORMAT)
                .locale(LOCALE);
    }

    private PutSavingsAccountsAccountIdRequest buildModifyRequest(ModifySavingsApplicationCommand c) {
        return new PutSavingsAccountsAccountIdRequest()
                .productId(c.getProductId())
                .submittedOnDate(c.getSubmittedOnDate().toString())
                .dateFormat(DATE_FORMAT)
                .locale(LOCALE);
    }
}
