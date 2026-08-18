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

import static org.apache.fineract.consumer.testsupport.ExceptionSupplierAnswers.throwsCallerSuppliedException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import feign.Request;
import feign.Response;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.beneficiaries.query.data.BeneficiaryQueryData;
import org.apache.fineract.consumer.beneficiaries.query.service.BeneficiariesQueryService;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.data.ResourceType;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.AccountTransfersApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.AccountTransferRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostAccountTransfersResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyDeriver;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyHolder;
import org.apache.fineract.consumer.infrastructure.jwt.data.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.service.StepUpTokenService;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpConstants;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpDestination;
import org.apache.fineract.consumer.infrastructure.otp.service.OtpService;
import org.apache.fineract.consumer.transfers.command.data.ConfirmTransferCommand;
import org.apache.fineract.consumer.transfers.command.data.InitiateTransferCommand;
import org.apache.fineract.consumer.transfers.command.data.TransferChallengeCommandData;
import org.apache.fineract.consumer.transfers.command.data.TransferCommandData;
import org.apache.fineract.consumer.transfers.command.data.TransferConstants;
import org.apache.fineract.consumer.transfers.command.exception.TransferAccessDeniedException;
import org.apache.fineract.consumer.transfers.command.exception.TransferBeneficiaryLimitExceededException;
import org.apache.fineract.consumer.transfers.command.exception.TransferInProgressException;
import org.apache.fineract.consumer.transfers.command.exception.TransferInvalidException;
import org.apache.fineract.consumer.transfers.command.exception.TransferStepUpInvalidException;
import org.apache.fineract.consumer.transfers.command.exception.TransferUpstreamUnavailableException;
import org.apache.fineract.consumer.user.query.data.UserStatus;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class TransfersCommandServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long USER_ID = 1L;
    private static final Long CLIENT_ID = 42L;
    private static final Long FROM_SAVINGS_ID = 7L;
    private static final Long TO_SAVINGS_ID = 8L;
    private static final Long TO_LOAN_ID = 9L;
    private static final Long CALLER_OFFICE_ID = 1L;
    private static final Long DEST_CLIENT_ID = 99L;
    private static final Long DEST_OFFICE_ID = 2L;
    private static final String EMAIL = "user@test.com";
    private static final String DEVICE_FINGERPRINT = "device-abc";
    private static final String STEP_UP_TOKEN = "step-up-token";
    private static final String OTP = "123456";
    private static final String ACTION_FINGERPRINT = "action-fingerprint";
    private static final BigDecimal AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal BENEFICIARY_LIMIT_ABOVE_AMOUNT = new BigDecimal("200.00");
    private static final BigDecimal BENEFICIARY_LIMIT_BELOW_AMOUNT = new BigDecimal("50.00");
    private static final BigDecimal BENEFICIARY_LIMIT = new BigDecimal("500.00");
    private static final BigDecimal AMOUNT_ABOVE_BENEFICIARY_LIMIT = new BigDecimal("501.00");
    private static final Long TRANSFER_ID = 999L;
    private static final String IDEMPOTENCY_KEY = "txn-key-1";

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private BeneficiariesQueryService beneficiariesQueryService;

    @Mock
    private OtpService otpService;

    @Mock
    private StepUpTokenService stepUpTokenService;

    @Mock
    private ClientApi clientApi;

    @Mock
    private SavingsAccountApi savingsAccountApi;

    @Mock
    private AccountTransfersApi accountTransfersApi;

    @Mock
    private IdempotencyKeyHolder idempotencyKeyHolder;

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
                .id(USER_ID)
                .publicId(PUBLIC_ID)
                .fineractClientId(CLIENT_ID)
                .email(EMAIL)
                .status(UserStatus.BOUND)
                .build();
    }

    private static BeneficiaryQueryData beneficiary(BigDecimal transferLimit) {
        return BeneficiaryQueryData.builder()
                .publicId(UUID.fromString("3f2c8a1e-0000-4000-8000-000000000002"))
                .name("Alice")
                .fineractAccountId(TO_SAVINGS_ID)
                .transferLimit(transferLimit)
                .build();
    }

    private static InitiateTransferCommand initiateSavingsCommand() {
        return initiateSavingsCommand(AMOUNT);
    }

    private static InitiateTransferCommand initiateSavingsCommand(BigDecimal amount) {
        return InitiateTransferCommand.builder()
                .fromAccountId(FROM_SAVINGS_ID)
                .toAccountId(TO_SAVINGS_ID)
                .toAccountType(TransferConstants.SAVINGS_TYPE_NAME)
                .amount(amount)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private static InitiateTransferCommand initiateLoanCommand() {
        return InitiateTransferCommand.builder()
                .fromAccountId(FROM_SAVINGS_ID)
                .toAccountId(TO_LOAN_ID)
                .toAccountType(TransferConstants.LOAN_TYPE_NAME)
                .amount(AMOUNT)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private static ConfirmTransferCommand confirmLoanCommand() {
        return ConfirmTransferCommand.builder()
                .stepUpToken(STEP_UP_TOKEN)
                .otp(OTP)
                .fromAccountId(FROM_SAVINGS_ID)
                .toAccountId(TO_LOAN_ID)
                .toAccountType(TransferConstants.LOAN_TYPE_NAME)
                .amount(AMOUNT)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();
    }

    private static ConfirmTransferCommand confirmSavingsCommand() {
        return confirmSavingsCommand(AMOUNT);
    }

    private static ConfirmTransferCommand confirmSavingsCommand(BigDecimal amount) {
        return ConfirmTransferCommand.builder()
                .stepUpToken(STEP_UP_TOKEN)
                .otp(OTP)
                .fromAccountId(FROM_SAVINGS_ID)
                .toAccountId(TO_SAVINGS_ID)
                .toAccountType(TransferConstants.SAVINGS_TYPE_NAME)
                .amount(amount)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .build();
    }

    private static FeignException feignException(int status) {
        Request request = Request.create(Request.HttpMethod.POST, "/test", Map.of(), null,
                StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(status).request(request).headers(Map.of()).build();
        return FeignException.errorStatus("test", response);
    }

    private void stubConfirmUpToUpstreamWrite() {
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.of(beneficiary(BENEFICIARY_LIMIT_ABOVE_AMOUNT)));
        when(clientApi.retrieveOneClient(CLIENT_ID, false))
                .thenReturn(new GetClientsClientIdResponse().officeId(CALLER_OFFICE_ID));
        when(clientApi.retrieveOneClient(DEST_CLIENT_ID, false))
                .thenReturn(new GetClientsClientIdResponse().officeId(DEST_OFFICE_ID));
        when(savingsAccountApi.retrieveSavingsAccount(TO_SAVINGS_ID, null, null, null))
                .thenReturn(new SavingsAccountData().clientId(DEST_CLIENT_ID).officeId(DEST_OFFICE_ID));
    }

    private void stubSavingsFingerprintAndIssue() {
        stubSavingsFingerprintAndIssue(AMOUNT);
    }

    private void stubSavingsFingerprintAndIssue(BigDecimal amount) {
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE, amount))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.issue(eq(PUBLIC_ID), eq(DEVICE_FINGERPRINT), eq(ACTION_FINGERPRINT), any()))
                .thenReturn(IssuedJwt.builder().tokenValue(STEP_UP_TOKEN).expiresAt(expiresAt).build());
    }

    @Test
    void initiateSendsOtpIssuesTokenAndMasksDestination() {
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.of(beneficiary(null)));
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.issue(eq(PUBLIC_ID), eq(DEVICE_FINGERPRINT), eq(ACTION_FINGERPRINT), any()))
                .thenReturn(IssuedJwt.builder().tokenValue(STEP_UP_TOKEN).expiresAt(expiresAt).build());

        TransferChallengeCommandData result = service.initiate(jwt(), initiateSavingsCommand());

        assertThat(result.getStepUpToken()).isEqualTo(STEP_UP_TOKEN);
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(result.getSentTo()).isEqualTo("u***@test.com");

        verify(accessPolicyEvaluator).authorize(any(), eq(ConsumerAction.TRANSFER_EXECUTE), eq(FROM_SAVINGS_ID), any());

        ArgumentCaptor<OtpDestination> destination = ArgumentCaptor.forClass(OtpDestination.class);
        verify(otpService).createOtp(eq(PUBLIC_ID), destination.capture());
        assertThat(destination.getValue().getDeliveryMethod()).isEqualTo(OtpConstants.EMAIL_DELIVERY_METHOD_NAME);
        assertThat(destination.getValue().getTarget()).isEqualTo(EMAIL);
    }

    @Test
    void initiateAllowsOwnedDestinationWithoutBeneficiaryRow() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID)).thenReturn(Optional.empty());
        when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.SAVINGS), eq(TO_SAVINGS_ID))).thenReturn(true);
        stubSavingsFingerprintAndIssue();

        service.initiate(jwt(), initiateSavingsCommand());

        verify(otpService).createOtp(eq(PUBLIC_ID), any());
    }

    @Test
    void initiateAllowsOwnLoanDestination() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.LOANS), eq(TO_LOAN_ID))).thenReturn(true);
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_LOAN_ID, TransferConstants.LOAN_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.issue(eq(PUBLIC_ID), eq(DEVICE_FINGERPRINT), eq(ACTION_FINGERPRINT), any()))
                .thenReturn(IssuedJwt.builder().tokenValue(STEP_UP_TOKEN).expiresAt(expiresAt).build());

        service.initiate(jwt(), initiateLoanCommand());

        verify(beneficiariesQueryService, never()).findActiveByAccount(any(), any());
        verify(otpService).createOtp(eq(PUBLIC_ID), any());
    }

    @Test
    void initiateDeniedWhenLoanDestinationNotOwned() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.LOANS), eq(TO_LOAN_ID))).thenReturn(false);

        assertThatThrownBy(() -> service.initiate(jwt(), initiateLoanCommand()))
                .isInstanceOf(TransferAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", TransferAccessDeniedException.CODE);

        verify(beneficiariesQueryService, never()).findActiveByAccount(any(), any());
        verify(otpService, never()).createOtp(any(), any());
    }

    @Test
    void initiateAllowsBeneficiaryWithinLimit() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.of(beneficiary(BENEFICIARY_LIMIT_ABOVE_AMOUNT)));
        stubSavingsFingerprintAndIssue();

        service.initiate(jwt(), initiateSavingsCommand());

        verify(otpService).createOtp(eq(PUBLIC_ID), any());
    }

    @Test
    void initiateDeniedWhenBeneficiaryLimitExceeded() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.of(beneficiary(BENEFICIARY_LIMIT_BELOW_AMOUNT)));

        assertThatThrownBy(() -> service.initiate(jwt(), initiateSavingsCommand()))
                .isInstanceOf(TransferBeneficiaryLimitExceededException.class)
                .hasFieldOrPropertyWithValue("code", TransferBeneficiaryLimitExceededException.CODE);

        verify(otpService, never()).createOtp(any(), any());
    }

    @Test
    void initiateDeniedWhenOwnedDestinationExceedsBeneficiaryLimit() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        lenient().when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.SAVINGS), eq(TO_SAVINGS_ID)))
                .thenReturn(true);
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.of(beneficiary(BENEFICIARY_LIMIT)));

        assertThatThrownBy(() -> service.initiate(jwt(), initiateSavingsCommand(AMOUNT_ABOVE_BENEFICIARY_LIMIT)))
                .isInstanceOf(TransferBeneficiaryLimitExceededException.class)
                .hasFieldOrPropertyWithValue("code", TransferBeneficiaryLimitExceededException.CODE);

        verify(otpService, never()).createOtp(any(), any());
    }

    @Test
    void initiateAllowsOwnedDestinationAtBeneficiaryLimit() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        lenient().when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.SAVINGS), eq(TO_SAVINGS_ID)))
                .thenReturn(true);
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.of(beneficiary(BENEFICIARY_LIMIT)));
        stubSavingsFingerprintAndIssue(BENEFICIARY_LIMIT);

        service.initiate(jwt(), initiateSavingsCommand(BENEFICIARY_LIMIT));

        verify(otpService).createOtp(eq(PUBLIC_ID), any());
    }

    @Test
    void initiateDeniedWhenDestinationUnregistered() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.SAVINGS), eq(TO_SAVINGS_ID))).thenReturn(false);
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiate(jwt(), initiateSavingsCommand()))
                .isInstanceOf(TransferAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", TransferAccessDeniedException.CODE);

        verify(otpService, never()).createOtp(any(), any());
    }

    @Test
    void initiateDeniedWhenAuthorizeRejects() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        doThrow(new TransferAccessDeniedException())
                .when(accessPolicyEvaluator).authorize(any(), eq(ConsumerAction.TRANSFER_EXECUTE), eq(FROM_SAVINGS_ID), any());

        assertThatThrownBy(() -> service.initiate(jwt(), initiateSavingsCommand()))
                .isInstanceOf(TransferAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", TransferAccessDeniedException.CODE);

        verify(beneficiariesQueryService, never()).findActiveByAccount(any(), any());
        verify(otpService, never()).createOtp(any(), any());
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
                .hasFieldOrPropertyWithValue("code", TransferInvalidException.CODE);

        verify(otpService, never()).createOtp(any(), any());
    }

    @Test
    void confirmCompletesTransferToBeneficiaryDestination() {
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.of(beneficiary(BENEFICIARY_LIMIT_ABOVE_AMOUNT)));
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

        verify(accessPolicyEvaluator).authorize(any(), eq(ConsumerAction.TRANSFER_EXECUTE), eq(FROM_SAVINGS_ID), any());

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
    void confirmCompletesTransferToOwnLoanDestination() {
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_LOAN_ID, TransferConstants.LOAN_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.LOANS), eq(TO_LOAN_ID))).thenReturn(true);
        when(clientApi.retrieveOneClient(CLIENT_ID, false))
                .thenReturn(new GetClientsClientIdResponse().officeId(CALLER_OFFICE_ID));
        when(accountTransfersApi.createAccountTransfer(any()))
                .thenReturn(new PostAccountTransfersResponse().resourceId(TRANSFER_ID));

        TransferCommandData result = service.confirm(jwt(), confirmLoanCommand());

        assertThat(result.getTransferId()).isEqualTo(TRANSFER_ID);
        assertThat(result.getToAccountId()).isEqualTo(TO_LOAN_ID);

        ArgumentCaptor<AccountTransferRequest> request = ArgumentCaptor.forClass(AccountTransferRequest.class);
        verify(accountTransfersApi).createAccountTransfer(request.capture());
        AccountTransferRequest sent = request.getValue();
        assertThat(sent.getToAccountType()).isEqualTo(TransferConstants.LOAN_TYPE_CODE);
        assertThat(sent.getToClientId()).isEqualTo(String.valueOf(CLIENT_ID));
        assertThat(sent.getToOfficeId()).isEqualTo(String.valueOf(CALLER_OFFICE_ID));
        verify(beneficiariesQueryService, never()).findActiveByAccount(any(), any());
    }

    @Test
    void confirmDeniedWhenLoanDestinationNotOwned() {
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_LOAN_ID, TransferConstants.LOAN_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.LOANS), eq(TO_LOAN_ID))).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(jwt(), confirmLoanCommand()))
                .isInstanceOf(TransferAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", TransferAccessDeniedException.CODE);

        verify(beneficiariesQueryService, never()).findActiveByAccount(any(), any());
        verify(accountTransfersApi, never()).createAccountTransfer(any());
    }

    @Test
    void confirmDeniedWhenBeneficiaryLimitExceeded() {
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.of(beneficiary(BENEFICIARY_LIMIT_BELOW_AMOUNT)));

        assertThatThrownBy(() -> service.confirm(jwt(), confirmSavingsCommand()))
                .isInstanceOf(TransferBeneficiaryLimitExceededException.class)
                .hasFieldOrPropertyWithValue("code", TransferBeneficiaryLimitExceededException.CODE);

        verify(accountTransfersApi, never()).createAccountTransfer(any());
    }

    @Test
    void confirmDeniedWhenOwnedDestinationExceedsBeneficiaryLimit() {
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE,
                AMOUNT_ABOVE_BENEFICIARY_LIMIT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        lenient().when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.SAVINGS), eq(TO_SAVINGS_ID)))
                .thenReturn(true);
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.of(beneficiary(BENEFICIARY_LIMIT)));

        assertThatThrownBy(() -> service.confirm(jwt(), confirmSavingsCommand(AMOUNT_ABOVE_BENEFICIARY_LIMIT)))
                .isInstanceOf(TransferBeneficiaryLimitExceededException.class)
                .hasFieldOrPropertyWithValue("code", TransferBeneficiaryLimitExceededException.CODE);

        verify(accountTransfersApi, never()).createAccountTransfer(any());
    }

    @Test
    void confirmRejectedWhenStepUpInvalid() {
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(jwt(), confirmSavingsCommand()))
                .isInstanceOf(TransferStepUpInvalidException.class)
                .hasFieldOrPropertyWithValue("code", TransferStepUpInvalidException.CODE);

        verify(accountTransfersApi, never()).createAccountTransfer(any());
    }

    @Test
    void confirmRejectedWhenOtpInvalid() {
        when(stepUpTokenService.actionFingerprint(
                TransferConstants.ENDPOINT, FROM_SAVINGS_ID, TO_SAVINGS_ID, TransferConstants.SAVINGS_TYPE_CODE, AMOUNT))
                .thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        doAnswer(throwsCallerSuppliedException(2)).when(otpService).validateOtp(eq(PUBLIC_ID), eq(OTP), any());

        assertThatThrownBy(() -> service.confirm(jwt(), confirmSavingsCommand()))
                .isInstanceOf(TransferStepUpInvalidException.class)
                .hasFieldOrPropertyWithValue("code", TransferStepUpInvalidException.CODE);

        verify(accountTransfersApi, never()).createAccountTransfer(any());
    }

    @Test
    void confirmSetsDerivedIdempotencyKeyBeforeUpstreamWriteAndClearsAfter() {
        stubConfirmUpToUpstreamWrite();
        when(accountTransfersApi.createAccountTransfer(any()))
                .thenReturn(new PostAccountTransfersResponse().resourceId(TRANSFER_ID));

        service.confirm(jwt(), confirmSavingsCommand());

        InOrder inOrder = inOrder(idempotencyKeyHolder, accountTransfersApi);
        inOrder.verify(idempotencyKeyHolder).set(IdempotencyKeyDeriver.derive(PUBLIC_ID, IDEMPOTENCY_KEY));
        inOrder.verify(accountTransfersApi).createAccountTransfer(any());
        inOrder.verify(idempotencyKeyHolder).clear();
    }

    @Test
    void confirmClearsIdempotencyKeyWhenUpstreamFails() {
        stubConfirmUpToUpstreamWrite();
        when(accountTransfersApi.createAccountTransfer(any())).thenThrow(feignException(503));

        assertThatThrownBy(() -> service.confirm(jwt(), confirmSavingsCommand()))
                .isInstanceOf(TransferUpstreamUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", TransferUpstreamUnavailableException.CODE);

        verify(idempotencyKeyHolder).clear();
    }

    @Test
    void confirmTranslatesUpstreamTooEarlyToTransferInProgress() {
        stubConfirmUpToUpstreamWrite();
        when(accountTransfersApi.createAccountTransfer(any())).thenThrow(feignException(425));

        assertThatThrownBy(() -> service.confirm(jwt(), confirmSavingsCommand()))
                .isInstanceOf(TransferInProgressException.class)
                .hasFieldOrPropertyWithValue("code", TransferInProgressException.CODE);

        verify(idempotencyKeyHolder).clear();
    }
}
