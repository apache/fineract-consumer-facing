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

package org.apache.fineract.consumer.openbanking.command.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.openbanking.command.data.CreateOpenBankingConsentCommand;
import org.apache.fineract.consumer.openbanking.command.data.CreateOpenBankingConsentCommandRequest;
import org.apache.fineract.consumer.openbanking.command.data.OpenBankingConsentCommandData;
import org.apache.fineract.consumer.openbanking.command.service.OpenBankingConsentCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/openbanking/account-access-consents",
        produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class OpenBankingConsentCommandController {

    private final OpenBankingConsentCommandService openBankingConsentCommandService;

    @Operation(operationId = "createAccountAccessConsent")
    @PostMapping
    public ResponseEntity<OpenBankingConsentCommandData> createConsent(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOpenBankingConsentCommandRequest request) {
        OpenBankingConsentCommandData created = openBankingConsentCommandService.create(jwt,
                CreateOpenBankingConsentCommand.builder()
                        .permissions(request.getPermissions())
                        .build());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
