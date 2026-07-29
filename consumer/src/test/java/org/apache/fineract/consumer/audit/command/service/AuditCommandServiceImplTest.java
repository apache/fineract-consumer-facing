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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.fineract.consumer.audit.command.data.AuditEventSource;
import org.apache.fineract.consumer.audit.command.data.AuditEventsSubmittedCommandData;
import org.apache.fineract.consumer.audit.command.data.AuditEventCommandRequest;
import org.apache.fineract.consumer.audit.command.data.SubmitAuditEventsCommandRequest;
import org.apache.fineract.consumer.audit.command.domain.AuditEvent;
import org.apache.fineract.consumer.audit.command.exception.AuditBatchTooLargeException;
import org.apache.fineract.consumer.audit.command.repository.AuditEventCommandRepository;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditSeverity;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AuditCommandServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long USER_ID = 7L;
    private static final String DEVICE_FINGERPRINT = "device-abc";
    private static final Instant OCCURRED_AT = Instant.parse("2026-07-23T10:00:00Z");
    private static final UUID EVENT_UUID = UUID.fromString("6b1d4f2a-9d2b-4c47-8a1e-b6f2d9c4e7aa");
    private static final UUID OTHER_EVENT_UUID = UUID.fromString("6b1d4f2a-9d2b-4c47-8a1e-b6f2d9c4e7ab");
    private static final int MAX_BATCH_SIZE = 3;
    private static final int MAX_DETAILS_BYTES = 128;
    private static final String JWT_TOKEN_VALUE = "token";
    private static final String JWT_ALG_HEADER = "alg";
    private static final String JWT_ALG_NONE = "none";
    private static final String DETAILS_VIEW_KEY = "view";
    private static final String DETAILS_VIEW_VALUE = "BALANCE";
    private static final String DETAILS_MESSAGE_KEY = "message";
    private static final String PII_BEARING_MESSAGE = "failed for user@test.com";
    private static final String UNKNOWN_EVENT_TYPE = "NOT_A_TYPE";
    private static final String MALFORMED_EVENT_UUID = "not-a-uuid";
    private static final String EXCEPTION_CODE_PROPERTY = "code";
    private static final String DUPLICATE_EVENT_UUID_MESSAGE = "duplicate event_uuid";

    @Mock
    private AuditEventCommandRepository repository;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private UserClientResolver userClientResolver;

    private AuditCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditCommandServiceImpl(repository, accessPolicyEvaluator, userClientResolver,
                new AuditPiiScreen(), JsonMapper.builder().build(), MAX_BATCH_SIZE, MAX_DETAILS_BYTES);
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue(JWT_TOKEN_VALUE)
                .header(JWT_ALG_HEADER, JWT_ALG_NONE)
                .subject(PUBLIC_ID.toString())
                .build();
    }

    private static AuditEventCommandRequest event(UUID eventUuid, String eventType, Map<String, Object> details) {
        return AuditEventCommandRequest.builder()
                .eventUuid(eventUuid == null ? null : eventUuid.toString())
                .eventType(eventType)
                .occurredAt(OCCURRED_AT)
                .details(details)
                .build();
    }

    private static SubmitAuditEventsCommandRequest batch(AuditEventCommandRequest... events) {
        return SubmitAuditEventsCommandRequest.builder()
                .events(List.of(events))
                .build();
    }

    private void stubPrincipal() {
        when(userClientResolver.resolveUserId(any(Jwt.class))).thenReturn(USER_ID);
    }

    @Nested
    class SubmitEvents {

        @Test
        void happyBatchPersistsEventsWithPrincipalIdentityAndDerivedSeverity() {
            stubPrincipal();

            AuditEventsSubmittedCommandData result = service.submitEvents(jwt(), DEVICE_FINGERPRINT, batch(
                    event(EVENT_UUID, AuditEventType.SENSITIVE_VIEW.name(), Map.of(DETAILS_VIEW_KEY, DETAILS_VIEW_VALUE)),
                    event(OTHER_EVENT_UUID, AuditEventType.CLIENT_ERROR.name(), null)));

            assertThat(result.getAccepted()).isEqualTo(2);
            assertThat(result.getRejected()).isZero();
            verify(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.AUDIT_EVENT_SUBMIT));

            ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
            verify(repository, times(2)).save(saved.capture());
            AuditEvent view = saved.getAllValues().get(0);
            assertThat(view.getEventUuid()).isEqualTo(EVENT_UUID);
            assertThat(view.getSource()).isEqualTo(AuditEventSource.CLIENT);
            assertThat(view.getEventType()).isEqualTo(AuditEventType.SENSITIVE_VIEW);
            assertThat(view.getSeverity()).isEqualTo(AuditSeverity.INFO);
            assertThat(view.getUserId()).isEqualTo(USER_ID);
            assertThat(view.getDeviceFingerprint()).isEqualTo(DEVICE_FINGERPRINT);
            assertThat(view.getOccurredAtClaimed()).isEqualTo(OCCURRED_AT);
            assertThat(view.getReceivedAt()).isNotNull();
            assertThat(view.getDetails()).contains(DETAILS_VIEW_VALUE);

            AuditEvent error = saved.getAllValues().get(1);
            assertThat(error.getSeverity()).isEqualTo(AuditSeverity.WARN);
            assertThat(error.getDetails()).isNull();
        }

        @Test
        void piiDetailsAreRejectedAndCountedWithoutFailingTheBatch() {
            stubPrincipal();

            AuditEventsSubmittedCommandData result = service.submitEvents(jwt(), DEVICE_FINGERPRINT, batch(
                    event(EVENT_UUID, AuditEventType.SENSITIVE_VIEW.name(), Map.of(DETAILS_VIEW_KEY, DETAILS_VIEW_VALUE)),
                    event(OTHER_EVENT_UUID, AuditEventType.CLIENT_ERROR.name(),
                            Map.of(DETAILS_MESSAGE_KEY, PII_BEARING_MESSAGE))));

            assertThat(result.getAccepted()).isEqualTo(1);
            assertThat(result.getRejected()).isEqualTo(1);
            verify(repository, times(1)).save(any());
        }

        @Test
        void oversizedDetailsAreRejectedAndCounted() {
            stubPrincipal();

            AuditEventsSubmittedCommandData result = service.submitEvents(jwt(), DEVICE_FINGERPRINT, batch(
                    event(EVENT_UUID, AuditEventType.CLIENT_ERROR.name(),
                            Map.of(DETAILS_MESSAGE_KEY, "x".repeat(MAX_DETAILS_BYTES + 1)))));

            assertThat(result.getAccepted()).isZero();
            assertThat(result.getRejected()).isEqualTo(1);
            verify(repository, never()).save(any());
        }

        @Test
        void serverOnlyEventTypeCannotBeForgedByClients() {
            stubPrincipal();

            AuditEventsSubmittedCommandData result = service.submitEvents(jwt(), DEVICE_FINGERPRINT, batch(
                    event(EVENT_UUID, AuditEventType.LOGIN_SUCCESS.name(), null)));

            assertThat(result.getAccepted()).isZero();
            assertThat(result.getRejected()).isEqualTo(1);
            verify(repository, never()).save(any());
        }

        @Test
        void unknownEventTypeIsRejectedAndCounted() {
            stubPrincipal();

            AuditEventsSubmittedCommandData result = service.submitEvents(jwt(), DEVICE_FINGERPRINT, batch(
                    event(EVENT_UUID, UNKNOWN_EVENT_TYPE, null)));

            assertThat(result.getAccepted()).isZero();
            assertThat(result.getRejected()).isEqualTo(1);
            verify(repository, never()).save(any());
        }

        @Test
        void malformedEventUuidIsRejectedAndCounted() {
            stubPrincipal();

            AuditEventsSubmittedCommandData result = service.submitEvents(jwt(), DEVICE_FINGERPRINT, batch(
                    AuditEventCommandRequest.builder()
                            .eventUuid(MALFORMED_EVENT_UUID)
                            .eventType(AuditEventType.NAVIGATION.name())
                            .build(),
                    event(null, AuditEventType.NAVIGATION.name(), null)));

            assertThat(result.getAccepted()).isZero();
            assertThat(result.getRejected()).isEqualTo(2);
            verify(repository, never()).save(any());
        }

        @Test
        void duplicateEventUuidCountsAsAcceptedIdempotentReplay() {
            stubPrincipal();
            doThrow(new DataIntegrityViolationException(DUPLICATE_EVENT_UUID_MESSAGE,
                    new ConstraintViolationException(DUPLICATE_EVENT_UUID_MESSAGE, new SQLException(),
                            AuditCommandServiceImpl.EVENT_UUID_UNIQUE_CONSTRAINT)))
                    .when(repository).save(any(AuditEvent.class));

            AuditEventsSubmittedCommandData result = service.submitEvents(jwt(), DEVICE_FINGERPRINT, batch(
                    event(EVENT_UUID, AuditEventType.LOGOUT.name(), null)));

            assertThat(result.getAccepted()).isEqualTo(1);
            assertThat(result.getRejected()).isZero();
        }

        @Test
        void deniedByPolicyPropagatesAndTouchesNothing() {
            doThrow(new AccessScopeInsufficientException())
                    .when(accessPolicyEvaluator)
                    .authorize(any(Jwt.class), eq(ConsumerAction.AUDIT_EVENT_SUBMIT));

            assertThatThrownBy(() -> service.submitEvents(jwt(), DEVICE_FINGERPRINT, batch(
                    event(EVENT_UUID, AuditEventType.NAVIGATION.name(), null))))
                    .isInstanceOf(AccessScopeInsufficientException.class);

            verify(repository, never()).save(any());
        }
    }

    @Nested
    class BatchLimit {

        @Test
        void batchOverMaxSizeIsRejectedWholesaleBeforePersistence() {
            AuditEventCommandRequest[] events = Collections
                    .nCopies(MAX_BATCH_SIZE + 1, event(EVENT_UUID, AuditEventType.NAVIGATION.name(), null))
                    .toArray(new AuditEventCommandRequest[0]);

            assertThatThrownBy(() -> service.submitEvents(jwt(), DEVICE_FINGERPRINT, batch(events)))
                    .isInstanceOf(AuditBatchTooLargeException.class)
                    .hasFieldOrPropertyWithValue(EXCEPTION_CODE_PROPERTY, AuditBatchTooLargeException.CODE);

            verify(repository, never()).save(any());
        }
    }
}
