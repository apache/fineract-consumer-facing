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

package org.apache.fineract.consumer.openbanking.query.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.fineract.consumer.openbanking.query.data.ConsentStatus;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingPermission;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name = "OpenBankingConsentQueryEntity")
@Table(name = "open_banking_consents")
@Immutable
@Getter
@NoArgsConstructor
public class OpenBankingConsentQueryEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tpp_client_id", nullable = false, updatable = false, length = 100)
    private String tppClientId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "permissions", nullable = false, updatable = false)
    @Convert(converter = OpenBankingPermissionSetConverter.class)
    private Set<OpenBankingPermission> permissions;

    @Column(name = "status", nullable = false, columnDefinition = "open_banking_consent_status")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private ConsentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "status_updated_at", nullable = false)
    private Instant statusUpdatedAt;

    public static OpenBankingConsentQueryEntity of(UUID id, String tppClientId, UUID userId,
            Set<OpenBankingPermission> permissions, ConsentStatus status, Instant createdAt, Instant expiresAt,
            Instant statusUpdatedAt) {
        OpenBankingConsentQueryEntity entity = new OpenBankingConsentQueryEntity();
        entity.id = id;
        entity.tppClientId = tppClientId;
        entity.userId = userId;
        entity.permissions = EnumSet.copyOf(permissions);
        entity.status = status;
        entity.createdAt = createdAt;
        entity.expiresAt = expiresAt;
        entity.statusUpdatedAt = statusUpdatedAt;
        return entity;
    }
}
