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
package org.apache.fineract.consumer.scheduledtransfers.query.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity(name = "ScheduledTransferQueryEntity")
@Table(name = "scheduled_transfers")
@Immutable
@Getter
@NoArgsConstructor
public class ScheduledTransferQueryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "fineract_from_account_id", nullable = false, updatable = false)
    private Long fineractFromAccountId;

    @Column(name = "fineract_to_account_id", nullable = false, updatable = false)
    private Long fineractToAccountId;

    @Column(name = "fineract_standing_instruction_id", unique = true)
    private Long fineractStandingInstructionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Test-support factory only — the application never constructs query entities by hand; they are always
     * materialized by Hibernate from the read path.
     */
    public static ScheduledTransferQueryEntity of(Long id, UUID publicId, Long userId, Long fineractFromAccountId,
            Long fineractToAccountId, Long fineractStandingInstructionId) {
        ScheduledTransferQueryEntity entity = new ScheduledTransferQueryEntity();
        entity.id = id;
        entity.publicId = publicId;
        entity.userId = userId;
        entity.fineractFromAccountId = fineractFromAccountId;
        entity.fineractToAccountId = fineractToAccountId;
        entity.fineractStandingInstructionId = fineractStandingInstructionId;
        entity.createdAt = Instant.now();
        return entity;
    }
}
