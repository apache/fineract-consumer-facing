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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.apache.fineract.consumer.audit.command.data.AuditEventSource;
import org.apache.fineract.consumer.audit.command.data.AuditRetentionProperties;
import org.apache.fineract.consumer.audit.command.repository.AuditEventCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditRetentionJobTest {

    private static final int SERVER_RETENTION_DAYS = 365;
    private static final int CLIENT_RETENTION_DAYS = 30;
    private static final Map<AuditEventSource, Integer> RETENTION_DAYS = Map
            .of(AuditEventSource.SERVER, SERVER_RETENTION_DAYS, AuditEventSource.CLIENT, CLIENT_RETENTION_DAYS);
    private static final String PURGE_CRON = "0 30 3 * * *";
    private static final int BATCH_SIZE = 1000;

    @Mock
    private AuditEventCommandRepository repository;

    private AuditRetentionJob job(boolean purgeEnabled) {
        return new AuditRetentionJob(repository,
                new AuditRetentionProperties(RETENTION_DAYS, PURGE_CRON, BATCH_SIZE, purgeEnabled));
    }

    @Test
    void purgeDisabledNeverTouchesRepository() {
        job(false).purgeExpiredEvents();

        verifyNoInteractions(repository);
    }

    @Test
    void purgeRunsOncePerSource() {
        when(repository.deleteChunkBySourceReceivedBefore(anyString(), any(Instant.class), eq(BATCH_SIZE)))
                .thenReturn(0);

        job(true).purgeExpiredEvents();

        for (AuditEventSource source : AuditEventSource.values()) {
            verify(repository, times(1)).deleteChunkBySourceReceivedBefore(eq(source.name()), any(Instant.class),
                    eq(BATCH_SIZE));
        }
    }

    @Test
    void purgeLoopsUntilChunkDeletesFewerThanBatchSize() {
        when(repository.deleteChunkBySourceReceivedBefore(eq(AuditEventSource.CLIENT.name()), any(Instant.class),
                eq(BATCH_SIZE))).thenReturn(BATCH_SIZE, BATCH_SIZE, 3);
        when(repository.deleteChunkBySourceReceivedBefore(eq(AuditEventSource.SERVER.name()), any(Instant.class),
                eq(BATCH_SIZE))).thenReturn(0);

        job(true).purgeExpiredEvents();

        verify(repository, times(3)).deleteChunkBySourceReceivedBefore(eq(AuditEventSource.CLIENT.name()),
                any(Instant.class), eq(BATCH_SIZE));
        verify(repository, times(1)).deleteChunkBySourceReceivedBefore(eq(AuditEventSource.SERVER.name()),
                any(Instant.class), eq(BATCH_SIZE));
    }

    @Test
    void purgeComputesPerSourceCutoffs() {
        when(repository.deleteChunkBySourceReceivedBefore(anyString(), any(Instant.class), eq(BATCH_SIZE)))
                .thenReturn(0);
        Instant before = Instant.now();

        job(true).purgeExpiredEvents();

        Instant after = Instant.now();
        for (AuditEventSource source : AuditEventSource.values()) {
            Duration retention = Duration.ofDays(RETENTION_DAYS.get(source));
            ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(repository).deleteChunkBySourceReceivedBefore(eq(source.name()), cutoffCaptor.capture(),
                    eq(BATCH_SIZE));
            assertThat(cutoffCaptor.getValue()).isBetween(before.minus(retention), after.minus(retention));
        }
    }

    @Test
    void purgeFailureOnOneSourceDoesNotStopOthers() {
        when(repository.deleteChunkBySourceReceivedBefore(anyString(), any(Instant.class), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> job(true).purgeExpiredEvents()).doesNotThrowAnyException();

        for (AuditEventSource source : AuditEventSource.values()) {
            verify(repository).deleteChunkBySourceReceivedBefore(eq(source.name()), any(Instant.class), eq(BATCH_SIZE));
        }
    }

    @Test
    void propertiesRejectMissingSourceEntry() {
        Map<AuditEventSource, Integer> incomplete = Map.of(AuditEventSource.SERVER, SERVER_RETENTION_DAYS);

        assertThatThrownBy(() -> new AuditRetentionProperties(incomplete, PURGE_CRON, BATCH_SIZE, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(AuditEventSource.CLIENT.name());
    }
}
