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

package org.apache.fineract.consumer.transfers.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.AccountTransfersApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.AccountTransferRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostAccountTransfersResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.jwt.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpTokenService;
import org.apache.fineract.consumer.infrastructure.web.AccessPolicyEvaluator;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.service.OtpCommandService;
import org.apache.fineract.consumer.transfers.command.data.ConfirmTransferCommand;
import org.apache.fineract.consumer.transfers.command.data.InitiateTransferCommand;
import org.apache.fineract.consumer.transfers.command.data.TransferChallengeCommandData;
import org.apache.fineract.consumer.transfers.command.data.TransferCommandData;
import org.apache.fineract.consumer.transfers.command.data.TransferConstants;
import org.apache.fineract.consumer.transfers.command.exception.TransferInvalidException;
import org.apache.fineract.consumer.transfers.command.exception.TransferAccessDeniedException;
import org.apache.fineract.consumer.transfers.command.exception.TransferStepUpInvalidException;
import org.apache.fineract.consumer.user.command.domain.UserStatus;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class TransfersCommandServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long CLIENT_ID = 42L;
    private static final Long FROM_SAVINGS_ID = 7L;
    private static final Long TO_SAVINGS_ID = 8L;
    private static final Long CALLER_OFFICE_ID = 1L;
    private static final Long DEST_CLIENT_ID = 99L;
    private static final Long DEST_OFFICE_ID = 2L;
    private static final String EMAIL = "user@test.com";
    private static final String DEVICE_FINGERPRINT = "device-abc";
    private static final String STEP_UP_TOKEN = "step-up-token";
    private static final String OTP = "123456";
    private static final String ACTION_FINGERPRINT = "action-fingerprint";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final Long TRANSFER_ID = 999L;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private OtpCommandService otpCommandService;

    @Mock
    private StepUpTokenService stepUpTokenService;

    @Mock
    private ClientApi clientApi;

    @Mock
    private SavingsAccountApi savingsAccountApi;

    @Mock
    private AccountTransfersApi accountTransfersApi;

    @InjectMocks
    private TransfersCommandServiceImpl service;

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .claim("scope", "read")
                .build();
    }

    private static UserQueryData user() {
        return UserQueryData.builder()
                .id(1L)
                .publicId(PUBLIC_ID)
                .fineractClientId(CLIENT_ID)
                .email(EMAIL)
                .status(UserStatus.BOUND)
                .build();
    }

    private static InitiateTransferCommand initiateSavingsCommand() {
        return InitiateTransferCommand.builder()
                .fromAccountId(FROM_SAVINGS_ID)
                .toAccountId(TO_SAVINGS_ID)
                .toAccountType(TransferConstants.SAVINGS_TYPE_NAME)
                .amount(AMOUNT)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private static ConfirmTransferCommand confirmSavingsCommand() {
        return ConfirmTransferCommand.builder()
                .stepUpToken(STEP_UP_TOKEN)
                .otp(OTP)
                .fromAccountId(FROM_SAVINGS_ID)
                .toAccountId(TO_SAVINGS_ID)
                .toAccountType(TransferConstants.SAVINGS_TYPE_NAME)
                .amount(AMOUNT)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    @Test
    void initiateSendsOtpIssuesTokenAndMasksDestination() {
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.canAccessSavings(CLIENT_ID, FROM_SAVINGS_ID)).thenReturn(true);
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.issue(eq(PUBLIC_ID), eq(DEVICE_FINGERPRINT), eq(ACTION_FINGERPRINT), any()))
                .thenReturn(IssuedJwt.builder().tokenValue(STEP_UP_TOKEN).expiresAt(expiresAt).build());

        TransferChallengeCommandData result = service.initiate(jwt(), initiateSavingsCommand());

        assertThat(result.getStepUpToken()).isEqualTo(STEP_UP_TOKEN);
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(result.getSentTo()).isEqualTo("u***@test.com");

        ArgumentCaptor<OtpDestination> destination = ArgumentCaptor.forClass(OtpDestination.class);
        verify(otpCommandService).createOtp(eq(PUBLIC_ID), destination.capture());
        assertThat(destination.getValue().getDeliveryMethod()).isEqualTo(OtpConstants.EMAIL_DELIVERY_METHOD_NAME);
        assertThat(destination.getValue().getTarget()).isEqualTo(EMAIL);
    }

    @Test
    void confirmCompletesTransfer() {
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.canAccessSavings(CLIENT_ID, FROM_SAVINGS_ID)).thenReturn(true);
        when(clientApi.retrieveOneClient(CLIENT_ID, false))
                .thenReturn(new GetClientsClientIdResponse().officeId(CALLER_OFFICE_ID));
        when(clientApi.retrieveOneClient(DEST_CLIENT_ID, false))
                .thenReturn(new GetClientsClientIdResponse().officeId(DEST_OFFICE_ID));
        when(savingsAccountApi.retrieveSavingsAccount(TO_SAVINGS_ID, null, null, null))
                .thenReturn(new SavingsAccountData().clientId(DEST_CLIENT_ID).officeId(DEST_OFFICE_ID));
        when(accountTransfersApi.createAccountTransfer(any()))
                .thenReturn(new PostAccountTransfersResponse().resourceId(TRANSFER_ID));

        TransferCommandData result = service.confirm(jwt(), confirmSavingsCommand());

        assertThat(result.getTransferId()).isEqualTo(TRANSFER_ID);
        assertThat(result.getFromAccountId()).isEqualTo(FROM_SAVINGS_ID);
        assertThat(result.getToAccountId()).isEqualTo(TO_SAVINGS_ID);
        assertThat(result.getAmount()).isEqualTo(AMOUNT);

        ArgumentCaptor<AccountTransferRequest> request = ArgumentCaptor.forClass(AccountTransferRequest.class);
        verify(accountTransfersApi).createAccountTransfer(request.capture());
        AccountTransferRequest sent = request.getValue();
        assertThat(sent.getFromOfficeId()).isEqualTo("1");
        assertThat(sent.getFromClientId()).isEqualTo("42");
        assertThat(sent.getFromAccountId()).isEqualTo("7");
        assertThat(sent.getFromAccountType()).isEqualTo(TransferConstants.SAVINGS_TYPE_CODE);
        assertThat(sent.getToOfficeId()).isEqualTo("2");
        assertThat(sent.getToClientId()).isEqualTo("99");
        assertThat(sent.getToAccountId()).isEqualTo("8");
        assertThat(sent.getToAccountType()).isEqualTo(TransferConstants.SAVINGS_TYPE_CODE);
        assertThat(sent.getTransferAmount()).isEqualTo("100.00");
        assertThat(sent.getLocale()).isEqualTo(TransferConstants.LOCALE);
        assertThat(sent.getDateFormat()).isEqualTo(TransferConstants.DATE_FORMAT);
        assertThat(sent.getTransferDescription()).isEqualTo(TransferConstants.DEFAULT_DESCRIPTION);
    }

    @Test
    void initiateRejectsUnknownAccountType() {
        InitiateTransferCommand command = InitiateTransferCommand.builder()
                .fromAccountId(FROM_SAVINGS_ID)
                .toAccountId(TO_SAVINGS_ID)
                .toAccountType("crypto")
                .amount(AMOUNT)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();

        assertThatThrownBy(() -> service.initiate(jwt(), command))
                .isInstanceOf(TransferInvalidException.class)
                .extracting(e -> TransferInvalidException.CODE)
                .isEqualTo(TransferInvalidException.CODE);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void initiateDeniedWhenAccessPolicyRejects() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.canAccessSavings(CLIENT_ID, FROM_SAVINGS_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.initiate(jwt(), initiateSavingsCommand()))
                .isInstanceOf(TransferAccessDeniedException.class)
                .extracting(e -> TransferAccessDeniedException.CODE)
                .isEqualTo(TransferAccessDeniedException.CODE);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void confirmRejectedWhenStepUpInvalid() {
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(jwt(), confirmSavingsCommand()))
                .isInstanceOf(TransferStepUpInvalidException.class)
                .extracting(e -> TransferStepUpInvalidException.CODE)
                .isEqualTo(TransferStepUpInvalidException.CODE);

        verify(accountTransfersApi, never()).createAccountTransfer(any());
    }

    @Test
    void confirmRejectedWhenOtpInvalid() {
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        org.mockito.Mockito.doThrow(new OtpTokenInvalidException())
                .when(otpCommandService).validateOtp(PUBLIC_ID, OTP);

        assertThatThrownBy(() -> service.confirm(jwt(), confirmSavingsCommand()))
                .isInstanceOf(TransferStepUpInvalidException.class)
                .extracting(e -> TransferStepUpInvalidException.CODE)
                .isEqualTo(TransferStepUpInvalidException.CODE);

        verify(accountTransfersApi, never()).createAccountTransfer(any());
    }
}
