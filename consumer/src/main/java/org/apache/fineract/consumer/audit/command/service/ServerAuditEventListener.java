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

package org.apache.fineract.consumer.audit.command.service;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.consumer.audit.command.domain.AuditEvent;
import org.apache.fineract.consumer.audit.command.repository.AuditEventCommandRepository;
import org.apache.fineract.consumer.infrastructure.audit.annotation.NonTransactionalAuditListener;
import org.apache.fineract.consumer.infrastructure.audit.annotation.TransactionalAuditListener;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.NonTransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.audit.data.TransactionalAuditEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerAuditEventListener {

    private final AuditEventCommandRepository auditEventCommandRepository;
    private final ObjectMapper objectMapper;

    @TransactionalAuditListener
    public void onTransactionalEvent(TransactionalAuditEvent event) {
        persist(event.getEventUuid(), event.getEventType(),
                event.getUserId(), event.isUnknownPrincipal(), event.getDeviceFingerprint(), event.getDetails());
    }

    @NonTransactionalAuditListener
    public void onNonTransactionalEvent(NonTransactionalAuditEvent event) {
        persist(event.getEventUuid(), event.getEventType(),
                event.getUserId(), event.isUnknownPrincipal(), event.getDeviceFingerprint(), event.getDetails());
    }

    private void persist(UUID eventUuid, AuditEventType eventType, Long userId,
            boolean unknownPrincipal, String deviceFingerprint, Map<String, Object> details) {
        try {
            AuditEvent entity = AuditEvent.forServerEvent(eventUuid, eventType, eventType.getSeverity(), userId,
                    unknownPrincipal, deviceFingerprint, serialize(details));
            auditEventCommandRepository.save(entity);
        } catch (DuplicateKeyException e) {
            log.debug("audit event {} already recorded; duplicate ignored", eventUuid);
        } catch (RuntimeException e) {
            log.warn("audit event {} ({}) could not be recorded", eventUuid, eventType, e);
        }
    }

    private String serialize(Map<String, Object> details) {
        return details == null || details.isEmpty() ? null : objectMapper.writeValueAsString(details);
    }
}
