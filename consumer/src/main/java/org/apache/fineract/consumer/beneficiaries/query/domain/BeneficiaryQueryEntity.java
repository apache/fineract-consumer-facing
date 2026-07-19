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

package org.apache.fineract.consumer.beneficiaries.query.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "BeneficiaryQueryEntity")
@Table(name = "beneficiaries")
@Immutable
@Getter
@NoArgsConstructor
public class BeneficiaryQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "fineract_office_id", nullable = false, updatable = false)
    private Long fineractOfficeId;

    @Column(name = "fineract_client_id", nullable = false, updatable = false)
    private Long fineractClientId;

    @Column(name = "fineract_account_id", nullable = false, updatable = false)
    private Long fineractAccountId;

    @Column(name = "account_type", nullable = false, updatable = false, columnDefinition = "beneficiary_account_type")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private BeneficiaryAccountType accountType;

    @Column(name = "transfer_limit", precision = 19, scale = 6)
    private BigDecimal transferLimit;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Test-support factory only — the application never constructs query entities by
     * hand; they are always materialized by Hibernate from the read path.
     */
    public static BeneficiaryQueryEntity of(UUID publicId, Long userId, String name, Long fineractOfficeId,
            Long fineractClientId, Long fineractAccountId, BeneficiaryAccountType accountType,
            BigDecimal transferLimit) {
        Instant now = Instant.now();
        BeneficiaryQueryEntity entity = new BeneficiaryQueryEntity();
        entity.publicId = publicId;
        entity.userId = userId;
        entity.name = name;
        entity.fineractOfficeId = fineractOfficeId;
        entity.fineractClientId = fineractClientId;
        entity.fineractAccountId = fineractAccountId;
        entity.accountType = accountType;
        entity.transferLimit = transferLimit;
        entity.active = true;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }
}
