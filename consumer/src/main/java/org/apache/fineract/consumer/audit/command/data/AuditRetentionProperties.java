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

package org.apache.fineract.consumer.audit.command.data;

import java.util.Map;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(AuditRetentionProperties.PREFIX)
public class AuditRetentionProperties {

    static final String PREFIX = "consumer.audit";
    private static final String MISSING_RETENTION_ENTRY_MESSAGE =
            PREFIX + ".retention-days is missing an entry for source ";

    private final Map<AuditEventSource, Integer> retentionDays;
    private final String purgeCron;
    private final int purgeBatchSize;
    private final boolean purgeEnabled;

    public AuditRetentionProperties(Map<AuditEventSource, Integer> retentionDays, String purgeCron, int purgeBatchSize,
            boolean purgeEnabled) {
        for (AuditEventSource source : AuditEventSource.values()) {
            if (retentionDays == null || !retentionDays.containsKey(source)) {
                throw new IllegalArgumentException(MISSING_RETENTION_ENTRY_MESSAGE + source);
            }
        }
        this.retentionDays = Map.copyOf(retentionDays);
        this.purgeCron = purgeCron;
        this.purgeBatchSize = purgeBatchSize;
        this.purgeEnabled = purgeEnabled;
    }
}
