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

package org.apache.fineract.consumer.savings.command.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.web.data.ConsumerHeaders;
import org.apache.fineract.consumer.savings.command.data.ModifySavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.ModifySavingsApplicationCommandRequest;
import org.apache.fineract.consumer.savings.command.data.SavingsApplicationCommandData;
import org.apache.fineract.consumer.savings.command.data.SubmitSavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.SubmitSavingsApplicationCommandRequest;
import org.apache.fineract.consumer.savings.command.data.WithdrawSavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.WithdrawSavingsApplicationCommandRequest;
import org.apache.fineract.consumer.savings.command.exception.SavingsApplicationInvalidException;
import org.apache.fineract.consumer.savings.command.service.SavingsCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/savings", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SavingsCommandController {

    private final SavingsCommandService savingsCommandService;

    @Operation(operationId = "submitSavingsApplication")
    @PostMapping
    public ResponseEntity<SavingsApplicationCommandData> submit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody SubmitSavingsApplicationCommandRequest request) {
        IdempotencyKeyPolicyEvaluator.validate(idempotencyKey);
        SubmitSavingsApplicationCommand cmd = SubmitSavingsApplicationCommand.builder()
                .productId(request.getProductId())
                .submittedOnDate(request.getSubmittedOnDate())
                .idempotencyKey(idempotencyKey)
                .build();
        SavingsApplicationCommandData data = savingsCommandService.submitApplication(jwt, cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(data);
    }

    @Operation(operationId = "modifySavingsApplication")
    @PutMapping("/{savingsId}")
    public ResponseEntity<SavingsApplicationCommandData> modify(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @PathVariable Long savingsId,
            @Valid @RequestBody ModifySavingsApplicationCommandRequest request) {
        IdempotencyKeyPolicyEvaluator.validate(idempotencyKey);
        ModifySavingsApplicationCommand cmd = ModifySavingsApplicationCommand.builder()
                .savingsId(savingsId)
                .productId(request.getProductId())
                .submittedOnDate(request.getSubmittedOnDate())
                .idempotencyKey(idempotencyKey)
                .build();
        return ResponseEntity.ok(savingsCommandService.modifyApplication(jwt, cmd));
    }

    @Operation(operationId = "withdrawSavingsApplication")
    @PostMapping("/{savingsId}")
    public ResponseEntity<SavingsApplicationCommandData> withdraw(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @PathVariable Long savingsId,
            @RequestParam("command") String command,
            @Valid @RequestBody WithdrawSavingsApplicationCommandRequest request) {
        if (!SavingsCommandService.WITHDRAW_COMMAND.equals(command)) {
            throw new SavingsApplicationInvalidException();
        }
        IdempotencyKeyPolicyEvaluator.validate(idempotencyKey);
        WithdrawSavingsApplicationCommand cmd = WithdrawSavingsApplicationCommand.builder()
                .savingsId(savingsId)
                .withdrawnOnDate(request.getWithdrawnOnDate())
                .note(request.getNote())
                .idempotencyKey(idempotencyKey)
                .build();
        return ResponseEntity.ok(savingsCommandService.withdrawApplication(jwt, cmd));
    }
}
