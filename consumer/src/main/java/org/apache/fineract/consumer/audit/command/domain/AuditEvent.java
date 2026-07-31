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

package org.apache.fineract.consumer.audit.command.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.fineract.consumer.audit.command.data.AuditEventSource;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditSeverity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "event_uuid", nullable = false, unique = true, updatable = false)
    private UUID eventUuid;

    @Column(name = "source", nullable = false, updatable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private AuditEventSource source;

    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AuditEventType eventType;

    @Column(name = "severity", nullable = false, updatable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private AuditSeverity severity;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "unknown_principal", nullable = false, updatable = false)
    private boolean unknownPrincipal;

    @Column(name = "device_fingerprint", updatable = false, length = 100)
    private String deviceFingerprint;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @Column(name = "idempotency_key", updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "occurred_at_claimed", updatable = false)
    private Instant occurredAtClaimed;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "details", updatable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String details;

    public static AuditEvent forServerEvent(UUID eventUuid, AuditEventType eventType, AuditSeverity severity,
            Long userId, boolean unknownPrincipal, String deviceFingerprint, String details) {
        AuditEvent event = new AuditEvent();
        event.eventUuid = eventUuid;
        event.source = AuditEventSource.SERVER;
        event.eventType = eventType;
        event.severity = severity;
        event.userId = userId;
        event.unknownPrincipal = unknownPrincipal;
        event.deviceFingerprint = deviceFingerprint;
        event.details = details;
        event.receivedAt = Instant.now();
        return event;
    }

    public static AuditEvent forClientEvent(UUID eventUuid, AuditEventType eventType, AuditSeverity severity,
            Long userId, String deviceFingerprint, Instant occurredAtClaimed, String details) {
        AuditEvent event = new AuditEvent();
        event.eventUuid = eventUuid;
        event.source = AuditEventSource.CLIENT;
        event.eventType = eventType;
        event.severity = severity;
        event.userId = userId;
        event.unknownPrincipal = false;
        event.deviceFingerprint = deviceFingerprint;
        event.occurredAtClaimed = occurredAtClaimed;
        event.details = details;
        event.receivedAt = Instant.now();
        return event;
    }
}
