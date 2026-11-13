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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.beneficiaries.command.service.BeneficiaryScheduledTransferLookupPort;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetPageItemsStandingInstructionSwagger;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.BusinessDateFineractClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClient;
import org.apache.fineract.consumer.scheduledtransfers.query.domain.ScheduledTransferQueryEntity;
import org.apache.fineract.consumer.scheduledtransfers.query.repository.ScheduledTransferQueryRepository;
import org.springframework.stereotype.Component;

/**
 * Answers the beneficiaries module's question whatever {@code consumer.scheduled-transfers.enabled} says: instructions
 * created while the feature was on keep running in Fineract after it is turned off.
 */
@Component
@RequiredArgsConstructor
public class BeneficiaryScheduledTransferLookupAdapter implements BeneficiaryScheduledTransferLookupPort {

    private final ScheduledTransferQueryRepository scheduledTransferQueryRepository;
    private final StandingInstructionFineractClient standingInstructionFineractClient;
    private final BusinessDateFineractClient businessDateFineractClient;

    @Override
    public boolean hasActiveTransfersTo(Long userId, Long fineractClientId, Long fineractAccountId) {
        return anyActiveTransfer(userId, fineractClientId, fineractAccountId, amount -> true);
    }

    @Override
    public boolean hasActiveTransfersAbove(Long userId, Long fineractClientId, Long fineractAccountId,
            BigDecimal limit) {
        return anyActiveTransfer(userId, fineractClientId, fineractAccountId,
                amount -> amount == null || amount.compareTo(limit) > 0);
    }

    private boolean anyActiveTransfer(Long userId, Long fineractClientId, Long fineractAccountId,
            Predicate<BigDecimal> amountCounts) {
        List<ScheduledTransferQueryEntity> transfers = scheduledTransferQueryRepository
                .findAllByUserIdAndFineractToAccountId(userId, fineractAccountId);
        if (transfers.isEmpty()) {
            return false;
        }
        // A confirm holding an unlinked row may be creating its instruction right now, for an amount only it knows.
        Instant now = Instant.now();
        if (transfers.stream().anyMatch(transfer -> isBeingConfirmed(transfer, now))) {
            return true;
        }

        List<GetPageItemsStandingInstructionSwagger> instructions =
                standingInstructionFineractClient.listClientStandingInstructions(fineractClientId);
        // Without Fineract's date, an instruction with an end date is taken to be still running.
        LocalDate today = businessDateFineractClient.today();
        LocalDate asOf = today == null ? LocalDate.MIN : today;
        return transfers.stream()
                .flatMap(transfer -> ScheduledTransferInstructionMatcher.instructionOf(transfer, instructions).stream())
                .filter(instruction -> ScheduledTransferInstructionMatcher.isActive(instruction, asOf))
                .anyMatch(instruction -> amountCounts.test(instruction.getAmount()));
    }

    private static boolean isBeingConfirmed(ScheduledTransferQueryEntity transfer, Instant now) {
        return transfer.getFineractStandingInstructionId() == null && transfer.getClaimedUntil() != null
                && transfer.getClaimedUntil().isAfter(now);
    }
}
