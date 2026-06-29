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
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.apache.fineract.consumer.savings.command.data.ConfirmSavingsChargePaymentCommand;
import org.apache.fineract.consumer.savings.command.data.ConfirmSavingsChargePaymentCommandRequest;
import org.apache.fineract.consumer.savings.command.data.InitiateSavingsChargePaymentCommand;
import org.apache.fineract.consumer.savings.command.data.InitiateSavingsChargePaymentCommandRequest;
import org.apache.fineract.consumer.savings.command.data.SavingsChargePaymentChallengeCommandData;
import org.apache.fineract.consumer.savings.command.data.SavingsChargePaymentCommandData;
import org.apache.fineract.consumer.savings.command.service.SavingsCommandService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/savings", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SavingsCommandController {

    private final SavingsCommandService savingsCommandService;

    @Operation(operationId = "initiateSavingsChargePayment")
    @PostMapping("/{savingsId}/charges/{chargeId}/pay")
    public ResponseEntity<SavingsChargePaymentChallengeCommandData> initiateChargePayment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @PathVariable Long savingsId,
            @PathVariable Long chargeId,
            @Valid @RequestBody InitiateSavingsChargePaymentCommandRequest request) {
        InitiateSavingsChargePaymentCommand command = InitiateSavingsChargePaymentCommand.builder()
                .savingsId(savingsId)
                .chargeId(chargeId)
                .amount(request.getAmount())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(savingsCommandService.initiateChargePayment(jwt, command));
    }

    @Operation(operationId = "confirmSavingsChargePayment")
    @PostMapping("/{savingsId}/charges/{chargeId}/pay/confirm")
    public ResponseEntity<SavingsChargePaymentCommandData> confirmChargePayment(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @PathVariable Long savingsId,
            @PathVariable Long chargeId,
            @Valid @RequestBody ConfirmSavingsChargePaymentCommandRequest request) {
        ConfirmSavingsChargePaymentCommand command = ConfirmSavingsChargePaymentCommand.builder()
                .savingsId(savingsId)
                .chargeId(chargeId)
                .stepUpToken(request.getStepUpToken())
                .otp(request.getOtp())
                .amount(request.getAmount())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(savingsCommandService.confirmChargePayment(jwt, command));
    }
}
