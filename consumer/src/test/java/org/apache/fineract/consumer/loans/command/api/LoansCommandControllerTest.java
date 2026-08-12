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

package org.apache.fineract.consumer.loans.command.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.idempotency.exception.IdempotencyKeyMalformedException;
import org.apache.fineract.consumer.loans.command.data.ModifyLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.ModifyLoanApplicationCommandRequest;
import org.apache.fineract.consumer.loans.command.data.SubmitLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.SubmitLoanApplicationCommandRequest;
import org.apache.fineract.consumer.loans.command.data.WithdrawLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.WithdrawLoanApplicationCommandRequest;
import org.apache.fineract.consumer.loans.command.service.LoansCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class LoansCommandControllerTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long LOAN_ID = 7L;
    private static final String IDEMPOTENCY_KEY = "loan-op-key-1";
    private static final String MALFORMED_IDEMPOTENCY_KEY = "key with spaces";

    @Mock
    private LoansCommandService loansCommandService;

    @InjectMocks
    private LoansCommandController controller;

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .claim("scope", "read")
                .build();
    }

    private static SubmitLoanApplicationCommandRequest submitRequest() {
        return SubmitLoanApplicationCommandRequest.builder()
                .productId(1L)
                .expectedDisbursementDate(LocalDate.of(2026, 7, 1))
                .submittedOnDate(LocalDate.of(2026, 7, 1))
                .build();
    }

    @Test
    void submitPassesIdempotencyKeyIntoCommand() {
        Jwt jwt = jwt();

        controller.submit(jwt, IDEMPOTENCY_KEY, submitRequest());

        ArgumentCaptor<SubmitLoanApplicationCommand> captor = ArgumentCaptor.forClass(SubmitLoanApplicationCommand.class);
        verify(loansCommandService).submitApplication(eq(jwt), captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    void submitRejectsMalformedIdempotencyKey() {
        assertThatThrownBy(() -> controller.submit(jwt(), MALFORMED_IDEMPOTENCY_KEY, submitRequest()))
                .isInstanceOf(IdempotencyKeyMalformedException.class)
                .hasFieldOrPropertyWithValue("code", IdempotencyKeyMalformedException.CODE);

        verifyNoInteractions(loansCommandService);
    }

    @Test
    void modifyPassesIdempotencyKeyIntoCommand() {
        Jwt jwt = jwt();
        ModifyLoanApplicationCommandRequest request = ModifyLoanApplicationCommandRequest.builder()
                .productId(1L)
                .expectedDisbursementDate(LocalDate.of(2026, 7, 1))
                .submittedOnDate(LocalDate.of(2026, 7, 1))
                .build();

        controller.modify(jwt, IDEMPOTENCY_KEY, LOAN_ID, request);

        ArgumentCaptor<ModifyLoanApplicationCommand> captor = ArgumentCaptor.forClass(ModifyLoanApplicationCommand.class);
        verify(loansCommandService).modifyApplication(eq(jwt), captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    void modifyRejectsMalformedIdempotencyKey() {
        ModifyLoanApplicationCommandRequest request = ModifyLoanApplicationCommandRequest.builder()
                .productId(1L)
                .build();

        assertThatThrownBy(() -> controller.modify(jwt(), MALFORMED_IDEMPOTENCY_KEY, LOAN_ID, request))
                .isInstanceOf(IdempotencyKeyMalformedException.class)
                .hasFieldOrPropertyWithValue("code", IdempotencyKeyMalformedException.CODE);

        verifyNoInteractions(loansCommandService);
    }

    @Test
    void withdrawPassesIdempotencyKeyIntoCommand() {
        Jwt jwt = jwt();
        WithdrawLoanApplicationCommandRequest request = WithdrawLoanApplicationCommandRequest.builder()
                .withdrawnOnDate(LocalDate.of(2026, 7, 1))
                .build();

        controller.withdraw(jwt, IDEMPOTENCY_KEY, LOAN_ID, LoansCommandService.WITHDRAW_COMMAND, request);

        ArgumentCaptor<WithdrawLoanApplicationCommand> captor = ArgumentCaptor.forClass(WithdrawLoanApplicationCommand.class);
        verify(loansCommandService).withdrawApplication(eq(jwt), captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
    }

    @Test
    void withdrawRejectsMalformedIdempotencyKey() {
        WithdrawLoanApplicationCommandRequest request = WithdrawLoanApplicationCommandRequest.builder()
                .withdrawnOnDate(LocalDate.of(2026, 7, 1))
                .build();

        assertThatThrownBy(() -> controller.withdraw(jwt(), MALFORMED_IDEMPOTENCY_KEY, LOAN_ID,
                LoansCommandService.WITHDRAW_COMMAND, request))
                .isInstanceOf(IdempotencyKeyMalformedException.class)
                .hasFieldOrPropertyWithValue("code", IdempotencyKeyMalformedException.CODE);

        verifyNoInteractions(loansCommandService);
    }
}
