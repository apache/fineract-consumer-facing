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

package org.apache.fineract.consumer.scheduledtransfers.command.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.scheduledtransfers.command.domain.ScheduledTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduledTransferCommandRepository extends JpaRepository<ScheduledTransfer, Long> {

    Optional<ScheduledTransfer> findByIdempotencyKey(String idempotencyKey);

    Optional<ScheduledTransfer> findByPublicIdAndUserId(UUID publicId, Long userId);

    /**
     * Claims an unlinked row whose claim is absent or has lapsed, in one statement, so of two confirms only one
     * succeeds. Returns the number of rows claimed: 1 or 0.
     */
    @Modifying
    @Query("""
            UPDATE ScheduledTransfer t SET t.claimedUntil = :until, t.updatedAt = :now
            WHERE t.id = :id AND t.fineractStandingInstructionId IS NULL
              AND (t.claimedUntil IS NULL OR t.claimedUntil < :now)
            """)
    int claim(@Param("id") Long id, @Param("until") Instant until, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE ScheduledTransfer t SET t.claimedUntil = NULL WHERE t.id = :id")
    int releaseClaim(@Param("id") Long id);

    /**
     * Links an unlinked row no confirm is working on, in one statement, so it cannot race a confirm claiming the same
     * row. Returns the number of rows linked: 1 or 0.
     */
    @Modifying
    @Query("""
            UPDATE ScheduledTransfer t
            SET t.fineractStandingInstructionId = :standingInstructionId, t.claimedUntil = NULL, t.updatedAt = :now
            WHERE t.id = :id AND t.fineractStandingInstructionId IS NULL
              AND (t.claimedUntil IS NULL OR t.claimedUntil < :now)
            """)
    int linkUnclaimed(@Param("id") Long id, @Param("standingInstructionId") Long standingInstructionId,
            @Param("now") Instant now);
}
