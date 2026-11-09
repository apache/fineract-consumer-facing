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
package org.apache.fineract.consumer.scheduledtransfers.query.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetPageItemsStandingInstructionSwagger;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetStandingInstructionHistoryPageItemsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.FineractCaller;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClient;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferFrequency;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferQueryConstants;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferQueryData;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunHistoryQuery;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunQueryData;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunQueryResponse;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunStatus;
import org.apache.fineract.consumer.scheduledtransfers.query.domain.ScheduledTransferQueryEntity;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryAccessDeniedException;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryDisabledException;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryInvalidException;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryNotFoundException;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryUpstreamUnavailableException;
import org.apache.fineract.consumer.scheduledtransfers.query.repository.ScheduledTransferQueryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduledTransfersQueryServiceImpl implements ScheduledTransfersQueryService {

    // Checked per call rather than with @ConditionalOnProperty: AOT evaluates bean conditions at build time, so a
    // native image would ignore the runtime setting.
    private static final String ENABLED_PROPERTY = "${consumer.scheduled-transfers.enabled:false}";

    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final UserClientResolver userClientResolver;
    private final StandingInstructionFineractClient standingInstructionFineractClient;
    private final ScheduledTransferQueryRepository scheduledTransferQueryRepository;
    private final boolean enabled;

    public ScheduledTransfersQueryServiceImpl(AccessPolicyEvaluator accessPolicyEvaluator,
            UserClientResolver userClientResolver, StandingInstructionFineractClient standingInstructionFineractClient,
            ScheduledTransferQueryRepository scheduledTransferQueryRepository,
            @Value(ENABLED_PROPERTY) boolean enabled) {
        this.accessPolicyEvaluator = accessPolicyEvaluator;
        this.userClientResolver = userClientResolver;
        this.standingInstructionFineractClient = standingInstructionFineractClient;
        this.scheduledTransferQueryRepository = scheduledTransferQueryRepository;
        this.enabled = enabled;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduledTransferQueryData> listScheduledTransfers(Jwt jwt) {
        requireEnabled();
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SCHEDULED_TRANSFER_LIST);
        List<ScheduledTransferQueryEntity> transfers = scheduledTransferQueryRepository
                .findAllByUserIdOrderByCreatedAtDescIdDesc(userClientResolver.resolveUserId(jwt));
        if (transfers.isEmpty()) {
            return List.of();
        }
        transfers.stream()
                .map(ScheduledTransferQueryEntity::getFineractFromAccountId)
                .distinct()
                .forEach(accountId -> authorizeSourceAccount(jwt, accountId));

        List<GetPageItemsStandingInstructionSwagger> instructions =
                clientInstructions(userClientResolver.resolveClientId(jwt));
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        List<ScheduledTransferQueryData> active = new ArrayList<>();
        for (ScheduledTransferQueryEntity transfer : transfers) {
            instructionOf(transfer, instructions)
                    .filter(instruction -> isActive(instruction, today))
                    .flatMap(instruction -> toQueryData(transfer, instruction))
                    .ifPresent(active::add);
        }
        return active;
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduledTransferRunQueryResponse listRuns(Jwt jwt, ScheduledTransferRunHistoryQuery query) {
        requireEnabled();
        requireValidPage(query.getPage(), query.getSize());
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SCHEDULED_TRANSFER_LIST);
        // Another customer's transfer is reported as missing, so its existence is not revealed.
        ScheduledTransferQueryEntity transfer = scheduledTransferQueryRepository
                .findByPublicIdAndUserId(query.getPublicId(), userClientResolver.resolveUserId(jwt))
                .orElseThrow(ScheduledTransferQueryNotFoundException::new);
        Long sourceAccountId = transfer.getFineractFromAccountId();
        authorizeSourceAccount(jwt, sourceAccountId);

        Long clientId = userClientResolver.resolveClientId(jwt);
        Long instructionId = transfer.getFineractStandingInstructionId() != null
                ? transfer.getFineractStandingInstructionId()
                : instructionOf(transfer, clientInstructions(clientId))
                        .map(GetPageItemsStandingInstructionSwagger::getId)
                        .orElse(null);
        if (instructionId == null) {
            return toRunPage(List.of(), query);
        }
        List<GetStandingInstructionHistoryPageItemsResponse> history = fetch(
                () -> standingInstructionFineractClient.listSavingsAccountRunHistory(clientId, sourceAccountId));
        List<ScheduledTransferRunQueryData> runs = new ArrayList<>();
        for (GetStandingInstructionHistoryPageItemsResponse run : history) {
            if (instructionId.equals(run.getStandingInstructionId())) {
                toRunData(run).ifPresent(runs::add);
            }
        }
        // Fineract lists runs oldest first. Reversing before the stable sort keeps the latest of a day's runs first.
        Collections.reverse(runs);
        runs.sort(Comparator.comparing(ScheduledTransferRunQueryData::getDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return toRunPage(runs, query);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ScheduledTransferQueryDisabledException();
        }
    }

    private static void requireValidPage(Integer page, Integer size) {
        if (page == null || size == null || page < 0 || size < 1
                || size > ScheduledTransferQueryConstants.MAX_PAGE_SIZE) {
            throw new ScheduledTransferQueryInvalidException();
        }
    }

    private void authorizeSourceAccount(Jwt jwt, Long accountId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_VIEW, accountId,
                ScheduledTransferQueryAccessDeniedException::new);
    }

    private List<GetPageItemsStandingInstructionSwagger> clientInstructions(Long clientId) {
        return fetch(() -> standingInstructionFineractClient.listClientStandingInstructions(clientId));
    }

    /**
     * The instruction a row stands for. A linked row names it by id. A row stays unlinked when confirm's outcome was
     * unknown and nobody retried; Fineract may still have created the instruction, under the name derived from the
     * row's publicId, so it is matched by name. Either way the instruction must debit the row's source account.
     */
    private static Optional<GetPageItemsStandingInstructionSwagger> instructionOf(ScheduledTransferQueryEntity transfer,
            List<GetPageItemsStandingInstructionSwagger> instructions) {
        Long linkedId = transfer.getFineractStandingInstructionId();
        String name = ScheduledTransferQueryConstants.INSTRUCTION_NAME_PREFIX + transfer.getPublicId();
        return instructions.stream()
                .filter(instruction -> linkedId != null
                        ? linkedId.equals(instruction.getId())
                        : name.equals(instruction.getName()))
                .filter(instruction -> instruction.getFromAccount() != null
                        && transfer.getFineractFromAccountId().equals(instruction.getFromAccount().getId()))
                .findFirst();
    }

    /** Fineract keeps an ended instruction active; its validTill is exclusive, so it ends the day before. */
    private static boolean isActive(GetPageItemsStandingInstructionSwagger instruction, LocalDate today) {
        boolean statusActive = instruction.getStatus() != null && instruction.getStatus().getId() != null
                && instruction.getStatus().getId() == ScheduledTransferQueryConstants.ACTIVE_STATUS_ID;
        return statusActive && (instruction.getValidTill() == null || instruction.getValidTill().isAfter(today));
    }

    private static Optional<ScheduledTransferQueryData> toQueryData(ScheduledTransferQueryEntity transfer,
            GetPageItemsStandingInstructionSwagger instruction) {
        return frequencyOf(instruction).map(frequency -> ScheduledTransferQueryData.builder()
                .publicId(transfer.getPublicId())
                .fromAccountId(transfer.getFineractFromAccountId())
                .toAccountId(transfer.getFineractToAccountId())
                .amount(instruction.getAmount())
                .frequency(frequency)
                .startDate(instruction.getValidFrom())
                .endDate(instruction.getValidTill() == null ? null : instruction.getValidTill().minusDays(1))
                .build());
    }

    private static Optional<ScheduledTransferFrequency> frequencyOf(
            GetPageItemsStandingInstructionSwagger instruction) {
        Integer id = instruction.getRecurrenceFrequency() == null ? null : instruction.getRecurrenceFrequency().getId();
        if (id == null) {
            return Optional.empty();
        }
        return switch (id) {
            case ScheduledTransferQueryConstants.WEEKS_FREQUENCY_ID -> Optional.of(ScheduledTransferFrequency.WEEKLY);
            case ScheduledTransferQueryConstants.MONTHS_FREQUENCY_ID -> Optional.of(ScheduledTransferFrequency.MONTHLY);
            default -> Optional.empty();
        };
    }

    /** Fineract's error log is never passed on: only the date, the amount moved, and the outcome. */
    private static Optional<ScheduledTransferRunQueryData> toRunData(
            GetStandingInstructionHistoryPageItemsResponse run) {
        if (ScheduledTransferQueryConstants.RUN_STATUS_SUCCESS.equals(run.getStatus())) {
            return Optional.of(ScheduledTransferRunQueryData.builder()
                    .date(run.getExecutionTime())
                    .amount(run.getAmount())
                    .status(ScheduledTransferRunStatus.SUCCEEDED)
                    .build());
        }
        if (ScheduledTransferQueryConstants.RUN_STATUS_FAILED.equals(run.getStatus())) {
            return Optional.of(ScheduledTransferRunQueryData.builder()
                    .date(run.getExecutionTime())
                    .status(ScheduledTransferRunStatus.FAILED)
                    .build());
        }
        return Optional.empty();
    }

    private static ScheduledTransferRunQueryResponse toRunPage(List<ScheduledTransferRunQueryData> runs,
            ScheduledTransferRunHistoryQuery query) {
        int page = query.getPage();
        int size = query.getSize();
        return ScheduledTransferRunQueryResponse.builder()
                .content(runs.stream().skip((long) page * size).limit(size).toList())
                .page(page)
                .size(size)
                .totalElements(runs.size())
                .totalPages((int) Math.ceil(runs.size() / (double) size))
                .build();
    }

    private <T> T fetch(Supplier<T> upstream) {
        return FineractCaller.call(upstream,
                ScheduledTransferQueryUpstreamUnavailableException::new,
                ScheduledTransferQueryUpstreamUnavailableException::new,
                ScheduledTransferQueryUpstreamUnavailableException::new);
    }
}
