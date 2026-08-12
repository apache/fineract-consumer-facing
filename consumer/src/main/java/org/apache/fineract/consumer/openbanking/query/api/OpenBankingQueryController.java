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

package org.apache.fineract.consumer.openbanking.query.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingAccountQueryData;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingBalanceQueryData;
import org.apache.fineract.consumer.openbanking.query.service.OpenBankingQueryService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/openbanking/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class OpenBankingQueryController {

    private final OpenBankingQueryService openBankingQueryService;

    @Operation(operationId = "listOpenBankingAccounts")
    @GetMapping
    public List<OpenBankingAccountQueryData> listAccounts(@AuthenticationPrincipal Jwt jwt) {
        return openBankingQueryService.listAccounts(jwt);
    }

    @Operation(operationId = "getOpenBankingAccountBalances")
    @GetMapping("/{accountId}/balances")
    public List<OpenBankingBalanceQueryData> getBalances(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String accountId) {
        return openBankingQueryService.getBalances(jwt, accountId);
    }
}
