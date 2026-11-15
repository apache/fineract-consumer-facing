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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.savings.command.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.OwnedAccountsCache;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsAccountIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsAccountIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutSavingsAccountsAccountIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutSavingsAccountsAccountIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.BusinessDateFineractClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.FineractCaller;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.SavingsCommandFineractClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.SavingsFineractClient;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyDeriver;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyHolder;
import org.apache.fineract.consumer.savings.command.data.ModifySavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.SavingsApplicationCommandData;
import org.apache.fineract.consumer.savings.command.data.SavingsCommandProperties;
import org.apache.fineract.consumer.savings.command.data.SubmitSavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.WithdrawSavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.exception.SavingsApplicationInvalidException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandAccessDeniedException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandInProgressException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandNotFoundException;
import org.apache.fineract.consumer.savings.command.exception.SavingsCommandUpstreamUnavailableException;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(SavingsCommandProperties.class)
public class SavingsCommandServiceImpl implements SavingsCommandService {

    private static final String LOCALE = "en";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final Long SAVINGS_DEPOSIT_TYPE_ID = 100L;

    private final UserQueryService userQueryService;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final OwnedAccountsCache ownedAccountsCache;
    private final IdempotencyKeyHolder idempotencyKeyHolder;
    private final SavingsCommandFineractClient savingsCommandFineractClient;
    private final SavingsFineractClient savingsFineractClient;
    private final BusinessDateFineractClient businessDateFineractClient;
    private final SavingsCommandProperties properties;
    private final Clock clock;

    public SavingsCommandServiceImpl(
            UserQueryService userQueryService,
            AccessPolicyEvaluator accessPolicyEvaluator,
            OwnedAccountsCache ownedAccountsCache,
            IdempotencyKeyHolder idempotencyKeyHolder,
            SavingsCommandFineractClient savingsCommandFineractClient,
            SavingsFineractClient savingsFineractClient,
            BusinessDateFineractClient businessDateFineractClient,
            SavingsCommandProperties properties,
            @Autowired(required = false) Clock clock) {
        this.userQueryService = userQueryService;
        this.accessPolicyEvaluator = accessPolicyEvaluator;
        this.ownedAccountsCache = ownedAccountsCache;
        this.idempotencyKeyHolder = idempotencyKeyHolder;
        this.savingsCommandFineractClient = savingsCommandFineractClient;
        this.savingsFineractClient = savingsFineractClient;
        this.businessDateFineractClient = businessDateFineractClient;
        this.properties = properties;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public SavingsApplicationCommandData submitApplication(Jwt jwt, SubmitSavingsApplicationCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_APPLICATION_SUBMIT);
        validateProductAllowList(command.getProductId());
        UUID publicId = resolvePublicId(jwt);
        Long clientId = resolveClientId(publicId);
        LocalDate submittedOnDate = resolveToday();
        PostSavingsAccountsRequest request = buildSubmitRequest(command, clientId, submittedOnDate);
        PostSavingsAccountsResponse response = callWithIdempotency(publicId, command.getIdempotencyKey(),
                () -> savingsCommandFineractClient.submitSavingsApplication(request));
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
        validateProductAllowList(command.getProductId());
        validateSavingsDepositType(command.getSavingsId());
        UUID publicId = resolvePublicId(jwt);
        Long clientId = resolveClientId(publicId);
        PutSavingsAccountsAccountIdRequest request = buildModifyRequest(command);
        PutSavingsAccountsAccountIdResponse response = callWithIdempotency(publicId, command.getIdempotencyKey(),
                () -> savingsCommandFineractClient.updateSavingsAccount(command.getSavingsId(), request, null));
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
        validateSavingsDepositType(command.getSavingsId());
        UUID publicId = resolvePublicId(jwt);
        Long clientId = resolveClientId(publicId);
        LocalDate withdrawnOnDate = resolveToday();
        PostSavingsAccountsAccountIdRequest request = new PostSavingsAccountsAccountIdRequest()
                .withdrawnOnDate(withdrawnOnDate.toString())
                .note(command.getNote())
                .dateFormat(DATE_FORMAT)
                .locale(LOCALE);
        PostSavingsAccountsAccountIdResponse response = callWithIdempotency(publicId, command.getIdempotencyKey(),
                () -> savingsCommandFineractClient.handleCommandsSavingsAccount(command.getSavingsId(), request, WITHDRAW_COMMAND));
        return SavingsApplicationCommandData.builder()
                .savingsId(command.getSavingsId())
                .resourceId(response.getResourceId())
                .clientId(clientId)
                .build();
    }

    private void validateSavingsDepositType(Long savingsId) {
        SavingsAccountData account = call(() -> savingsFineractClient.getAccount(savingsId));
        Long typeId = account.getDepositType() == null ? null : account.getDepositType().getId();
        if (!SAVINGS_DEPOSIT_TYPE_ID.equals(typeId)) {
            throw new SavingsApplicationInvalidException();
        }
    }

    private LocalDate resolveToday() {
        LocalDate date = call(businessDateFineractClient::today);
        return date != null ? date : LocalDate.now(clock);
    }

    private UUID resolvePublicId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private Long resolveClientId(UUID publicId) {
        UserQueryData user = userQueryService.findByPublicId(publicId);
        return user.getFineractClientId();
    }

    private <T> T callWithIdempotency(UUID publicId, String rawKey, Supplier<T> upstream) {
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

    private void validateProductAllowList(Long productId) {
        Set<Long> allowed = properties.getAllowedProductIds();
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(productId)) {
            throw new SavingsApplicationInvalidException();
        }
    }

    private PostSavingsAccountsRequest buildSubmitRequest(SubmitSavingsApplicationCommand c, Long clientId,
            LocalDate submittedOnDate) {
        return new PostSavingsAccountsRequest()
                .clientId(clientId)
                .productId(c.getProductId())
                .submittedOnDate(submittedOnDate.toString())
                .dateFormat(DATE_FORMAT)
                .locale(LOCALE);
    }

    private PutSavingsAccountsAccountIdRequest buildModifyRequest(ModifySavingsApplicationCommand c) {
        return new PutSavingsAccountsAccountIdRequest()
                .productId(c.getProductId())
                .dateFormat(DATE_FORMAT)
                .locale(LOCALE);
    }
}
