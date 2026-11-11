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

package org.apache.fineract.consumer.scheduledtransfers.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A recurring transfer created through the BFF. Fineract holds the standing instruction and runs it; this row only
 * records who owns it, so the BFF lists and cancels nothing it did not create.
 *
 * <p>The row is reserved before Fineract is called and linked to the instruction afterwards. A confirm retried with the
 * same idempotency key finds the reserved row, sends the same key to Fineract, which replays the original instruction,
 * and links it; an instruction can therefore never exist in Fineract without a row the customer can see.
 */
@Entity
@Table(name = "scheduled_transfers")
@Getter
@NoArgsConstructor
public class ScheduledTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "fineract_from_account_id", nullable = false, updatable = false)
    private Long fineractFromAccountId;

    @Column(name = "fineract_to_account_id", nullable = false, updatable = false)
    private Long fineractToAccountId;

    @Column(name = "fineract_standing_instruction_id", unique = true)
    private Long fineractStandingInstructionId;

    /** Set while a confirm is working on an unlinked row, so a concurrent confirm with the same key waits. */
    @Column(name = "claimed_until")
    private Instant claimedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static ScheduledTransfer reserve(UUID publicId, Long userId, String idempotencyKey, Long fineractFromAccountId,
            Long fineractToAccountId, Instant claimedUntil) {
        Instant now = Instant.now();
        ScheduledTransfer transfer = new ScheduledTransfer();
        transfer.publicId = publicId;
        transfer.userId = userId;
        transfer.idempotencyKey = idempotencyKey;
        transfer.fineractFromAccountId = fineractFromAccountId;
        transfer.fineractToAccountId = fineractToAccountId;
        transfer.claimedUntil = claimedUntil;
        transfer.createdAt = now;
        transfer.updatedAt = now;
        return transfer;
    }

    public boolean isLinked() {
        return fineractStandingInstructionId != null;
    }

    public void link(Long standingInstructionId) {
        if (standingInstructionId == null) {
            throw new IllegalArgumentException("standing instruction id is required");
        }
        if (isLinked() && !fineractStandingInstructionId.equals(standingInstructionId)) {
            throw new IllegalStateException("scheduled transfer is already linked to another standing instruction");
        }
        this.fineractStandingInstructionId = standingInstructionId;
        this.claimedUntil = null;
        this.updatedAt = Instant.now();
    }
}
