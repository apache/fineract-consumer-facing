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

package org.apache.fineract.consumer.scheduledtransfers.command.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.idempotency.exception.IdempotencyKeyMalformedException;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ConfirmScheduledTransferCommand;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ConfirmScheduledTransferCommandRequest;
import org.apache.fineract.consumer.scheduledtransfers.command.data.InitiateScheduledTransferCommand;
import org.apache.fineract.consumer.scheduledtransfers.command.data.InitiateScheduledTransferCommandRequest;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferChallengeCommandData;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferCommandData;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferFrequency;
import org.apache.fineract.consumer.scheduledtransfers.command.service.ScheduledTransfersCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ScheduledTransfersCommandControllerTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String DEVICE_FINGERPRINT = "device-abc";
    private static final String IDEMPOTENCY_KEY = "sched-key-1";
    private static final String STEP_UP_TOKEN = "step-up-token";
    private static final String OTP = "123456";
    private static final Long FROM_ACCOUNT_ID = 7L;
    private static final Long TO_ACCOUNT_ID = 8L;
    private static final BigDecimal AMOUNT = new BigDecimal("25.50");
    private static final LocalDate START = LocalDate.of(2027, 1, 31);
    private static final LocalDate END = LocalDate.of(2027, 5, 31);

    @Mock
    private ScheduledTransfersCommandService service;

    @InjectMocks
    private ScheduledTransfersCommandController controller;

    @Test
    void initiateThreadsTheScheduleAndDeviceIntoTheCommand() {
        when(service.initiate(any(), any())).thenReturn(ScheduledTransferChallengeCommandData.builder().build());

        controller.initiate(jwt(), DEVICE_FINGERPRINT, InitiateScheduledTransferCommandRequest.builder()
                .fromAccountId(FROM_ACCOUNT_ID)
                .toAccountId(TO_ACCOUNT_ID)
                .amount(AMOUNT)
                .frequency(ScheduledTransferFrequency.MONTHLY)
                .startDate(START)
                .endDate(END)
                .build());

        ArgumentCaptor<InitiateScheduledTransferCommand> command =
                ArgumentCaptor.forClass(InitiateScheduledTransferCommand.class);
        verify(service).initiate(any(), command.capture());
        InitiateScheduledTransferCommand sent = command.getValue();
        assertThat(sent.getFromAccountId()).isEqualTo(FROM_ACCOUNT_ID);
        assertThat(sent.getToAccountId()).isEqualTo(TO_ACCOUNT_ID);
        assertThat(sent.getAmount()).isEqualTo(AMOUNT);
        assertThat(sent.getFrequency()).isEqualTo(ScheduledTransferFrequency.MONTHLY);
        assertThat(sent.getStartDate()).isEqualTo(START);
        assertThat(sent.getEndDate()).isEqualTo(END);
        assertThat(sent.getDeviceFingerprint()).isEqualTo(DEVICE_FINGERPRINT);
    }

    @Test
    void confirmThreadsHeadersIntoTheCommand() {
        when(service.confirm(any(), any())).thenReturn(ScheduledTransferCommandData.builder().build());

        controller.confirm(jwt(), DEVICE_FINGERPRINT, IDEMPOTENCY_KEY, confirmRequest());

        ArgumentCaptor<ConfirmScheduledTransferCommand> command =
                ArgumentCaptor.forClass(ConfirmScheduledTransferCommand.class);
        verify(service).confirm(any(), command.capture());
        ConfirmScheduledTransferCommand sent = command.getValue();
        assertThat(sent.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(sent.getDeviceFingerprint()).isEqualTo(DEVICE_FINGERPRINT);
        assertThat(sent.getStepUpToken()).isEqualTo(STEP_UP_TOKEN);
        assertThat(sent.getOtp()).isEqualTo(OTP);
        assertThat(sent.getFrequency()).isEqualTo(ScheduledTransferFrequency.WEEKLY);
        assertThat(sent.getEndDate()).isNull();
    }

    @Test
    void confirmRejectsAMalformedIdempotencyKey() {
        assertThatThrownBy(() -> controller.confirm(jwt(), DEVICE_FINGERPRINT, "not visible ascii", confirmRequest()))
                .isInstanceOf(IdempotencyKeyMalformedException.class)
                .hasFieldOrPropertyWithValue("code", IdempotencyKeyMalformedException.CODE);
        verifyNoInteractions(service);
    }

    private static ConfirmScheduledTransferCommandRequest confirmRequest() {
        return ConfirmScheduledTransferCommandRequest.builder()
                .stepUpToken(STEP_UP_TOKEN)
                .otp(OTP)
                .fromAccountId(FROM_ACCOUNT_ID)
                .toAccountId(TO_ACCOUNT_ID)
                .amount(AMOUNT)
                .frequency(ScheduledTransferFrequency.WEEKLY)
                .startDate(START)
                .build();
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .claim("scope", "read")
                .build();
    }
}
