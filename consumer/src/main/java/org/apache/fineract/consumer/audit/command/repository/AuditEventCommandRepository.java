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

package org.apache.fineract.consumer.audit.command.repository;

import java.time.Instant;
import org.apache.fineract.consumer.audit.command.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AuditEventCommandRepository extends JpaRepository<AuditEvent, Long> {

    @Transactional
    @Modifying
    @Query(nativeQuery = true, value = """
            DELETE FROM audit_events
            WHERE id IN (
                SELECT id
                FROM audit_events
                WHERE source = :source
                  AND received_at < :cutoff
                LIMIT :batchSize
            )
            """)
    int deleteChunkBySourceReceivedBefore(@Param("source") String source, @Param("cutoff") Instant cutoff,
            @Param("batchSize") int batchSize);
}
