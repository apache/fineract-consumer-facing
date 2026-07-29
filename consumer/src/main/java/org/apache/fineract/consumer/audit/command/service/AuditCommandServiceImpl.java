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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.consumer.audit.command.data.AuditEventCommandRequest;
import org.apache.fineract.consumer.audit.command.data.AuditEventsSubmittedCommandData;
import org.apache.fineract.consumer.audit.command.data.SubmitAuditEventsCommandRequest;
import org.apache.fineract.consumer.audit.command.domain.AuditEvent;
import org.apache.fineract.consumer.audit.command.exception.AuditBatchTooLargeException;
import org.apache.fineract.consumer.audit.command.repository.AuditEventCommandRepository;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.springframework.beans.factory.annotation.Value;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class AuditCommandServiceImpl implements AuditCommandService {

    private static final String MAX_BATCH_SIZE_PROPERTY = "${consumer.audit.max-batch-size}";
    private static final String MAX_DETAILS_BYTES_PROPERTY = "${consumer.audit.max-details-bytes}";

    static final String EVENT_UUID_UNIQUE_CONSTRAINT = "audit_events_event_uuid_key";

    private final AuditEventCommandRepository auditEventCommandRepository;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final UserClientResolver userClientResolver;
    private final AuditPiiScreen auditPiiScreen;
    private final ObjectMapper objectMapper;
    private final int maxBatchSize;
    private final int maxDetailsBytes;

    public AuditCommandServiceImpl(AuditEventCommandRepository auditEventCommandRepository,
            AccessPolicyEvaluator accessPolicyEvaluator, UserClientResolver userClientResolver,
            AuditPiiScreen auditPiiScreen, ObjectMapper objectMapper,
            @Value(MAX_BATCH_SIZE_PROPERTY) int maxBatchSize,
            @Value(MAX_DETAILS_BYTES_PROPERTY) int maxDetailsBytes) {
        this.auditEventCommandRepository = auditEventCommandRepository;
        this.accessPolicyEvaluator = accessPolicyEvaluator;
        this.userClientResolver = userClientResolver;
        this.auditPiiScreen = auditPiiScreen;
        this.objectMapper = objectMapper;
        this.maxBatchSize = maxBatchSize;
        this.maxDetailsBytes = maxDetailsBytes;
    }

    @Override
    public AuditEventsSubmittedCommandData submitEvents(Jwt jwt, String deviceFingerprint,
            SubmitAuditEventsCommandRequest request) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.AUDIT_EVENT_SUBMIT);
        if (request.getEvents().size() > maxBatchSize) {
            throw new AuditBatchTooLargeException();
        }
        Long userId = userClientResolver.resolveUserId(jwt);

        int accepted = 0;
        for (AuditEventCommandRequest event : request.getEvents()) {
            AuditEvent entity = toEntity(event, userId, deviceFingerprint);
            if (entity != null && saveDeduplicating(entity)) {
                accepted++;
            }
        }
        return AuditEventsSubmittedCommandData.builder()
                .accepted(accepted)
                .rejected(request.getEvents().size() - accepted)
                .build();
    }

    private AuditEvent toEntity(AuditEventCommandRequest event, Long userId, String deviceFingerprint) {
        UUID eventUuid = parseEventUuid(event.getEventUuid());
        AuditEventType eventType = parseClientEventType(event.getEventType());
        if (eventUuid == null || eventType == null) {
            return null;
        }
        String detailsJson = null;
        if (event.getDetails() != null) {
            detailsJson = serializeAndScreenDetails(event.getDetails());
            if (detailsJson == null) {
                return null;
            }
        }
        return AuditEvent.forClientEvent(eventUuid, eventType, eventType.getSeverity(),
                userId, deviceFingerprint, event.getOccurredAt(), detailsJson);
    }

    private boolean saveDeduplicating(AuditEvent auditEvent) {
        try {
            auditEventCommandRepository.save(auditEvent);
            return true;
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException cve
                    && EVENT_UUID_UNIQUE_CONSTRAINT.equals(cve.getConstraintName())) {
                return true;
            }
            log.warn("audit event {} rejected by database", auditEvent.getEventUuid(), e);
            return false;
        }
    }

    private String serializeAndScreenDetails(Map<String, Object> details) {
        String detailsJson;
        try {
            detailsJson = objectMapper.writeValueAsString(details);
        } catch (JacksonException e) {
            return null;
        }
        if (detailsJson.getBytes(StandardCharsets.UTF_8).length > maxDetailsBytes) {
            return null;
        }
        if (auditPiiScreen.containsPii(detailsJson)) {
            return null;
        }
        return detailsJson;
    }

    private static UUID parseEventUuid(String eventUuid) {
        if (eventUuid == null) {
            return null;
        }
        try {
            return UUID.fromString(eventUuid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static AuditEventType parseClientEventType(String eventType) {
        if (eventType == null) {
            return null;
        }
        AuditEventType parsed;
        try {
            parsed = AuditEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return parsed.isClientSubmittable() ? parsed : null;
    }
}
