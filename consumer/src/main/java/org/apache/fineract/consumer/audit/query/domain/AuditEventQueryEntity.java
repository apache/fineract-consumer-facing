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

package org.apache.fineract.consumer.audit.query.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.fineract.consumer.audit.query.data.AuditEventSource;
import org.apache.fineract.consumer.audit.query.data.AuditEventType;
import org.apache.fineract.consumer.audit.query.data.AuditSeverity;
import org.hibernate.annotations.Immutable;

@Entity(name = "AuditEventQueryEntity")
@Table(name = "audit_events")
@Immutable
@Getter
@NoArgsConstructor
public class AuditEventQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

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

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public static AuditEventQueryEntity of(AuditEventSource source, AuditEventType eventType,
            AuditSeverity severity, Long userId, Instant receivedAt) {
        AuditEventQueryEntity entity = new AuditEventQueryEntity();
        entity.source = source;
        entity.eventType = eventType;
        entity.severity = severity;
        entity.userId = userId;
        entity.receivedAt = receivedAt;
        return entity;
    }
}
