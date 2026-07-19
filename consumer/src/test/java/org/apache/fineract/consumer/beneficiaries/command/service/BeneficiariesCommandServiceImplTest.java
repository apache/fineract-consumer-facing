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

package org.apache.fineract.consumer.beneficiaries.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.beneficiaries.command.data.BeneficiaryChallengeCommandData;
import org.apache.fineract.consumer.beneficiaries.command.data.BeneficiaryCommandData;
import org.apache.fineract.consumer.beneficiaries.command.data.BeneficiaryConstants;
import org.apache.fineract.consumer.beneficiaries.command.data.ConfirmAddBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.ConfirmUpdateBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.InitiateAddBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.InitiateUpdateBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.domain.Beneficiary;
import org.apache.fineract.consumer.beneficiaries.command.domain.BeneficiaryAccountType;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryAccountInvalidException;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryDuplicateNameException;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryNotFoundException;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryStepUpInvalidException;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryUpstreamUnavailableException;
import org.apache.fineract.consumer.beneficiaries.command.repository.BeneficiaryCommandRepository;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SearchApiApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdStatus;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSearchResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountStatusEnumData;
import org.apache.fineract.consumer.infrastructure.jwt.data.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpTokenService;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.service.OtpCommandService;
import org.apache.fineract.consumer.user.query.domain.UserStatus;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class BeneficiariesCommandServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final UUID BENEFICIARY_PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000002");
    private static final Long USER_ID = 1L;
    private static final String EMAIL = "user@test.com";
    private static final String DEVICE_FINGERPRINT = "device-abc";
    private static final String STEP_UP_TOKEN = "step-up-token";
    private static final String OTP = "123456";
    private static final String ACTION_FINGERPRINT = "action-fingerprint";
    private static final String NAME = "Alice Savings";
    private static final String OLD_NAME = "Old Name";
    private static final String MASKED_EMAIL = "u***@test.com";
    private static final String OFFICE_NAME = "Head Office";
    private static final String OTHER_OFFICE_NAME = "Other Office";
    private static final String ACCOUNT_NUMBER = "000000123";
    private static final String OTHER_ACCOUNT_NUMBER = "999999999";
    private static final BigDecimal TRANSFER_LIMIT = new BigDecimal("500.00");
    private static final String TRANSFER_LIMIT_FINGERPRINT_PART = "500";
    private static final Long LOAN_ID = 55L;
    private static final Long SAVINGS_ID = 66L;
    private static final Long DEST_CLIENT_ID = 99L;
    private static final Long DEST_OFFICE_ID = 2L;
    private static final Long ACTIVE_STATUS_ID = 300L;
    private static final Long CLOSED_STATUS_ID = 600L;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private OtpCommandService otpCommandService;

    @Mock
    private StepUpTokenService stepUpTokenService;

    @Mock
    private BeneficiaryCommandRepository beneficiaryCommandRepository;

    @Mock
    private LoansApi loansApi;

    @Mock
    private SavingsAccountApi savingsAccountApi;

    @Mock
    private SearchApiApi searchApiApi;

    @Mock
    private ClientApi clientApi;

    @InjectMocks
    private BeneficiariesCommandServiceImpl service;

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .build();
    }

    private static UserQueryData user() {
        return UserQueryData.builder()
                .id(USER_ID)
                .publicId(PUBLIC_ID)
                .fineractClientId(42L)
                .email(EMAIL)
                .status(UserStatus.BOUND)
                .build();
    }

    private static InitiateAddBeneficiaryCommand initiateAddLoanCommand() {
        return InitiateAddBeneficiaryCommand.builder()
                .name(NAME)
                .officeName(OFFICE_NAME)
                .accountNumber(ACCOUNT_NUMBER)
                .accountType(BeneficiaryAccountType.LOAN)
                .transferLimit(TRANSFER_LIMIT)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private static ConfirmAddBeneficiaryCommand confirmAddSavingsCommand() {
        return ConfirmAddBeneficiaryCommand.builder()
                .stepUpToken(STEP_UP_TOKEN)
                .otp(OTP)
                .name(NAME)
                .officeName(OFFICE_NAME)
                .accountNumber(ACCOUNT_NUMBER)
                .accountType(BeneficiaryAccountType.SAVINGS)
                .transferLimit(TRANSFER_LIMIT)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private static InitiateUpdateBeneficiaryCommand initiateUpdateCommand(String name) {
        return InitiateUpdateBeneficiaryCommand.builder()
                .publicId(BENEFICIARY_PUBLIC_ID)
                .name(name)
                .transferLimit(TRANSFER_LIMIT)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private static ConfirmUpdateBeneficiaryCommand confirmUpdateCommand(String name) {
        return ConfirmUpdateBeneficiaryCommand.builder()
                .stepUpToken(STEP_UP_TOKEN)
                .otp(OTP)
                .publicId(BENEFICIARY_PUBLIC_ID)
                .name(name)
                .transferLimit(TRANSFER_LIMIT)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .build();
    }

    private static Beneficiary existingBeneficiary(String name) {
        return Beneficiary.register(BENEFICIARY_PUBLIC_ID, USER_ID, name, DEST_OFFICE_ID, DEST_CLIENT_ID,
                SAVINGS_ID, BeneficiaryAccountType.SAVINGS, BigDecimal.TEN);
    }

    private static GetLoansResponse loanPage(Long statusId) {
        return new GetLoansResponse().pageItems(Set.of(new GetLoansLoanIdResponse()
                .id(LOAN_ID)
                .accountNo(ACCOUNT_NUMBER)
                .clientId(DEST_CLIENT_ID)
                .status(new GetLoansLoanIdStatus().id(statusId))));
    }

    private static SavingsAccountData savingsAccount(String accountNo, Long statusId) {
        return new SavingsAccountData()
                .accountNo(accountNo)
                .clientId(DEST_CLIENT_ID)
                .status(new SavingsAccountStatusEnumData().id(statusId));
    }

    private static GetClientsClientIdResponse destinationClient(String officeName) {
        return new GetClientsClientIdResponse().officeId(DEST_OFFICE_ID).officeName(officeName);
    }

    private void stubAddFingerprint(BeneficiaryAccountType accountType) {
        when(stepUpTokenService.actionFingerprint(BeneficiaryConstants.ADD_ACTION_ID,
                NAME, OFFICE_NAME, ACCOUNT_NUMBER, accountType.name(), TRANSFER_LIMIT_FINGERPRINT_PART))
                .thenReturn(ACTION_FINGERPRINT);
    }

    private void stubUpdateFingerprint(String name) {
        when(stepUpTokenService.actionFingerprint(BeneficiaryConstants.UPDATE_ACTION_ID,
                BENEFICIARY_PUBLIC_ID.toString(), name, TRANSFER_LIMIT_FINGERPRINT_PART))
                .thenReturn(ACTION_FINGERPRINT);
    }

    private void stubIssuedToken(Instant expiresAt) {
        when(stepUpTokenService.issue(eq(PUBLIC_ID), eq(DEVICE_FINGERPRINT), eq(ACTION_FINGERPRINT), any()))
                .thenReturn(IssuedJwt.builder().tokenValue(STEP_UP_TOKEN).expiresAt(expiresAt).build());
    }

    private void stubLoanResolution() {
        when(loansApi.retrieveAllLoans(null, null, null, null, null, ACCOUNT_NUMBER, null, null, null))
                .thenReturn(loanPage(ACTIVE_STATUS_ID));
        when(clientApi.retrieveOneClient(DEST_CLIENT_ID, false)).thenReturn(destinationClient(OFFICE_NAME));
    }

    private void stubSavingsResolution() {
        when(searchApiApi.searchData(ACCOUNT_NUMBER, BeneficiaryConstants.SAVINGS_SEARCH_RESOURCE, true))
                .thenReturn(List.of(new GetSearchResponse().entityId(SAVINGS_ID)));
        when(savingsAccountApi.retrieveSavingsAccount(SAVINGS_ID, null, null, null))
                .thenReturn(savingsAccount(ACCOUNT_NUMBER, ACTIVE_STATUS_ID));
        when(clientApi.retrieveOneClient(DEST_CLIENT_ID, false)).thenReturn(destinationClient(OFFICE_NAME));
    }

    private void stubValidConfirmAdd() {
        stubAddFingerprint(BeneficiaryAccountType.SAVINGS);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT))
                .thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(false);
        stubSavingsResolution();
    }

    @Test
    void initiateAddSendsOtpIssuesTokenAndMasksEmail() {
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(false);
        stubLoanResolution();
        stubAddFingerprint(BeneficiaryAccountType.LOAN);
        stubIssuedToken(expiresAt);

        BeneficiaryChallengeCommandData result = service.initiateAdd(jwt(), initiateAddLoanCommand());

        assertThat(result.getStepUpToken()).isEqualTo(STEP_UP_TOKEN);
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(result.getSentTo()).isEqualTo(MASKED_EMAIL);

        ArgumentCaptor<OtpDestination> destination = ArgumentCaptor.forClass(OtpDestination.class);
        verify(otpCommandService).createOtp(eq(PUBLIC_ID), destination.capture());
        assertThat(destination.getValue().getDeliveryMethod()).isEqualTo(OtpConstants.EMAIL_DELIVERY_METHOD_NAME);
        assertThat(destination.getValue().getTarget()).isEqualTo(EMAIL);
    }

    @Test
    void initiateAddRejectsDuplicateName() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(true);

        assertThatThrownBy(() -> service.initiateAdd(jwt(), initiateAddLoanCommand()))
                .isInstanceOf(BeneficiaryDuplicateNameException.class)
                .hasFieldOrPropertyWithValue("code", BeneficiaryDuplicateNameException.CODE);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void initiateAddRejectsUnresolvableLoanAccount() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(false);
        when(loansApi.retrieveAllLoans(null, null, null, null, null, ACCOUNT_NUMBER, null, null, null))
                .thenReturn(new GetLoansResponse().pageItems(Set.of()));

        assertThatThrownBy(() -> service.initiateAdd(jwt(), initiateAddLoanCommand()))
                .isInstanceOf(BeneficiaryAccountInvalidException.class)
                .hasFieldOrPropertyWithValue("code", BeneficiaryAccountInvalidException.CODE);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void initiateAddRejectsClosedLoan() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(false);
        when(loansApi.retrieveAllLoans(null, null, null, null, null, ACCOUNT_NUMBER, null, null, null))
                .thenReturn(loanPage(CLOSED_STATUS_ID));

        assertThatThrownBy(() -> service.initiateAdd(jwt(), initiateAddLoanCommand()))
                .isInstanceOf(BeneficiaryAccountInvalidException.class);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void initiateAddRejectsOfficeNameMismatch() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(false);
        when(loansApi.retrieveAllLoans(null, null, null, null, null, ACCOUNT_NUMBER, null, null, null))
                .thenReturn(loanPage(ACTIVE_STATUS_ID));
        when(clientApi.retrieveOneClient(DEST_CLIENT_ID, false)).thenReturn(destinationClient(OTHER_OFFICE_NAME));

        assertThatThrownBy(() -> service.initiateAdd(jwt(), initiateAddLoanCommand()))
                .isInstanceOf(BeneficiaryAccountInvalidException.class);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void initiateAddMapsFeignNotFoundToAccountInvalid() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(false);
        when(loansApi.retrieveAllLoans(null, null, null, null, null, ACCOUNT_NUMBER, null, null, null))
                .thenThrow(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> service.initiateAdd(jwt(), initiateAddLoanCommand()))
                .isInstanceOf(BeneficiaryAccountInvalidException.class);
    }

    @Test
    void initiateAddMapsOtherFeignFailureToUpstreamUnavailable() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(false);
        when(loansApi.retrieveAllLoans(null, null, null, null, null, ACCOUNT_NUMBER, null, null, null))
                .thenThrow(mock(FeignException.InternalServerError.class));

        assertThatThrownBy(() -> service.initiateAdd(jwt(), initiateAddLoanCommand()))
                .isInstanceOf(BeneficiaryUpstreamUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", BeneficiaryUpstreamUnavailableException.CODE);
    }

    @Test
    void confirmAddPersistsBeneficiary() {
        stubValidConfirmAdd();

        BeneficiaryCommandData result = service.confirmAdd(jwt(), confirmAddSavingsCommand());

        ArgumentCaptor<Beneficiary> saved = ArgumentCaptor.forClass(Beneficiary.class);
        verify(beneficiaryCommandRepository).saveAndFlush(saved.capture());
        Beneficiary beneficiary = saved.getValue();
        assertThat(beneficiary.getUserId()).isEqualTo(USER_ID);
        assertThat(beneficiary.getName()).isEqualTo(NAME);
        assertThat(beneficiary.getFineractOfficeId()).isEqualTo(DEST_OFFICE_ID);
        assertThat(beneficiary.getFineractClientId()).isEqualTo(DEST_CLIENT_ID);
        assertThat(beneficiary.getFineractAccountId()).isEqualTo(SAVINGS_ID);
        assertThat(beneficiary.getAccountType()).isEqualTo(BeneficiaryAccountType.SAVINGS);
        assertThat(beneficiary.getTransferLimit()).isEqualTo(TRANSFER_LIMIT);
        assertThat(beneficiary.isActive()).isTrue();

        assertThat(result.getPublicId()).isEqualTo(beneficiary.getPublicId());
        assertThat(result.getName()).isEqualTo(NAME);
        assertThat(result.getAccountType()).isEqualTo(BeneficiaryAccountType.SAVINGS);
        assertThat(result.getTransferLimit()).isEqualTo(TRANSFER_LIMIT);
    }

    @Test
    void confirmAddRejectsInvalidStepUp() {
        stubAddFingerprint(BeneficiaryAccountType.SAVINGS);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT))
                .thenReturn(false);

        assertThatThrownBy(() -> service.confirmAdd(jwt(), confirmAddSavingsCommand()))
                .isInstanceOf(BeneficiaryStepUpInvalidException.class)
                .hasFieldOrPropertyWithValue("code", BeneficiaryStepUpInvalidException.CODE);

        verify(beneficiaryCommandRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmAddRejectsInvalidOtp() {
        stubAddFingerprint(BeneficiaryAccountType.SAVINGS);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT))
                .thenReturn(true);
        doThrow(new OtpTokenInvalidException()).when(otpCommandService).validateOtp(PUBLIC_ID, OTP);

        assertThatThrownBy(() -> service.confirmAdd(jwt(), confirmAddSavingsCommand()))
                .isInstanceOf(BeneficiaryStepUpInvalidException.class);

        verify(beneficiaryCommandRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmAddSkipsSearchHitWhoseAccountNumberDiffers() {
        stubAddFingerprint(BeneficiaryAccountType.SAVINGS);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT))
                .thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(false);
        when(searchApiApi.searchData(ACCOUNT_NUMBER, BeneficiaryConstants.SAVINGS_SEARCH_RESOURCE, true))
                .thenReturn(List.of(new GetSearchResponse().entityId(SAVINGS_ID)));
        when(savingsAccountApi.retrieveSavingsAccount(SAVINGS_ID, null, null, null))
                .thenReturn(savingsAccount(OTHER_ACCOUNT_NUMBER, ACTIVE_STATUS_ID));

        assertThatThrownBy(() -> service.confirmAdd(jwt(), confirmAddSavingsCommand()))
                .isInstanceOf(BeneficiaryAccountInvalidException.class);

        verify(beneficiaryCommandRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmAddMapsIntegrityViolationToDuplicateName() {
        stubValidConfirmAdd();
        when(beneficiaryCommandRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.confirmAdd(jwt(), confirmAddSavingsCommand()))
                .isInstanceOf(BeneficiaryDuplicateNameException.class);
    }

    @Test
    void initiateUpdateSendsOtpAndIssuesToken() {
        Instant expiresAt = Instant.now().plusSeconds(300);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.findByPublicIdAndUserIdAndActiveTrue(BENEFICIARY_PUBLIC_ID, USER_ID))
                .thenReturn(Optional.of(existingBeneficiary(OLD_NAME)));
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(false);
        stubUpdateFingerprint(NAME);
        stubIssuedToken(expiresAt);

        BeneficiaryChallengeCommandData result = service.initiateUpdate(jwt(), initiateUpdateCommand(NAME));

        assertThat(result.getStepUpToken()).isEqualTo(STEP_UP_TOKEN);
        assertThat(result.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(result.getSentTo()).isEqualTo(MASKED_EMAIL);
        verify(otpCommandService).createOtp(eq(PUBLIC_ID), any());
    }

    @Test
    void initiateUpdateRejectsUnknownBeneficiary() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.findByPublicIdAndUserIdAndActiveTrue(BENEFICIARY_PUBLIC_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateUpdate(jwt(), initiateUpdateCommand(NAME)))
                .isInstanceOf(BeneficiaryNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", BeneficiaryNotFoundException.CODE);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void initiateUpdateRejectsDuplicateName() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.findByPublicIdAndUserIdAndActiveTrue(BENEFICIARY_PUBLIC_ID, USER_ID))
                .thenReturn(Optional.of(existingBeneficiary(OLD_NAME)));
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(true);

        assertThatThrownBy(() -> service.initiateUpdate(jwt(), initiateUpdateCommand(NAME)))
                .isInstanceOf(BeneficiaryDuplicateNameException.class);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void initiateUpdateAllowsCaseOnlyRename() {
        Instant expiresAt = Instant.now().plusSeconds(300);
        String renamed = NAME.toUpperCase(java.util.Locale.ROOT);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.findByPublicIdAndUserIdAndActiveTrue(BENEFICIARY_PUBLIC_ID, USER_ID))
                .thenReturn(Optional.of(existingBeneficiary(NAME)));
        stubUpdateFingerprint(renamed);
        stubIssuedToken(expiresAt);

        service.initiateUpdate(jwt(), initiateUpdateCommand(renamed));

        verify(beneficiaryCommandRepository, never())
                .existsByUserIdAndNameIgnoreCaseAndActiveTrue(anyLong(), anyString());
        verify(otpCommandService).createOtp(eq(PUBLIC_ID), any());
    }

    @Test
    void confirmUpdateUpdatesBeneficiary() {
        stubUpdateFingerprint(NAME);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT))
                .thenReturn(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        Beneficiary beneficiary = existingBeneficiary(OLD_NAME);
        when(beneficiaryCommandRepository.findByPublicIdAndUserIdAndActiveTrue(BENEFICIARY_PUBLIC_ID, USER_ID))
                .thenReturn(Optional.of(beneficiary));
        when(beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(USER_ID, NAME))
                .thenReturn(false);

        BeneficiaryCommandData result = service.confirmUpdate(jwt(), confirmUpdateCommand(NAME));

        verify(beneficiaryCommandRepository).saveAndFlush(beneficiary);
        assertThat(beneficiary.getName()).isEqualTo(NAME);
        assertThat(beneficiary.getTransferLimit()).isEqualTo(TRANSFER_LIMIT);
        assertThat(result.getPublicId()).isEqualTo(BENEFICIARY_PUBLIC_ID);
        assertThat(result.getName()).isEqualTo(NAME);
        assertThat(result.getTransferLimit()).isEqualTo(TRANSFER_LIMIT);
    }

    @Test
    void confirmUpdateRejectsInvalidStepUp() {
        stubUpdateFingerprint(NAME);
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT))
                .thenReturn(false);

        assertThatThrownBy(() -> service.confirmUpdate(jwt(), confirmUpdateCommand(NAME)))
                .isInstanceOf(BeneficiaryStepUpInvalidException.class);

        verify(beneficiaryCommandRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteDeactivatesBeneficiary() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        Beneficiary beneficiary = existingBeneficiary(NAME);
        when(beneficiaryCommandRepository.findByPublicIdAndUserIdAndActiveTrue(BENEFICIARY_PUBLIC_ID, USER_ID))
                .thenReturn(Optional.of(beneficiary));

        service.delete(jwt(), BENEFICIARY_PUBLIC_ID);

        verify(beneficiaryCommandRepository).save(beneficiary);
        assertThat(beneficiary.isActive()).isFalse();
    }

    @Test
    void initiateAddDeniedByPolicyPropagatesAndSendsNoOtp() {
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.BENEFICIARY_ADD));

        assertThatThrownBy(() -> service.initiateAdd(jwt(), initiateAddLoanCommand()))
                .isInstanceOf(AccessScopeInsufficientException.class);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void confirmAddDeniedByPolicyPropagatesAndPersistsNothing() {
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.BENEFICIARY_ADD));

        assertThatThrownBy(() -> service.confirmAdd(jwt(), confirmAddSavingsCommand()))
                .isInstanceOf(AccessScopeInsufficientException.class);

        verify(beneficiaryCommandRepository, never()).saveAndFlush(any());
    }

    @Test
    void initiateUpdateDeniedByPolicyPropagatesAndSendsNoOtp() {
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.BENEFICIARY_MODIFY));

        assertThatThrownBy(() -> service.initiateUpdate(jwt(), initiateUpdateCommand(NAME)))
                .isInstanceOf(AccessScopeInsufficientException.class);

        verify(otpCommandService, never()).createOtp(any(), any());
    }

    @Test
    void confirmUpdateDeniedByPolicyPropagatesAndPersistsNothing() {
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.BENEFICIARY_MODIFY));

        assertThatThrownBy(() -> service.confirmUpdate(jwt(), confirmUpdateCommand(NAME)))
                .isInstanceOf(AccessScopeInsufficientException.class);

        verify(beneficiaryCommandRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteDeniedByPolicyPropagatesAndPersistsNothing() {
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.BENEFICIARY_DELETE));

        assertThatThrownBy(() -> service.delete(jwt(), BENEFICIARY_PUBLIC_ID))
                .isInstanceOf(AccessScopeInsufficientException.class);

        verify(beneficiaryCommandRepository, never()).save(any());
    }

    @Test
    void deleteRejectsUnknownBeneficiary() {
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiaryCommandRepository.findByPublicIdAndUserIdAndActiveTrue(BENEFICIARY_PUBLIC_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(jwt(), BENEFICIARY_PUBLIC_ID))
                .isInstanceOf(BeneficiaryNotFoundException.class);

        verify(beneficiaryCommandRepository, never()).save(any());
    }
}
