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

package org.apache.fineract.consumer.audit.query.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.audit.query.data.AuditEventListQuery;
import org.apache.fineract.consumer.audit.query.data.AuditEventQueryData;
import org.apache.fineract.consumer.audit.query.service.AuditQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@ConditionalOnProperty(name = "consumer.audit.query-enabled", havingValue = "true")
@RestController
@RequestMapping(value = "/api/v1/audit", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuditQueryController {

    private final AuditQueryService auditQueryService;

    @Operation(operationId = "listAuditEvents",
            description = "Demo-only: lists the caller's own audit activity so the demo frontend can "
                    + "make the trail visible. Audit trails are not a consumer product surface.")
    @GetMapping("/events")
    public List<AuditEventQueryData> listEvents(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        AuditEventListQuery query = AuditEventListQuery.builder()
                .page(page)
                .size(size)
                .build();
        return auditQueryService.listEvents(jwt, query);
    }
}
