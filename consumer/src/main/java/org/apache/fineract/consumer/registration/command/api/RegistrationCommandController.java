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

package org.apache.fineract.consumer.registration.command.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.registration.command.data.SendOtpCommand;
import org.apache.fineract.consumer.registration.command.data.SendOtpCommandData;
import org.apache.fineract.consumer.registration.command.data.SendOtpCommandRequest;
import org.apache.fineract.consumer.registration.command.data.SubmitRegistrationCommand;
import org.apache.fineract.consumer.registration.command.data.SubmitRegistrationCommandData;
import org.apache.fineract.consumer.registration.command.data.SubmitRegistrationCommandRequest;
import org.apache.fineract.consumer.registration.command.data.VerifyOtpCommand;
import org.apache.fineract.consumer.registration.command.data.VerifyOtpCommandData;
import org.apache.fineract.consumer.registration.command.data.VerifyOtpCommandRequest;
import org.apache.fineract.consumer.registration.command.service.RegistrationCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/registration", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class RegistrationCommandController {

    private final RegistrationCommandService registrationCommandService;

    @Operation(operationId = "submitRegistration")
    @PostMapping("/submit")
    public ResponseEntity<SubmitRegistrationCommandData> submit(
            @Valid @RequestBody SubmitRegistrationCommandRequest request) {
        SubmitRegistrationCommand command = SubmitRegistrationCommand.builder()
                .fineractClientId(request.getFineractClientId())
                .email(request.getEmail())
                .password(request.getPassword())
                .documentTypeName(request.getDocumentTypeName())
                .documentKey(request.getDocumentKey())
                .build();
        SubmitRegistrationCommandData data = registrationCommandService.submit(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(data);
    }

    @Operation(operationId = "sendRegistrationOtp")
    @PostMapping("/otp/send")
    public ResponseEntity<SendOtpCommandData> sendOtp(@Valid @RequestBody SendOtpCommandRequest request) {
        SendOtpCommand command = SendOtpCommand.builder()
                .registrationId(request.getRegistrationId())
                .deliveryMethod(request.getDeliveryMethod())
                .build();
        SendOtpCommandData data = registrationCommandService.sendOtp(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(data);
    }

    @Operation(operationId = "verifyRegistrationOtp")
    @PostMapping("/otp/verify")
    public ResponseEntity<VerifyOtpCommandData> verifyOtp(@Valid @RequestBody VerifyOtpCommandRequest request) {
        VerifyOtpCommand command = VerifyOtpCommand.builder()
                .registrationId(request.getRegistrationId())
                .token(request.getToken())
                .build();
        VerifyOtpCommandData data = registrationCommandService.verifyOtp(command);
        return ResponseEntity.ok(data);
    }
}
