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

package org.apache.fineract.consumer.infrastructure.oauth2.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.fineract.consumer.infrastructure.oauth2.data.RegisteredTppStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "registered_tpps")
@Getter
@NoArgsConstructor
public class RegisteredTpp {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false, updatable = false, length = 100)
    private String clientId;

    @Column(name = "client_secret_hash", nullable = false, length = 200)
    private String clientSecretHash;

    @Column(name = "client_name", nullable = false, length = 200)
    private String clientName;

    @Column(name = "redirect_uri", nullable = false, length = 500)
    private String redirectUri;

    @Column(name = "scopes", nullable = false, length = 1000)
    private String scopes;

    @Column(name = "status", nullable = false, columnDefinition = "registered_tpp_status")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private RegisteredTppStatus status;

    @Column(name = "onboarded_at", nullable = false, updatable = false)
    private Instant onboardedAt;

    @Column(name = "status_updated_at", nullable = false)
    private Instant statusUpdatedAt;

    public static RegisteredTpp onboard(UUID id, String clientId, String clientSecretHash, String clientName,
            String redirectUri, String scopes) {
        Instant now = Instant.now();
        RegisteredTpp tpp = new RegisteredTpp();
        tpp.id = id;
        tpp.clientId = clientId;
        tpp.clientSecretHash = clientSecretHash;
        tpp.clientName = clientName;
        tpp.redirectUri = redirectUri;
        tpp.scopes = scopes;
        tpp.status = RegisteredTppStatus.ACTIVE;
        tpp.onboardedAt = now;
        tpp.statusUpdatedAt = now;
        return tpp;
    }

    public void revoke() {
        this.status = RegisteredTppStatus.REVOKED;
        this.statusUpdatedAt = Instant.now();
    }

    public boolean isActive() {
        return status == RegisteredTppStatus.ACTIVE;
    }
}
