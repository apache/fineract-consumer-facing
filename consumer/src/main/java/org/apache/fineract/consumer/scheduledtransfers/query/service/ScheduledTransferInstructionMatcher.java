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
import java.util.List;
import java.util.Optional;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetPageItemsStandingInstructionSwagger;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferQueryConstants;
import org.apache.fineract.consumer.scheduledtransfers.query.domain.ScheduledTransferQueryEntity;

/** Finds the Fineract instruction a scheduled transfer row stands for, and whether it still runs. */
final class ScheduledTransferInstructionMatcher {

    private ScheduledTransferInstructionMatcher() {
    }

    /**
     * The instruction a row stands for. A linked row names it by id. A row stays unlinked when confirm's outcome was
     * unknown and nobody retried; Fineract may still have created the instruction, under the name derived from the
     * row's publicId, so it is matched by name. Either way the instruction must debit the row's source account.
     */
    static Optional<GetPageItemsStandingInstructionSwagger> instructionOf(ScheduledTransferQueryEntity transfer,
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
    static boolean isActive(GetPageItemsStandingInstructionSwagger instruction, LocalDate today) {
        boolean statusActive = instruction.getStatus() != null && instruction.getStatus().getId() != null
                && instruction.getStatus().getId() == ScheduledTransferQueryConstants.ACTIVE_STATUS_ID;
        return statusActive && (instruction.getValidTill() == null || instruction.getValidTill().isAfter(today));
    }
}
