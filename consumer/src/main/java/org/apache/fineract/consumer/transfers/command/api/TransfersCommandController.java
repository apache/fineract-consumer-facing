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

package org.apache.fineract.consumer.transfers.command.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.apache.fineract.consumer.transfers.command.data.ConfirmTransferCommand;
import org.apache.fineract.consumer.transfers.command.data.ConfirmTransferCommandRequest;
import org.apache.fineract.consumer.transfers.command.data.InitiateTransferCommand;
import org.apache.fineract.consumer.transfers.command.data.InitiateTransferCommandRequest;
import org.apache.fineract.consumer.transfers.command.data.TransferChallengeCommandData;
import org.apache.fineract.consumer.transfers.command.data.TransferCommandData;
import org.apache.fineract.consumer.transfers.command.service.TransfersCommandService;
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
@RequestMapping(value = "/api/v1/transfers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TransfersCommandController {

    private final TransfersCommandService transfersCommandService;

    @Operation(operationId = "initiateTransfer")
    @PostMapping("/initiate")
    public ResponseEntity<TransferChallengeCommandData> initiate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @Valid @RequestBody InitiateTransferCommandRequest request) {
        InitiateTransferCommand command = InitiateTransferCommand.builder()
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .toAccountType(request.getToAccountType())
                .amount(request.getAmount())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(transfersCommandService.initiate(jwt, command));
    }

    @Operation(operationId = "confirmTransfer")
    @PostMapping("/confirm")
    public ResponseEntity<TransferCommandData> confirm(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @Valid @RequestBody ConfirmTransferCommandRequest request) {
        ConfirmTransferCommand command = ConfirmTransferCommand.builder()
                .stepUpToken(request.getStepUpToken())
                .otp(request.getOtp())
                .fromAccountId(request.getFromAccountId())
                .toAccountId(request.getToAccountId())
                .toAccountType(request.getToAccountType())
                .amount(request.getAmount())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(transfersCommandService.confirm(jwt, command));
    }
}
