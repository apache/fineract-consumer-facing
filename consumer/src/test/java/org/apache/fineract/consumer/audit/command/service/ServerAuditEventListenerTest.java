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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.apache.fineract.consumer.audit.command.data.AuditEventSource;
import org.apache.fineract.consumer.audit.command.domain.AuditEvent;
import org.apache.fineract.consumer.audit.command.repository.AuditEventCommandRepository;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditSeverity;
import org.apache.fineract.consumer.infrastructure.audit.data.TransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.audit.data.NonTransactionalAuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ServerAuditEventListenerTest {

    private static final Long USER_ID = 7L;
    private static final String DEVICE_FINGERPRINT = "test-device";

    @Mock
    private AuditEventCommandRepository auditEventCommandRepository;

    private ServerAuditEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ServerAuditEventListener(auditEventCommandRepository, JsonMapper.builder().build());
    }

    @Test
    void commandEventIsPersistedAsServerRowWithMappedFields() {
        TransactionalAuditEvent event = TransactionalAuditEvent.of(
                AuditEventType.LOGIN_SUCCESS,
                USER_ID, false, DEVICE_FINGERPRINT, Map.of("flow", "change"));

        listener.onTransactionalEvent(event);

        AuditEvent saved = capturedEntity();
        assertThat(saved.getEventUuid()).isEqualTo(event.getEventUuid());
        assertThat(saved.getSource()).isEqualTo(AuditEventSource.SERVER);
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.LOGIN_SUCCESS);
        assertThat(saved.getSeverity()).isEqualTo(AuditSeverity.INFO);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.isUnknownPrincipal()).isFalse();
        assertThat(saved.getDeviceFingerprint()).isEqualTo(DEVICE_FINGERPRINT);
        assertThat(saved.getDetails()).isEqualTo("{\"flow\":\"change\"}");
        assertThat(saved.getReceivedAt()).isNotNull();
    }

    @Test
    void queryEventIsPersistedAsServerRowWithMappedFields() {
        NonTransactionalAuditEvent event = NonTransactionalAuditEvent.of(
                AuditEventType.LOGIN_FAILURE,
                null, true, DEVICE_FINGERPRINT, Map.of("reason", "revoked"));

        listener.onNonTransactionalEvent(event);

        AuditEvent saved = capturedEntity();
        assertThat(saved.getEventUuid()).isEqualTo(event.getEventUuid());
        assertThat(saved.getSource()).isEqualTo(AuditEventSource.SERVER);
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.LOGIN_FAILURE);
        assertThat(saved.getSeverity()).isEqualTo(AuditSeverity.WARN);
        assertThat(saved.getUserId()).isNull();
        assertThat(saved.isUnknownPrincipal()).isTrue();
        assertThat(saved.getDetails()).isEqualTo("{\"reason\":\"revoked\"}");
    }

    @Test
    void nullDetailsArePersistedAsNull() {
        listener.onNonTransactionalEvent(NonTransactionalAuditEvent.of(
                AuditEventType.LOGIN_FAILURE,
                null, true, DEVICE_FINGERPRINT, null));

        assertThat(capturedEntity().getDetails()).isNull();
    }

    @Test
    void repositoryFailureIsSwallowed() {
        when(auditEventCommandRepository.save(any(AuditEvent.class)))
                .thenThrow(new RuntimeException("db unavailable"));

        assertThatCode(() -> listener.onNonTransactionalEvent(NonTransactionalAuditEvent.of(
                AuditEventType.ACCESS_DENIED,
                USER_ID, false, DEVICE_FINGERPRINT, Map.of("reason", "kyc_required"))))
                .doesNotThrowAnyException();
    }

    @Test
    void duplicateEventUuidIsSwallowedAsRegistryReplay() {
        when(auditEventCommandRepository.save(any(AuditEvent.class)))
                .thenThrow(new DuplicateKeyException("duplicate event_uuid"));

        assertThatCode(() -> listener.onTransactionalEvent(TransactionalAuditEvent.of(
                AuditEventType.LOGIN_SUCCESS,
                USER_ID, false, DEVICE_FINGERPRINT, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void nonDuplicateIntegrityViolationIsSwallowedWithoutBeingTreatedAsDuplicate() {
        when(auditEventCommandRepository.save(any(AuditEvent.class)))
                .thenThrow(new DataIntegrityViolationException("value too long for column"));

        assertThatCode(() -> listener.onNonTransactionalEvent(NonTransactionalAuditEvent.of(
                AuditEventType.LOGIN_FAILURE,
                null, true, DEVICE_FINGERPRINT, null)))
                .doesNotThrowAnyException();
    }

    private AuditEvent capturedEntity() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventCommandRepository).save(captor.capture());
        return captor.getValue();
    }
}
