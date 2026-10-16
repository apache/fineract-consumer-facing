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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.idempotency.exception.IdempotencyKeyMalformedException;
import org.apache.fineract.consumer.savings.command.data.ModifySavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.ModifySavingsApplicationCommandRequest;
import org.apache.fineract.consumer.savings.command.data.SubmitSavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.SubmitSavingsApplicationCommandRequest;
import org.apache.fineract.consumer.savings.command.data.WithdrawSavingsApplicationCommand;
import org.apache.fineract.consumer.savings.command.data.WithdrawSavingsApplicationCommandRequest;
import org.apache.fineract.consumer.savings.command.exception.SavingsApplicationInvalidException;
import org.apache.fineract.consumer.savings.command.service.SavingsCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class SavingsCommandControllerTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long SAVINGS_ID = 5L;
    private static final String IDEMPOTENCY_KEY = "savings-op-key-1";
    private static final String MALFORMED_IDEMPOTENCY_KEY = "key with spaces";

    @Mock
    private SavingsCommandService savingsCommandService;

    @InjectMocks
    private SavingsCommandController controller;

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .claim("scope", "read")
                .build();
    }

    private static SubmitSavingsApplicationCommandRequest submitRequest() {
        return SubmitSavingsApplicationCommandRequest.builder()
                .productId(1L)
                .submittedOnDate(LocalDate.of(2026, 7, 1))
                .build();
    }

    @Test
    void submitPassesIdempotencyKeyIntoCommand() {
        Jwt jwt = jwt();

        controller.submit(jwt, IDEMPOTENCY_KEY, submitRequest());

        ArgumentCaptor<SubmitSavingsApplicationCommand> captor = ArgumentCaptor.forClass(SubmitSavingsApplicationCommand.class);
        verify(savingsCommandService).submitApplication(eq(jwt), captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(captor.getValue().getProductId()).isEqualTo(1L);
        assertThat(captor.getValue().getSubmittedOnDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    }

    @Test
    void submitRejectsMalformedIdempotencyKey() {
        assertThatThrownBy(() -> controller.submit(jwt(), MALFORMED_IDEMPOTENCY_KEY, submitRequest()))
                .isInstanceOf(IdempotencyKeyMalformedException.class)
                .hasFieldOrPropertyWithValue("code", IdempotencyKeyMalformedException.CODE);

        verifyNoInteractions(savingsCommandService);
    }

    @Test
    void modifyPassesIdempotencyKeyIntoCommand() {
        Jwt jwt = jwt();
        ModifySavingsApplicationCommandRequest request = ModifySavingsApplicationCommandRequest.builder()
                .productId(2L)
                .submittedOnDate(LocalDate.of(2026, 7, 2))
                .build();

        controller.modify(jwt, IDEMPOTENCY_KEY, SAVINGS_ID, request);

        ArgumentCaptor<ModifySavingsApplicationCommand> captor = ArgumentCaptor.forClass(ModifySavingsApplicationCommand.class);
        verify(savingsCommandService).modifyApplication(eq(jwt), captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(captor.getValue().getSavingsId()).isEqualTo(SAVINGS_ID);
        assertThat(captor.getValue().getProductId()).isEqualTo(2L);
        assertThat(captor.getValue().getSubmittedOnDate()).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    @Test
    void modifyRejectsMalformedIdempotencyKey() {
        ModifySavingsApplicationCommandRequest request = ModifySavingsApplicationCommandRequest.builder()
                .productId(1L)
                .submittedOnDate(LocalDate.of(2026, 7, 1))
                .build();

        assertThatThrownBy(() -> controller.modify(jwt(), MALFORMED_IDEMPOTENCY_KEY, SAVINGS_ID, request))
                .isInstanceOf(IdempotencyKeyMalformedException.class)
                .hasFieldOrPropertyWithValue("code", IdempotencyKeyMalformedException.CODE);

        verifyNoInteractions(savingsCommandService);
    }

    @Test
    void withdrawPassesIdempotencyKeyIntoCommand() {
        Jwt jwt = jwt();
        WithdrawSavingsApplicationCommandRequest request = WithdrawSavingsApplicationCommandRequest.builder()
                .withdrawnOnDate(LocalDate.of(2026, 7, 3))
                .note("No longer interested")
                .build();

        controller.withdraw(jwt, IDEMPOTENCY_KEY, SAVINGS_ID, SavingsCommandService.WITHDRAW_COMMAND, request);

        ArgumentCaptor<WithdrawSavingsApplicationCommand> captor = ArgumentCaptor.forClass(WithdrawSavingsApplicationCommand.class);
        verify(savingsCommandService).withdrawApplication(eq(jwt), captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(captor.getValue().getSavingsId()).isEqualTo(SAVINGS_ID);
        assertThat(captor.getValue().getWithdrawnOnDate()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(captor.getValue().getNote()).isEqualTo("No longer interested");
    }

    @Test
    void withdrawRejectsMalformedIdempotencyKey() {
        WithdrawSavingsApplicationCommandRequest request = WithdrawSavingsApplicationCommandRequest.builder()
                .withdrawnOnDate(LocalDate.of(2026, 7, 1))
                .build();

        assertThatThrownBy(() -> controller.withdraw(jwt(), MALFORMED_IDEMPOTENCY_KEY, SAVINGS_ID,
                SavingsCommandService.WITHDRAW_COMMAND, request))
                .isInstanceOf(IdempotencyKeyMalformedException.class)
                .hasFieldOrPropertyWithValue("code", IdempotencyKeyMalformedException.CODE);

        verifyNoInteractions(savingsCommandService);
    }

    @Test
    void withdrawRejectsInvalidCommand() {
        WithdrawSavingsApplicationCommandRequest request = WithdrawSavingsApplicationCommandRequest.builder()
                .withdrawnOnDate(LocalDate.of(2026, 7, 1))
                .build();

        assertThatThrownBy(() -> controller.withdraw(jwt(), IDEMPOTENCY_KEY, SAVINGS_ID,
                "invalidCommand", request))
                .isInstanceOf(SavingsApplicationInvalidException.class)
                .hasFieldOrPropertyWithValue("code", SavingsApplicationInvalidException.CODE);

        verifyNoInteractions(savingsCommandService);
    }
}
