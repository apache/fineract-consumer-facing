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

package org.apache.fineract.consumer.savings.query.api;

import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountListItemQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsApplicationTemplateQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsChargeQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionSearchQuery;
import org.apache.fineract.consumer.savings.query.service.SavingsQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/savings", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SavingsQueryController {

    private final SavingsQueryService savingsQueryService;

    @Operation(operationId = "listSavingsAccounts")
    @GetMapping
    public List<SavingsAccountListItemQueryData> listAccounts(@AuthenticationPrincipal Jwt jwt) {
        return savingsQueryService.listAccounts(jwt);
    }

    @Operation(operationId = "getSavingsAccount")
    @GetMapping("/{savingsId}")
    public SavingsAccountQueryData getAccount(@AuthenticationPrincipal Jwt jwt, @PathVariable Long savingsId) {
        return savingsQueryService.getAccount(jwt, savingsId);
    }

    @Operation(operationId = "getSavingsCharges")
    @GetMapping("/{savingsId}/charges")
    public List<SavingsChargeQueryData> getCharges(@AuthenticationPrincipal Jwt jwt, @PathVariable Long savingsId) {
        return savingsQueryService.getCharges(jwt, savingsId);
    }

    @Operation(operationId = "searchSavingsTransactions")
    @GetMapping("/{savingsId}/transactions")
    public List<SavingsTransactionQueryData> searchTransactions(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long savingsId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        SavingsTransactionSearchQuery query = SavingsTransactionSearchQuery.builder()
                .savingsId(savingsId)
                .fromDate(fromDate)
                .toDate(toDate)
                .page(page)
                .size(size)
                .build();
        return savingsQueryService.searchTransactions(jwt, query);
    }

    @Operation(operationId = "getSavingsTransaction")
    @GetMapping("/{savingsId}/transactions/{transactionId}")
    public SavingsTransactionQueryData getTransaction(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long savingsId,
            @PathVariable Long transactionId) {
        return savingsQueryService.getTransaction(jwt, savingsId, transactionId);
    }

    @Operation(operationId = "getSavingsApplicationTemplate")
    @GetMapping("/template")
    public SavingsApplicationTemplateQueryData getApplicationTemplate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long productId) {
        return savingsQueryService.getApplicationTemplate(jwt, productId);
    }
}
