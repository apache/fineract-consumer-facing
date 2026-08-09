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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingTppConsentQueryData;
import org.apache.fineract.consumer.openbanking.query.service.OpenBankingConsentQueryService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/openbanking/account-access-consents",
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class OpenBankingConsentQueryController {

    private final OpenBankingConsentQueryService openBankingConsentQueryService;

    @Operation(operationId = "getAccountAccessConsent")
    @GetMapping("/{consentId}")
    public OpenBankingTppConsentQueryData getConsent(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID consentId) {
        return openBankingConsentQueryService.getConsentForTppClient(jwt, consentId);
    }
}
