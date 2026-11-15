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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.scheduledtransfers.command.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.web.data.ConsumerHeaders;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ConfirmCancelScheduledTransferCommand;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ConfirmCancelScheduledTransferCommandRequest;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ConfirmScheduledTransferCommand;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ConfirmScheduledTransferCommandRequest;
import org.apache.fineract.consumer.scheduledtransfers.command.data.InitiateCancelScheduledTransferCommand;
import org.apache.fineract.consumer.scheduledtransfers.command.data.InitiateScheduledTransferCommand;
import org.apache.fineract.consumer.scheduledtransfers.command.data.InitiateScheduledTransferCommandRequest;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferChallengeCommandData;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferCommandData;
import org.apache.fineract.consumer.scheduledtransfers.command.service.ScheduledTransfersCommandService;
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
@RequestMapping(value = "/api/v1/scheduled-transfers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ScheduledTransfersCommandController {

    private final ScheduledTransfersCommandService scheduledTransfersCommandService;

    @Operation(operationId = "initiateScheduledTransfer")
    @PostMapping("/initiate")
    public ResponseEntity<ScheduledTransferChallengeCommandData> initiate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @Valid @RequestBody InitiateScheduledTransferCommandRequest request) {
        InitiateScheduledTransferCommand command = InitiateScheduledTransferCommand.builder()
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .amount(request.getAmount())
                .frequency(request.getFrequency())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(scheduledTransfersCommandService.initiate(jwt, command));
    }

    @Operation(operationId = "confirmScheduledTransfer")
    @PostMapping("/confirm")
    public ResponseEntity<ScheduledTransferCommandData> confirm(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @RequestHeader(ConsumerHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody ConfirmScheduledTransferCommandRequest request) {
        IdempotencyKeyPolicyEvaluator.validate(idempotencyKey);
        ConfirmScheduledTransferCommand command = ConfirmScheduledTransferCommand.builder()
                .stepUpToken(request.getStepUpToken())
                .otp(request.getOtp())
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .amount(request.getAmount())
                .frequency(request.getFrequency())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .deviceFingerprint(deviceFingerprint)
                .idempotencyKey(idempotencyKey)
                .build();
        return ResponseEntity.ok(scheduledTransfersCommandService.confirm(jwt, command));
    }

    @Operation(operationId = "initiateCancelScheduledTransfer")
    @PostMapping("/{publicId}/cancel/initiate")
    public ResponseEntity<ScheduledTransferChallengeCommandData> initiateCancel(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @PathVariable UUID publicId) {
        InitiateCancelScheduledTransferCommand command = InitiateCancelScheduledTransferCommand.builder()
                .publicId(publicId)
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(scheduledTransfersCommandService.initiateCancel(jwt, command));
    }

    @Operation(operationId = "confirmCancelScheduledTransfer")
    @PostMapping("/{publicId}/cancel/confirm")
    public ResponseEntity<Void> confirmCancel(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @RequestHeader(ConsumerHeaders.IDEMPOTENCY_KEY) String idempotencyKey,
            @PathVariable UUID publicId,
            @Valid @RequestBody ConfirmCancelScheduledTransferCommandRequest request) {
        IdempotencyKeyPolicyEvaluator.validate(idempotencyKey);
        ConfirmCancelScheduledTransferCommand command = ConfirmCancelScheduledTransferCommand.builder()
                .publicId(publicId)
                .stepUpToken(request.getStepUpToken())
                .otp(request.getOtp())
                .deviceFingerprint(deviceFingerprint)
                .idempotencyKey(idempotencyKey)
                .build();
        scheduledTransfersCommandService.confirmCancel(jwt, command);
        return ResponseEntity.noContent().build();
    }
}
