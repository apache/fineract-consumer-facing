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
package org.apache.fineract.consumer.audit.command.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.audit.command.data.AuditEventsSubmittedCommandData;
import org.apache.fineract.consumer.audit.command.data.SubmitAuditEventsCommandRequest;
import org.apache.fineract.consumer.audit.command.service.AuditCommandService;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/audit", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuditCommandController {

    private final AuditCommandService auditCommandService;

    @Operation(operationId = "submitAuditEvents")
    @PostMapping("/events")
    public ResponseEntity<AuditEventsSubmittedCommandData> submitEvents(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @Valid @RequestBody SubmitAuditEventsCommandRequest request) {
        return ResponseEntity.accepted()
                .body(auditCommandService.submitEvents(jwt, deviceFingerprint, request));
    }
}
