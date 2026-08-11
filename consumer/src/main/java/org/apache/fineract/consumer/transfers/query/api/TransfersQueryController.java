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

package org.apache.fineract.consumer.transfers.query.api;

import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.transfers.query.data.TransferHistoryQuery;
import org.apache.fineract.consumer.transfers.query.data.TransferQueryResponse;
import org.apache.fineract.consumer.transfers.query.service.TransfersQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/transfers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TransfersQueryController {

    private final TransfersQueryService transfersQueryService;

    @Operation(operationId = "listTransfers",
            description = "Savings-to-savings transfer history assembled by fanning out over the client's own "
                    + "savings accounts. Savings-to-loan transfers are excluded — loan repayments belong to the "
                    + "loan account view. Accepted limits: cost grows with the client's account count "
                    + "(N+1 Fineract calls) plus one extra call per transfer whose counterparty leg is outside "
                    + "the client's savings accounts, and paging is BFF-side over a materialised set — which is "
                    + "also why the returned page carries an exact total, so clients never have to guess "
                    + "whether a further page exists.")
    @GetMapping
    public TransferQueryResponse listTransfers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        TransferHistoryQuery query = TransferHistoryQuery.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .page(page)
                .size(size)
                .build();
        return transfersQueryService.listTransfers(jwt, query);
    }
}
