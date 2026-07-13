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
package org.apache.fineract.consumer.beneficiaries.command.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.beneficiaries.command.data.BeneficiaryChallengeCommandData;
import org.apache.fineract.consumer.beneficiaries.command.data.BeneficiaryCommandData;
import org.apache.fineract.consumer.beneficiaries.command.data.ConfirmAddBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.ConfirmAddBeneficiaryCommandRequest;
import org.apache.fineract.consumer.beneficiaries.command.data.ConfirmUpdateBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.ConfirmUpdateBeneficiaryCommandRequest;
import org.apache.fineract.consumer.beneficiaries.command.data.InitiateAddBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.InitiateAddBeneficiaryCommandRequest;
import org.apache.fineract.consumer.beneficiaries.command.data.InitiateUpdateBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.InitiateUpdateBeneficiaryCommandRequest;
import org.apache.fineract.consumer.beneficiaries.command.service.BeneficiariesCommandService;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/beneficiaries", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BeneficiariesCommandController {

    private final BeneficiariesCommandService beneficiariesCommandService;

    @Operation(operationId = "initiateAddBeneficiary")
    @PostMapping("/initiate")
    public ResponseEntity<BeneficiaryChallengeCommandData> initiateAdd(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @Valid @RequestBody InitiateAddBeneficiaryCommandRequest request) {
        InitiateAddBeneficiaryCommand command = InitiateAddBeneficiaryCommand.builder()
                .name(request.getName())
                .officeName(request.getOfficeName())
                .accountNumber(request.getAccountNumber())
                .accountType(request.getAccountType())
                .transferLimit(request.getTransferLimit())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(beneficiariesCommandService.initiateAdd(jwt, command));
    }

    @Operation(operationId = "confirmAddBeneficiary")
    @PostMapping("/confirm")
    public ResponseEntity<BeneficiaryCommandData> confirmAdd(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @Valid @RequestBody ConfirmAddBeneficiaryCommandRequest request) {
        ConfirmAddBeneficiaryCommand command = ConfirmAddBeneficiaryCommand.builder()
                .stepUpToken(request.getStepUpToken())
                .otp(request.getOtp())
                .name(request.getName())
                .officeName(request.getOfficeName())
                .accountNumber(request.getAccountNumber())
                .accountType(request.getAccountType())
                .transferLimit(request.getTransferLimit())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(beneficiariesCommandService.confirmAdd(jwt, command));
    }

    @Operation(operationId = "initiateUpdateBeneficiary")
    @PutMapping("/{publicId}/initiate")
    public ResponseEntity<BeneficiaryChallengeCommandData> initiateUpdate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @PathVariable UUID publicId,
            @Valid @RequestBody InitiateUpdateBeneficiaryCommandRequest request) {
        InitiateUpdateBeneficiaryCommand command = InitiateUpdateBeneficiaryCommand.builder()
                .publicId(publicId)
                .name(request.getName())
                .transferLimit(request.getTransferLimit())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(beneficiariesCommandService.initiateUpdate(jwt, command));
    }

    @Operation(operationId = "confirmUpdateBeneficiary")
    @PutMapping("/{publicId}/confirm")
    public ResponseEntity<BeneficiaryCommandData> confirmUpdate(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(ConsumerHeaders.DEVICE_FINGERPRINT) String deviceFingerprint,
            @PathVariable UUID publicId,
            @Valid @RequestBody ConfirmUpdateBeneficiaryCommandRequest request) {
        ConfirmUpdateBeneficiaryCommand command = ConfirmUpdateBeneficiaryCommand.builder()
                .stepUpToken(request.getStepUpToken())
                .otp(request.getOtp())
                .publicId(publicId)
                .name(request.getName())
                .transferLimit(request.getTransferLimit())
                .deviceFingerprint(deviceFingerprint)
                .build();
        return ResponseEntity.ok(beneficiariesCommandService.confirmUpdate(jwt, command));
    }

    @Operation(operationId = "deleteBeneficiary")
    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID publicId) {
        beneficiariesCommandService.delete(jwt, publicId);
        return ResponseEntity.noContent().build();
    }
}
