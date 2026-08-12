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

package org.apache.fineract.consumer.openbanking.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.fineract.consumer.openbanking.command.data.ConsentStatus;
import org.apache.fineract.consumer.openbanking.command.data.OpenBankingPermission;
import org.apache.fineract.consumer.openbanking.command.exception.OpenBankingConsentNotRevocableException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "open_banking_consents")
@Getter
@NoArgsConstructor
public class OpenBankingConsent {

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

    public static OpenBankingConsent createAwaitingAuthorisation(UUID id, String tppClientId,
            Set<OpenBankingPermission> permissions, Duration ttl) {
        Instant now = Instant.now();
        OpenBankingConsent consent = new OpenBankingConsent();
        consent.id = id;
        consent.tppClientId = tppClientId;
        consent.permissions = EnumSet.copyOf(permissions);
        consent.status = ConsentStatus.AWAITING_AUTHORISATION;
        consent.createdAt = now;
        consent.expiresAt = now.plus(ttl);
        consent.statusUpdatedAt = now;
        return consent;
    }

    public void authorise(UUID userId) {
        requireAwaitingAuthorisation();
        this.userId = userId;
        transitionTo(ConsentStatus.AUTHORISED);
    }

    public void reject(UUID userId) {
        requireAwaitingAuthorisation();
        this.userId = userId;
        transitionTo(ConsentStatus.REJECTED);
    }

    public void revoke() {
        if (status != ConsentStatus.AUTHORISED) {
            throw new OpenBankingConsentNotRevocableException();
        }
        transitionTo(ConsentStatus.REVOKED);
    }

    private void requireAwaitingAuthorisation() {
        if (status != ConsentStatus.AWAITING_AUTHORISATION) {
            throw new IllegalStateException("consent is " + status + ", expected AWAITING_AUTHORISATION");
        }
    }

    private void transitionTo(ConsentStatus newStatus) {
        this.status = newStatus;
        this.statusUpdatedAt = Instant.now();
    }
}
