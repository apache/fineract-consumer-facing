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

package org.apache.fineract.consumer.savings.command.service;

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
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdResponse;
import org.apache.fineract.consumer.infrastructure.jwt.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpTokenService;
import org.apache.fineract.consumer.infrastructure.web.AccessPolicyEvaluator;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.service.OtpCommandService;
import org.apache.fineract.consumer.savings.command.data.ConfirmSavingsChargePaymentCommand;
import org.apache.fineract.consumer.savings.command.data.InitiateSavingsChargePaymentCommand;
import org.apache.fineract.consumer.savings.command.data.SavingsChargePaymentChallengeCommandData;
import org.apache.fineract.consumer.savings.command.data.SavingsChargePaymentCommandData;
import org.apache.fineract.consumer.savings.command.data.SavingsChargePaymentConstants;
import org.apache.fineract.consumer.savings.command.exception.SavingsAccountAccessDeniedException;
import org.apache.fineract.consumer.savings.command.exception.SavingsChargePaymentStepUpInvalidException;
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
class SavingsCommandServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long CLIENT_ID = 42L;
    private static final Long SAVINGS_ID = 7L;
    private static final Long CHARGE_ID = 13L;
    private static final String EMAIL = "user@test.com";
    private static final String DEVICE_FINGERPRINT = "device-abc";
    private static final String STEP_UP_TOKEN = "step-up-token";
    private static final String OTP = "123456";
    private static final String ACTION_FINGERPRINT = "action-fingerprint";
    private static final BigDecimal AMOUNT = new BigDecimal("12.50");

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private OtpCommandService otpCommandService;

    @Mock
    private StepUpTokenService stepUpTokenService;

    @Mock
    private SavingsChargesApi savingsChargesApi;

    @InjectMocks
    private SavingsCommandServiceImpl service;

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

    private static InitiateSavingsChargePaymentCommand initiateCommand() {
        return InitiateSavingsChargePaymentCommand.builder()
                .savingsId(SAVINGS_ID)
                .chargeId(CHARGE_ID)
                .amount(AMOUNT)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private static ConfirmSavingsChargePaymentCommand confirmCommand() {
        return ConfirmSavingsChargePaymentCommand.builder()
                .savingsId(SAVINGS_ID)
                .chargeId(CHARGE_ID)
                .stepUpToken(STEP_UP_TOKEN)
                .otp(OTP)
                .amount(AMOUNT)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    @Test
    void initiateSendsOtpIssuesTokenAndMasksDestination() {
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.canAccessSavings(CLIENT_ID, SAVINGS_ID)).thenReturn(true);
        when(stepUpTokenService.actionFingerprint(
                SavingsChargePaymentConstants.ENDPOINT, SAVINGS_ID, CHARGE_ID, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.issue(eq(PUBLIC_ID), eq(DEVICE_FINGERPRINT), eq(ACTION_FINGERPRINT), any()))
                .thenReturn(IssuedJwt.builder().tokenValue(STEP_UP_TOKEN).expiresAt(expiresAt).build());

        SavingsChargePaymentChallengeCommandData result = service.initiateChargePayment(jwt(), initiateCommand());

        assertThat(result.getStepUpToken()).isEqualTo(STEP_UP_TOKEN);
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(result.getSentTo()).isEqualTo("u***@test.com");

        ArgumentCaptor<OtpDestination> destination = ArgumentCaptor.forClass(OtpDestination.class);
        verify(otpCommandService).createOtp(eq(PUBLIC_ID), destination.capture());
        assertThat(destination.getValue().getDeliveryMethod()).isEqualTo(OtpConstants.EMAIL_DELIVERY_METHOD_NAME);
        assertThat(destination.getValue().getTarget()).isEqualTo(EMAIL);
    }

    @Test
    void initiateDeniedWhenAccessPolicyRejects() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.canAccessSavings(CLIENT_ID, SAVINGS_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.initiateChargePayment(jwt(), initiateCommand()))
                .isInstanceOf(SavingsAccountAccessDeniedException.class)
                .extracting(e -> SavingsAccountAccessDeniedException.CODE)
                .isEqualTo(SavingsAccountAccessDeniedException.CODE);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void confirmPaysChargeWithPaychargeCommandAndAmount() {
        when(stepUpTokenService.actionFingerprint(
                SavingsChargePaymentConstants.ENDPOINT, SAVINGS_ID, CHARGE_ID, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.canAccessSavings(CLIENT_ID, SAVINGS_ID)).thenReturn(true);
        when(savingsChargesApi.payOrWaiveSavingsAccountCharge(
                eq(SAVINGS_ID), eq(CHARGE_ID), any(), eq(SavingsChargePaymentConstants.PAYCHARGE_COMMAND)))
                .thenReturn(new PostSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdResponse()
                        .resourceId(99L));

        SavingsChargePaymentCommandData result = service.confirmChargePayment(jwt(), confirmCommand());

        assertThat(result.getSavingsId()).isEqualTo(SAVINGS_ID);
        assertThat(result.getChargeId()).isEqualTo(CHARGE_ID);
        assertThat(result.getAmount()).isEqualTo(AMOUNT);
        assertThat(result.getResourceId()).isEqualTo(99L);

        ArgumentCaptor<PostSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdRequest> body =
                ArgumentCaptor.forClass(PostSavingsAccountsSavingsAccountIdChargesSavingsAccountChargeIdRequest.class);
        verify(savingsChargesApi).payOrWaiveSavingsAccountCharge(
                eq(SAVINGS_ID), eq(CHARGE_ID), body.capture(),
                eq(SavingsChargePaymentConstants.PAYCHARGE_COMMAND));
        assertThat(body.getValue().getAmount()).isEqualTo(AMOUNT.floatValue());
        assertThat(body.getValue().getLocale()).isEqualTo(SavingsChargePaymentConstants.LOCALE);
        assertThat(body.getValue().getDateFormat()).isEqualTo(SavingsChargePaymentConstants.DATE_FORMAT);
        assertThat(body.getValue().getDueDate()).isNotBlank();
    }

    @Test
    void confirmRejectedWhenStepUpTokenInvalid() {
        when(stepUpTokenService.actionFingerprint(
                SavingsChargePaymentConstants.ENDPOINT, SAVINGS_ID, CHARGE_ID, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(false);

        assertThatThrownBy(() -> service.confirmChargePayment(jwt(), confirmCommand()))
                .isInstanceOf(SavingsChargePaymentStepUpInvalidException.class)
                .extracting(e -> SavingsChargePaymentStepUpInvalidException.CODE)
                .isEqualTo(SavingsChargePaymentStepUpInvalidException.CODE);

        verify(savingsChargesApi, never()).payOrWaiveSavingsAccountCharge(any(), any(), any(), any());
    }

    @Test
    void confirmRejectedWhenOtpInvalid() {
        when(stepUpTokenService.actionFingerprint(
                SavingsChargePaymentConstants.ENDPOINT, SAVINGS_ID, CHARGE_ID, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        doThrowOtpInvalid();

        assertThatThrownBy(() -> service.confirmChargePayment(jwt(), confirmCommand()))
                .isInstanceOf(SavingsChargePaymentStepUpInvalidException.class)
                .extracting(e -> SavingsChargePaymentStepUpInvalidException.CODE)
                .isEqualTo(SavingsChargePaymentStepUpInvalidException.CODE);

        verify(savingsChargesApi, never()).payOrWaiveSavingsAccountCharge(any(), any(), any(), any());
    }

    private void doThrowOtpInvalid() {
        org.mockito.Mockito.doThrow(new OtpTokenInvalidException())
                .when(otpCommandService).validateOtp(PUBLIC_ID, OTP);
    }
}
