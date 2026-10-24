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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
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

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

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
    }

    @Test
    void submitRejectsMalformedIdempotencyKey() {
        assertThatThrownBy(() -> controller.submit(jwt(), MALFORMED_IDEMPOTENCY_KEY, submitRequest()))
                .isInstanceOf(IdempotencyKeyMalformedException.class)
                .hasFieldOrPropertyWithValue("code", IdempotencyKeyMalformedException.CODE);

        verifyNoInteractions(savingsCommandService);
    }

    @Test
    void submitRequestValidatesConstraints() {
        SubmitSavingsApplicationCommandRequest valid = SubmitSavingsApplicationCommandRequest.builder()
                .productId(1L)
                .build();
        assertThat(validator.validate(valid)).isEmpty();

        SubmitSavingsApplicationCommandRequest missingProductId = SubmitSavingsApplicationCommandRequest.builder()
                .build();
        Set<ConstraintViolation<SubmitSavingsApplicationCommandRequest>> v1 = validator.validate(missingProductId);
        assertThat(v1).extracting(ConstraintViolation::getPropertyPath).map(Object::toString).contains("productId");

        SubmitSavingsApplicationCommandRequest nonPositiveProductId = SubmitSavingsApplicationCommandRequest.builder()
                .productId(0L)
                .build();
        Set<ConstraintViolation<SubmitSavingsApplicationCommandRequest>> v2 = validator.validate(nonPositiveProductId);
        assertThat(v2).extracting(ConstraintViolation::getPropertyPath).map(Object::toString).contains("productId");
    }

    @Test
    void modifyPassesIdempotencyKeyIntoCommand() {
        Jwt jwt = jwt();
        ModifySavingsApplicationCommandRequest request = ModifySavingsApplicationCommandRequest.builder()
                .productId(2L)
                .build();

        controller.modify(jwt, IDEMPOTENCY_KEY, SAVINGS_ID, request);

        ArgumentCaptor<ModifySavingsApplicationCommand> captor = ArgumentCaptor.forClass(ModifySavingsApplicationCommand.class);
        verify(savingsCommandService).modifyApplication(eq(jwt), captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(captor.getValue().getSavingsId()).isEqualTo(SAVINGS_ID);
        assertThat(captor.getValue().getProductId()).isEqualTo(2L);
    }

    @Test
    void modifyRejectsMalformedIdempotencyKey() {
        ModifySavingsApplicationCommandRequest request = ModifySavingsApplicationCommandRequest.builder()
                .productId(1L)
                .build();

        assertThatThrownBy(() -> controller.modify(jwt(), MALFORMED_IDEMPOTENCY_KEY, SAVINGS_ID, request))
                .isInstanceOf(IdempotencyKeyMalformedException.class)
                .hasFieldOrPropertyWithValue("code", IdempotencyKeyMalformedException.CODE);

        verifyNoInteractions(savingsCommandService);
    }

    @Test
    void modifyRequestValidatesConstraints() {
        ModifySavingsApplicationCommandRequest valid = ModifySavingsApplicationCommandRequest.builder()
                .productId(2L)
                .build();
        assertThat(validator.validate(valid)).isEmpty();

        ModifySavingsApplicationCommandRequest nonPositiveProductId = ModifySavingsApplicationCommandRequest.builder()
                .productId(-1L)
                .build();
        Set<ConstraintViolation<ModifySavingsApplicationCommandRequest>> v1 = validator.validate(nonPositiveProductId);
        assertThat(v1).extracting(ConstraintViolation::getPropertyPath).map(Object::toString).contains("productId");

        ModifySavingsApplicationCommandRequest missingProductId = ModifySavingsApplicationCommandRequest.builder()
                .build();
        Set<ConstraintViolation<ModifySavingsApplicationCommandRequest>> v2 = validator.validate(missingProductId);
        assertThat(v2).extracting(ConstraintViolation::getPropertyPath).map(Object::toString).contains("productId");
    }

    @Test
    void withdrawPassesIdempotencyKeyIntoCommand() {
        Jwt jwt = jwt();
        WithdrawSavingsApplicationCommandRequest request = WithdrawSavingsApplicationCommandRequest.builder()
                .note("No longer interested")
                .build();

        controller.withdraw(jwt, IDEMPOTENCY_KEY, SAVINGS_ID, SavingsCommandService.WITHDRAW_COMMAND, request);

        ArgumentCaptor<WithdrawSavingsApplicationCommand> captor = ArgumentCaptor.forClass(WithdrawSavingsApplicationCommand.class);
        verify(savingsCommandService).withdrawApplication(eq(jwt), captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(captor.getValue().getSavingsId()).isEqualTo(SAVINGS_ID);
        assertThat(captor.getValue().getNote()).isEqualTo("No longer interested");
    }

    @Test
    void withdrawRejectsMalformedIdempotencyKey() {
        WithdrawSavingsApplicationCommandRequest request = WithdrawSavingsApplicationCommandRequest.builder()
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
                .build();

        assertThatThrownBy(() -> controller.withdraw(jwt(), IDEMPOTENCY_KEY, SAVINGS_ID,
                "invalidCommand", request))
                .isInstanceOf(SavingsApplicationInvalidException.class)
                .hasFieldOrPropertyWithValue("code", SavingsApplicationInvalidException.CODE);

        verifyNoInteractions(savingsCommandService);
    }

    @Test
    void withdrawRequestValidatesConstraints() {
        WithdrawSavingsApplicationCommandRequest valid = WithdrawSavingsApplicationCommandRequest.builder()
                .note("Valid note")
                .build();
        assertThat(validator.validate(valid)).isEmpty();

        String longNote = "a".repeat(1001);
        WithdrawSavingsApplicationCommandRequest oversizedNote = WithdrawSavingsApplicationCommandRequest.builder()
                .note(longNote)
                .build();
        Set<ConstraintViolation<WithdrawSavingsApplicationCommandRequest>> v2 = validator.validate(oversizedNote);
        assertThat(v2).extracting(ConstraintViolation::getPropertyPath).map(Object::toString).contains("note");
    }
}
