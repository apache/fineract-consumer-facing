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

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.consumer.audit.command.data.AuditEventSource;
import org.apache.fineract.consumer.audit.command.data.AuditRetentionProperties;
import org.apache.fineract.consumer.audit.command.repository.AuditEventCommandRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(AuditRetentionProperties.class)
@RequiredArgsConstructor
@Slf4j
public class AuditRetentionJob {

    private static final String PURGE_CRON_PROPERTY = "${consumer.audit.purge-cron}";

    private final AuditEventCommandRepository repository;
    private final AuditRetentionProperties properties;

    @Scheduled(cron = PURGE_CRON_PROPERTY)
    public void purgeExpiredEvents() {
        if (!properties.isPurgeEnabled()) {
            return;
        }
        for (AuditEventSource source : AuditEventSource.values()) {
            try {
                Instant cutoff = Instant.now().minus(Duration.ofDays(properties.getRetentionDays().get(source)));
                int batchSize = properties.getPurgeBatchSize();
                long totalDeleted = 0;
                int deleted;
                do {
                    deleted = repository.deleteChunkBySourceReceivedBefore(source.name(), cutoff, batchSize);
                    totalDeleted += deleted;
                } while (deleted >= batchSize);
                log.info("audit.retention.purge.completed: source={} deleted={} cutoff={}", source, totalDeleted, cutoff);
            } catch (RuntimeException e) {
                log.error("audit.retention.purge.failed: source={}", source, e);
            }
        }
    }
}
