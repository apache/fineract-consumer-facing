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

package org.apache.fineract.consumer.loans.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansLoanIdChargesChargeIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansLoanIdChargesChargeIdResponse;
import org.apache.fineract.consumer.infrastructure.jwt.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpTokenService;
import org.apache.fineract.consumer.infrastructure.web.AccessPolicyEvaluator;
import org.apache.fineract.consumer.loans.command.data.ConfirmLoanChargePaymentCommand;
import org.apache.fineract.consumer.loans.command.data.InitiateLoanChargePaymentCommand;
import org.apache.fineract.consumer.loans.command.data.LoanChargePaymentChallengeCommandData;
import org.apache.fineract.consumer.loans.command.data.LoanChargePaymentCommandData;
import org.apache.fineract.consumer.loans.command.data.LoanChargePaymentConstants;
import org.apache.fineract.consumer.loans.command.exception.LoanAccessDeniedException;
import org.apache.fineract.consumer.loans.command.exception.LoanChargePaymentStepUpInvalidException;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.service.OtpCommandService;
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
class LoansCommandServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long CLIENT_ID = 42L;
    private static final Long LOAN_ID = 7L;
    private static final Long CHARGE_ID = 13L;
    private static final String EMAIL = "user@test.com";
    private static final String DEVICE_FINGERPRINT = "device-abc";
    private static final String STEP_UP_TOKEN = "step-up-token";
    private static final String OTP = "123456";
    private static final String ACTION_FINGERPRINT = "action-fingerprint";

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private LoansApi loansApi;

    @Mock
    private OtpCommandService otpCommandService;

    @Mock
    private StepUpTokenService stepUpTokenService;

    @Mock
    private LoanChargesApi loanChargesApi;

    @InjectMocks
    private LoansCommandServiceImpl service;

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

    private static InitiateLoanChargePaymentCommand initiateCommand() {
        return InitiateLoanChargePaymentCommand.builder()
                .loanId(LOAN_ID)
                .chargeId(CHARGE_ID)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private static ConfirmLoanChargePaymentCommand confirmCommand() {
        return ConfirmLoanChargePaymentCommand.builder()
                .loanId(LOAN_ID)
                .chargeId(CHARGE_ID)
                .stepUpToken(STEP_UP_TOKEN)
                .otp(OTP)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    @Test
    void initiateSendsOtpIssuesTokenAndMasksDestination() {
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.canAccessLoans(CLIENT_ID, LOAN_ID)).thenReturn(true);
        when(stepUpTokenService.actionFingerprint(
                LoanChargePaymentConstants.ENDPOINT, LOAN_ID, CHARGE_ID))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.issue(eq(PUBLIC_ID), eq(DEVICE_FINGERPRINT), eq(ACTION_FINGERPRINT), any()))
                .thenReturn(IssuedJwt.builder().tokenValue(STEP_UP_TOKEN).expiresAt(expiresAt).build());

        LoanChargePaymentChallengeCommandData result = service.initiateChargePayment(jwt(), initiateCommand());

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
        when(accessPolicyEvaluator.canAccessLoans(CLIENT_ID, LOAN_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.initiateChargePayment(jwt(), initiateCommand()))
                .isInstanceOf(LoanAccessDeniedException.class)
                .extracting(e -> LoanAccessDeniedException.CODE)
                .isEqualTo(LoanAccessDeniedException.CODE);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void confirmPaysChargeWithPayCommandAndNoAmount() {
        when(stepUpTokenService.actionFingerprint(
                LoanChargePaymentConstants.ENDPOINT, LOAN_ID, CHARGE_ID))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.canAccessLoans(CLIENT_ID, LOAN_ID)).thenReturn(true);
        when(loanChargesApi.executeLoanChargeOnExistingCharge(
                eq(LOAN_ID), eq(CHARGE_ID), any(), eq(LoanChargePaymentConstants.PAY_COMMAND)))
                .thenReturn(new PostLoansLoanIdChargesChargeIdResponse().resourceId(99L));

        LoanChargePaymentCommandData result = service.confirmChargePayment(jwt(), confirmCommand());

        assertThat(result.getLoanId()).isEqualTo(LOAN_ID);
        assertThat(result.getChargeId()).isEqualTo(CHARGE_ID);
        assertThat(result.getResourceId()).isEqualTo(99L);

        ArgumentCaptor<PostLoansLoanIdChargesChargeIdRequest> body =
                ArgumentCaptor.forClass(PostLoansLoanIdChargesChargeIdRequest.class);
        verify(loanChargesApi).executeLoanChargeOnExistingCharge(
                eq(LOAN_ID), eq(CHARGE_ID), body.capture(),
                eq(LoanChargePaymentConstants.PAY_COMMAND));
        assertThat(body.getValue().getAmount()).isNull();
        assertThat(body.getValue().getLocale()).isEqualTo(LoanChargePaymentConstants.LOCALE);
        assertThat(body.getValue().getDateFormat()).isEqualTo(LoanChargePaymentConstants.DATE_FORMAT);
        assertThat(body.getValue().getTransactionDate()).isNotBlank();
    }

    @Test
    void confirmRejectedWhenStepUpTokenInvalid() {
        when(stepUpTokenService.actionFingerprint(
                LoanChargePaymentConstants.ENDPOINT, LOAN_ID, CHARGE_ID))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(false);

        assertThatThrownBy(() -> service.confirmChargePayment(jwt(), confirmCommand()))
                .isInstanceOf(LoanChargePaymentStepUpInvalidException.class)
                .extracting(e -> LoanChargePaymentStepUpInvalidException.CODE)
                .isEqualTo(LoanChargePaymentStepUpInvalidException.CODE);

        verify(loanChargesApi, never()).executeLoanChargeOnExistingCharge(any(), any(), any(), any());
    }

    @Test
    void confirmRejectedWhenOtpInvalid() {
        when(stepUpTokenService.actionFingerprint(
                LoanChargePaymentConstants.ENDPOINT, LOAN_ID, CHARGE_ID))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        doThrowOtpInvalid();

        assertThatThrownBy(() -> service.confirmChargePayment(jwt(), confirmCommand()))
                .isInstanceOf(LoanChargePaymentStepUpInvalidException.class)
                .extracting(e -> LoanChargePaymentStepUpInvalidException.CODE)
                .isEqualTo(LoanChargePaymentStepUpInvalidException.CODE);

        verify(loanChargesApi, never()).executeLoanChargeOnExistingCharge(any(), any(), any(), any());
    }

    private void doThrowOtpInvalid() {
        org.mockito.Mockito.doThrow(new OtpTokenInvalidException())
                .when(otpCommandService).validateOtp(PUBLIC_ID, OTP);
    }
}
