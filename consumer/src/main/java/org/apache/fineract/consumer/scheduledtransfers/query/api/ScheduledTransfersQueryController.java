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
package org.apache.fineract.consumer.scheduledtransfers.query.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferQueryData;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunHistoryQuery;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunQueryResponse;
import org.apache.fineract.consumer.scheduledtransfers.query.service.ScheduledTransfersQueryService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/scheduled-transfers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ScheduledTransfersQueryController {

    private final ScheduledTransfersQueryService scheduledTransfersQueryService;

    @Operation(operationId = "listScheduledTransfers",
            description = "The caller's active scheduled transfers, newest first. Only transfers set up through this "
                    + "API are listed; standing instructions the bank set up are not.")
    @GetMapping
    public List<ScheduledTransferQueryData> listScheduledTransfers(@AuthenticationPrincipal Jwt jwt) {
        return scheduledTransfersQueryService.listScheduledTransfers(jwt);
    }

    @Operation(operationId = "listScheduledTransferRuns",
            description = "The runs of one of the caller's scheduled transfers, newest first. A failed run moved "
                    + "nothing and carries no amount.")
    @GetMapping("/{publicId}/runs")
    public ScheduledTransferRunQueryResponse listScheduledTransferRuns(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID publicId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        ScheduledTransferRunHistoryQuery query = ScheduledTransferRunHistoryQuery.builder()
                .publicId(publicId)
                .page(page)
                .size(size)
                .build();
        return scheduledTransfersQueryService.listRuns(jwt, query);
    }
}
