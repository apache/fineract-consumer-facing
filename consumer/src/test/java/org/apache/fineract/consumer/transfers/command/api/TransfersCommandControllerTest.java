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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.idempotency.exception.IdempotencyKeyMalformedException;
import org.apache.fineract.consumer.transfers.command.data.ConfirmTransferCommand;
import org.apache.fineract.consumer.transfers.command.data.ConfirmTransferCommandRequest;
import org.apache.fineract.consumer.transfers.command.data.TransferCommandData;
import org.apache.fineract.consumer.transfers.command.data.TransferConstants;
import org.apache.fineract.consumer.transfers.command.service.TransfersCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class TransfersCommandControllerTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String DEVICE_FINGERPRINT = "device-abc";
    private static final String IDEMPOTENCY_KEY = "txn-key-1";
    private static final String STEP_UP_TOKEN = "step-up-token";
    private static final String OTP = "123456";
    private static final Long FROM_ACCOUNT_ID = 7L;
    private static final Long TO_ACCOUNT_ID = 8L;
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock
    private TransfersCommandService transfersCommandService;

    @InjectMocks
    private TransfersCommandController controller;

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .claim("scope", "read")
                .build();
    }

    private static ConfirmTransferCommandRequest confirmRequest() {
        return ConfirmTransferCommandRequest.builder()
                .stepUpToken(STEP_UP_TOKEN)
                .otp(OTP)
                .fromAccountId(FROM_ACCOUNT_ID)
                .toAccountId(TO_ACCOUNT_ID)
                .toAccountType(TransferConstants.SAVINGS_TYPE_NAME)
                .amount(AMOUNT)
                .build();
    }

    @Test
    void confirmThreadsHeadersIntoCommand() {
        when(transfersCommandService.confirm(any(), any())).thenReturn(TransferCommandData.builder().build());

        controller.confirm(jwt(), DEVICE_FINGERPRINT, IDEMPOTENCY_KEY, confirmRequest());

        ArgumentCaptor<ConfirmTransferCommand> command = ArgumentCaptor.forClass(ConfirmTransferCommand.class);
        verify(transfersCommandService).confirm(any(), command.capture());
        ConfirmTransferCommand sent = command.getValue();
        assertThat(sent.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(sent.getDeviceFingerprint()).isEqualTo(DEVICE_FINGERPRINT);
        assertThat(sent.getStepUpToken()).isEqualTo(STEP_UP_TOKEN);
        assertThat(sent.getOtp()).isEqualTo(OTP);
        assertThat(sent.getFromAccountId()).isEqualTo(FROM_ACCOUNT_ID);
        assertThat(sent.getToAccountId()).isEqualTo(TO_ACCOUNT_ID);
        assertThat(sent.getToAccountType()).isEqualTo(TransferConstants.SAVINGS_TYPE_NAME);
        assertThat(sent.getAmount()).isEqualTo(AMOUNT);
    }

    @Test
    void confirmRejectsMalformedIdempotencyKey() {
        assertThatThrownBy(() -> controller.confirm(jwt(), DEVICE_FINGERPRINT, "not visible ascii", confirmRequest()))
                .isInstanceOf(IdempotencyKeyMalformedException.class)
                .hasFieldOrPropertyWithValue("code", IdempotencyKeyMalformedException.CODE);

        verifyNoInteractions(transfersCommandService);
    }
}
