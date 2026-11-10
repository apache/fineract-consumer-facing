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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.scheduledtransfers.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import feign.FeignException;
import feign.Request;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.beneficiaries.query.data.BeneficiaryQueryData;
import org.apache.fineract.consumer.beneficiaries.query.service.BeneficiariesQueryService;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.data.ResourceType;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.TransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.exception.AbstractConsumerException;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.CurrencyData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.StandingInstructionCreationRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.BusinessDateFineractClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.SavingsFineractClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.TransferFineractClient;
import org.apache.fineract.consumer.infrastructure.idempotency.data.DerivedIdempotencyKey;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyDeriver;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyHolder;
import org.apache.fineract.consumer.infrastructure.jwt.data.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpPurpose;
import org.apache.fineract.consumer.infrastructure.otp.service.OtpService;
import org.apache.fineract.consumer.infrastructure.stepup.data.StepUpConstants;
import org.apache.fineract.consumer.infrastructure.stepup.service.StepUpTokenService;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ConfirmScheduledTransferCommand;
import org.apache.fineract.consumer.scheduledtransfers.command.data.InitiateScheduledTransferCommand;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferChallengeCommandData;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferCommandData;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferConstants;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferFrequency;
import org.apache.fineract.consumer.scheduledtransfers.command.domain.ScheduledTransfer;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferAccessDeniedException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferBeneficiaryLimitExceededException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferCurrencyMismatchException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferDisabledException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferInvalidException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferStepUpInvalidException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferUpstreamUnavailableException;
import org.apache.fineract.consumer.scheduledtransfers.command.repository.ScheduledTransferCommandRepository;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.data.UserStatus;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduledTransfersCommandServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long USER_ID = 1L;
    private static final Long CLIENT_ID = 42L;
    private static final Long FROM_SAVINGS_ID = 7L;
    private static final Long TO_SAVINGS_ID = 8L;
    private static final Long CALLER_OFFICE_ID = 1L;
    private static final Long DEST_CLIENT_ID = 99L;
    private static final Long DEST_OFFICE_ID = 2L;
    private static final Long STANDING_INSTRUCTION_ID = 555L;
    private static final String EMAIL = "user@test.com";
    private static final String DEVICE_FINGERPRINT = "device-abc";
    private static final String STEP_UP_TOKEN = "step-up-token";
    private static final String OTP = "123456";
    private static final String ACTION_FINGERPRINT = "action-fingerprint";
    private static final String IDEMPOTENCY_KEY = "sched-key-1";
    private static final String USD = "USD";
    private static final BigDecimal AMOUNT = new BigDecimal("25.50");
    // Fixed and years away from this machine's clock, so a check that read the server's date would show up.
    private static final LocalDate FINERACT_TODAY = LocalDate.of(2030, 1, 15);
    private static final LocalDate START = FINERACT_TODAY.plusDays(10);
    private static final LocalDate END = START.plusMonths(3);

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;
    @Mock
    private UserQueryService userQueryService;
    @Mock
    private BeneficiariesQueryService beneficiariesQueryService;
    @Mock
    private OtpService otpService;
    @Mock
    private StepUpTokenService stepUpTokenService;
    @Mock
    private StandingInstructionFineractClient standingInstructionFineractClient;
    @Mock
    private SavingsFineractClient savingsFineractClient;
    @Mock
    private TransferFineractClient transferFineractClient;
    @Mock
    private BusinessDateFineractClient businessDateFineractClient;
    @Mock
    private ScheduledTransferCommandRepository repository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final IdempotencyKeyHolder idempotencyKeyHolder = new IdempotencyKeyHolder();
    private final TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));

    private ScheduledTransfersCommandServiceImpl service;
    private ScheduledTransfer lastSaved;

    @BeforeEach
    void setUp() {
        service = service(true);
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID)).thenReturn(Optional.empty());
        when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.SAVINGS), eq(TO_SAVINGS_ID))).thenReturn(true);
        when(savingsFineractClient.getAccount(FROM_SAVINGS_ID)).thenReturn(account(CLIENT_ID, USD));
        when(savingsFineractClient.getAccount(TO_SAVINGS_ID)).thenReturn(account(DEST_CLIENT_ID, USD));
        when(transferFineractClient.getClientOfficeId(CLIENT_ID)).thenReturn(CALLER_OFFICE_ID);
        when(transferFineractClient.getClientOfficeId(DEST_CLIENT_ID)).thenReturn(DEST_OFFICE_ID);
        when(businessDateFineractClient.today()).thenReturn(FINERACT_TODAY);
        when(stepUpTokenService.actionFingerprint(anyString(), any(Object[].class))).thenReturn(ACTION_FINGERPRINT);
        when(stepUpTokenService.issue(any(), any(), any(), any()))
                .thenReturn(IssuedJwt.builder().tokenValue(STEP_UP_TOKEN).expiresAt(Instant.EPOCH).build());
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(true);
        when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(ScheduledTransfer.class))).thenAnswer(invocation -> {
            lastSaved = invocation.getArgument(0);
            return lastSaved;
        });
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(lastSaved));
        when(standingInstructionFineractClient.createStandingInstruction(any())).thenReturn(STANDING_INSTRUCTION_ID);
    }

    @Test
    void disabledFeatureRejectsInitiateBeforeAnyCheck() {
        service = service(false);

        assertThatThrownBy(() -> service.initiate(jwt(), initiate(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferDisabledException.class)
                .hasFieldOrPropertyWithValue("code", ScheduledTransferDisabledException.CODE);
        verifyNoInteractions(accessPolicyEvaluator, otpService, standingInstructionFineractClient);
    }

    @Test
    void disabledFeatureRejectsConfirmBeforeAnyCheck() {
        service = service(false);

        assertThatThrownBy(() -> service.confirm(jwt(), confirm(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferDisabledException.class);
        verifyNoInteractions(stepUpTokenService, otpService, repository, standingInstructionFineractClient);
    }

    @Test
    void initiateAuthorizesTheSourceAccountFirst() {
        doThrow(new ScheduledTransferAccessDeniedException()).when(accessPolicyEvaluator)
                .authorize(any(), eq(ConsumerAction.SCHEDULED_TRANSFER_CREATE), eq(FROM_SAVINGS_ID), any());

        assertThatThrownBy(() -> service.initiate(jwt(), initiate(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferAccessDeniedException.class);
        verifyNoInteractions(otpService);
    }

    @Test
    void initiateSendsAScheduledTransferOtpAndBindsTheStepUpToEveryParameter() {
        ScheduledTransferChallengeCommandData challenge =
                service.initiate(jwt(), initiate(ScheduledTransferFrequency.MONTHLY));

        assertThat(challenge.getStepUpToken()).isEqualTo(STEP_UP_TOKEN);
        assertThat(challenge.getSentTo()).isNotEqualTo(EMAIL);
        verify(otpService).createOtp(eq(PUBLIC_ID), eq(OtpPurpose.SCHEDULED_TRANSFER), any());
        verify(stepUpTokenService).actionFingerprint(ScheduledTransferConstants.CREATE_ACTION_ID, FROM_SAVINGS_ID,
                TO_SAVINGS_ID, ScheduledTransferFrequency.MONTHLY.name(), AMOUNT, START.toString(), END.toString());
        verify(stepUpTokenService).issue(PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT, StepUpConstants.STEPUP_TTL);
    }

    @Test
    void initiateRejectsAStartDateOnFineractsToday() {
        InitiateScheduledTransferCommand today = initiateBuilder().startDate(FINERACT_TODAY).endDate(null).build();

        assertThatThrownBy(() -> service.initiate(jwt(), today)).isInstanceOf(ScheduledTransferInvalidException.class);
        verifyNoInteractions(otpService);
    }

    @Test
    void initiateAcceptsAStartDateTheDayAfterFineractsToday() {
        InitiateScheduledTransferCommand tomorrow =
                initiateBuilder().startDate(FINERACT_TODAY.plusDays(1)).endDate(null).build();

        service.initiate(jwt(), tomorrow);

        verify(otpService).createOtp(eq(PUBLIC_ID), eq(OtpPurpose.SCHEDULED_TRANSFER), any());
    }

    @Test
    void initiateRejectsTheBffsTomorrowOnceFineractHasPassedMidnight() {
        // Tenant on Asia/Kolkata, BFF on UTC, 20:00 UTC: Fineract is already on the BFF's tomorrow and today's job ran.
        LocalDate bffTomorrow = LocalDate.now(ZoneId.systemDefault()).plusDays(1);
        when(businessDateFineractClient.today()).thenReturn(bffTomorrow);
        InitiateScheduledTransferCommand command = initiateBuilder()
                .frequency(ScheduledTransferFrequency.WEEKLY)
                .startDate(bffTomorrow)
                .endDate(null)
                .build();

        assertThatThrownBy(() -> service.initiate(jwt(), command)).isInstanceOf(ScheduledTransferInvalidException.class);
    }

    @Test
    void initiateRejectsAMonthlyStartDateAfterThe28th() {
        InitiateScheduledTransferCommand onThe29th =
                initiateBuilder().startDate(LocalDate.of(2030, 1, 29)).endDate(null).build();
        InitiateScheduledTransferCommand onThe31st =
                initiateBuilder().startDate(LocalDate.of(2030, 1, 31)).endDate(null).build();

        assertThatThrownBy(() -> service.initiate(jwt(), onThe29th)).isInstanceOf(ScheduledTransferInvalidException.class);
        assertThatThrownBy(() -> service.initiate(jwt(), onThe31st)).isInstanceOf(ScheduledTransferInvalidException.class);
        verifyNoInteractions(otpService);
    }

    @Test
    void initiateAcceptsAMonthlyStartDateOnThe28th() {
        InitiateScheduledTransferCommand onThe28th =
                initiateBuilder().startDate(LocalDate.of(2030, 1, 28)).endDate(null).build();

        service.initiate(jwt(), onThe28th);

        verify(otpService).createOtp(eq(PUBLIC_ID), eq(OtpPurpose.SCHEDULED_TRANSFER), any());
    }

    @Test
    void initiateAcceptsAWeeklyStartDateAfterThe28th() {
        InitiateScheduledTransferCommand weekly = initiateBuilder()
                .frequency(ScheduledTransferFrequency.WEEKLY)
                .startDate(LocalDate.of(2030, 1, 31))
                .endDate(null)
                .build();

        service.initiate(jwt(), weekly);

        verify(otpService).createOtp(eq(PUBLIC_ID), eq(OtpPurpose.SCHEDULED_TRANSFER), any());
    }

    @Test
    void initiateFailsAsUpstreamUnavailableWhenFineractReportsNoDate() {
        when(businessDateFineractClient.today()).thenReturn(null);

        assertThatThrownBy(() -> service.initiate(jwt(), initiate(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferUpstreamUnavailableException.class);
        verifyNoInteractions(otpService);
    }

    @Test
    void initiateFailsAsUpstreamUnavailableWhenFineractsDateCannotBeRead() {
        when(businessDateFineractClient.today()).thenThrow(feignStatus(503));

        assertThatThrownBy(() -> service.initiate(jwt(), initiate(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferUpstreamUnavailableException.class);
        verifyNoInteractions(otpService);
    }

    @Test
    void confirmRechecksTheStartDateAgainstFineractsTodayBeforeReserving() {
        // Fineract's date moved past the start date between initiate and confirm.
        when(businessDateFineractClient.today()).thenReturn(START);

        assertThatThrownBy(() -> service.confirm(jwt(), confirm(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferInvalidException.class);
        verify(repository, never()).saveAndFlush(any());
        verifyNoInteractions(standingInstructionFineractClient);
    }

    @Test
    void initiateRejectsAnEndDateBeforeTheStartDate() {
        InitiateScheduledTransferCommand reversed = initiateBuilder().endDate(START.minusDays(1)).build();

        assertThatThrownBy(() -> service.initiate(jwt(), reversed)).isInstanceOf(ScheduledTransferInvalidException.class);
    }

    @Test
    void initiateRejectsTheSameSourceAndDestination() {
        InitiateScheduledTransferCommand sameAccount = initiateBuilder().toAccountId(FROM_SAVINGS_ID).build();

        assertThatThrownBy(() -> service.initiate(jwt(), sameAccount)).isInstanceOf(ScheduledTransferInvalidException.class);
    }

    @Test
    void initiateRejectsAnAmountAboveTheBeneficiaryLimit() {
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.of(beneficiary(new BigDecimal("25.49"))));

        assertThatThrownBy(() -> service.initiate(jwt(), initiate(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferBeneficiaryLimitExceededException.class);
        verifyNoInteractions(otpService);
    }

    @Test
    void initiateAcceptsAnAmountEqualToTheBeneficiaryLimitWithoutOwningTheAccount() {
        when(beneficiariesQueryService.findActiveByAccount(USER_ID, TO_SAVINGS_ID))
                .thenReturn(Optional.of(beneficiary(AMOUNT)));
        when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.SAVINGS), eq(TO_SAVINGS_ID))).thenReturn(false);

        service.initiate(jwt(), initiate(ScheduledTransferFrequency.MONTHLY));

        verify(otpService).createOtp(eq(PUBLIC_ID), eq(OtpPurpose.SCHEDULED_TRANSFER), any());
    }

    @Test
    void initiateRejectsADestinationThatIsNeitherOwnedNorABeneficiary() {
        when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.SAVINGS), eq(TO_SAVINGS_ID))).thenReturn(false);

        assertThatThrownBy(() -> service.initiate(jwt(), initiate(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferAccessDeniedException.class);
    }

    @Test
    void initiateRejectsAccountsInDifferentCurrencies() {
        when(savingsFineractClient.getAccount(TO_SAVINGS_ID)).thenReturn(account(DEST_CLIENT_ID, "EUR"));

        assertThatThrownBy(() -> service.initiate(jwt(), initiate(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferCurrencyMismatchException.class);
        verifyNoInteractions(otpService);
    }

    @Test
    void confirmRejectsAStepUpThatDoesNotMatchAndReservesNothing() {
        when(stepUpTokenService.verify(STEP_UP_TOKEN, PUBLIC_ID, DEVICE_FINGERPRINT, ACTION_FINGERPRINT)).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(jwt(), confirm(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferStepUpInvalidException.class);
        verifyNoInteractions(repository, standingInstructionFineractClient);
    }

    @Test
    void confirmCreatesAMonthlyInstructionWithAnInclusiveEndDate() {
        ScheduledTransferCommandData result = service.confirm(jwt(), confirm(ScheduledTransferFrequency.MONTHLY));

        StandingInstructionCreationRequest request = sentRequest();
        assertThat(request.getName()).isEqualTo(ScheduledTransferConstants.INSTRUCTION_NAME_PREFIX + result.getPublicId());
        assertThat(request.getFromClientId()).isEqualTo(String.valueOf(CLIENT_ID));
        assertThat(request.getFromOfficeId()).isEqualTo(String.valueOf(CALLER_OFFICE_ID));
        assertThat(request.getFromAccountId()).isEqualTo(String.valueOf(FROM_SAVINGS_ID));
        assertThat(request.getToClientId()).isEqualTo(String.valueOf(DEST_CLIENT_ID));
        assertThat(request.getToOfficeId()).isEqualTo(String.valueOf(DEST_OFFICE_ID));
        assertThat(request.getToAccountId()).isEqualTo(String.valueOf(TO_SAVINGS_ID));
        assertThat(request.getPriority()).isEqualTo(ScheduledTransferConstants.LOW_PRIORITY);
        assertThat(request.getAmount()).isEqualTo(AMOUNT.toPlainString());
        assertThat(request.getValidFrom()).isEqualTo(START.toString());
        assertThat(request.getValidTill()).isEqualTo(END.plusDays(1).toString());
        assertThat(request.getRecurrenceFrequency()).isEqualTo(ScheduledTransferConstants.MONTHS_FREQUENCY);
        assertThat(request.getRecurrenceInterval()).isEqualTo(ScheduledTransferConstants.EVERY_PERIOD);
        assertThat(request.getRecurrenceOnMonthDay()).startsWith(String.format("%02d ", START.getDayOfMonth()));
        assertThat(request.getMonthDayFormat()).isEqualTo(ScheduledTransferConstants.MONTH_DAY_FORMAT);
        assertThat(request.getAllowPartialTransfer()).isFalse();
    }

    @Test
    void confirmCreatesAWeeklyInstructionWithoutAMonthDayOrEndDate() {
        ConfirmScheduledTransferCommand weekly = confirmBuilder(ScheduledTransferFrequency.WEEKLY).endDate(null).build();

        service.confirm(jwt(), weekly);

        StandingInstructionCreationRequest request = sentRequest();
        assertThat(request.getRecurrenceFrequency()).isEqualTo(ScheduledTransferConstants.WEEKS_FREQUENCY);
        assertThat(request.getRecurrenceOnMonthDay()).isNull();
        assertThat(request.getMonthDayFormat()).isNull();
        assertThat(request.getValidTill()).isNull();
    }

    @Test
    void confirmReservesBeforeCallingFineractAndForwardsTheDerivedKey() {
        DerivedIdempotencyKey expectedKey = IdempotencyKeyDeriver.derive(PUBLIC_ID, IDEMPOTENCY_KEY);
        when(standingInstructionFineractClient.createStandingInstruction(any())).thenAnswer(invocation -> {
            assertThat(idempotencyKeyHolder.get()).isEqualTo(expectedKey);
            return STANDING_INSTRUCTION_ID;
        });

        service.confirm(jwt(), confirm(ScheduledTransferFrequency.MONTHLY));

        InOrder order = inOrder(repository, standingInstructionFineractClient);
        order.verify(repository).saveAndFlush(any(ScheduledTransfer.class));
        order.verify(standingInstructionFineractClient).createStandingInstruction(any());
        assertThat(idempotencyKeyHolder.get()).isNull();
    }

    @Test
    void confirmLinksTheInstructionAndAuditsTheCreation() {
        ScheduledTransfer reserved = reserved();
        when(repository.saveAndFlush(any(ScheduledTransfer.class))).thenReturn(reserved);
        when(repository.findById(reserved.getId())).thenReturn(Optional.of(reserved));

        ScheduledTransferCommandData result = service.confirm(jwt(), confirm(ScheduledTransferFrequency.MONTHLY));

        assertThat(result.getPublicId()).isEqualTo(reserved.getPublicId());
        assertThat(reserved.getFineractStandingInstructionId()).isEqualTo(STANDING_INSTRUCTION_ID);
        ArgumentCaptor<TransactionalAuditEvent> event = ArgumentCaptor.forClass(TransactionalAuditEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo(AuditEventType.SCHEDULED_TRANSFER_CREATED);
        assertThat(event.getValue().getDetails()).isEqualTo(Map.of(
                ScheduledTransfersCommandServiceImpl.AUDIT_DETAIL_PUBLIC_ID, reserved.getPublicId().toString()));
    }

    @Test
    void confirmRetriedWithTheSameKeyReturnsTheLinkedTransferWithoutCallingFineract() {
        ScheduledTransfer linked = reserved();
        linked.link(STANDING_INSTRUCTION_ID);
        when(repository.findByIdempotencyKey(IdempotencyKeyDeriver.derive(PUBLIC_ID, IDEMPOTENCY_KEY).getValue()))
                .thenReturn(Optional.of(linked));

        ScheduledTransferCommandData result = service.confirm(jwt(), confirm(ScheduledTransferFrequency.MONTHLY));

        assertThat(result.getPublicId()).isEqualTo(linked.getPublicId());
        verify(standingInstructionFineractClient, never()).createStandingInstruction(any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void confirmReleasesTheReservationWhenFineractRejectsTheInstruction() {
        ScheduledTransfer reserved = reserved();
        when(repository.saveAndFlush(any(ScheduledTransfer.class))).thenReturn(reserved);
        when(repository.findById(reserved.getId())).thenReturn(Optional.of(reserved));
        when(standingInstructionFineractClient.createStandingInstruction(any())).thenThrow(feignStatus(400));

        assertThatThrownBy(() -> service.confirm(jwt(), confirm(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferInvalidException.class);
        verify(repository).delete(reserved);
    }

    @Test
    void confirmReleasesTheReservationWhenTheDestinationIsNoLongerAllowed() {
        ScheduledTransfer reserved = reserved();
        when(repository.saveAndFlush(any(ScheduledTransfer.class))).thenReturn(reserved);
        when(repository.findById(reserved.getId())).thenReturn(Optional.of(reserved));
        when(accessPolicyEvaluator.ownsResource(any(), eq(ResourceType.SAVINGS), eq(TO_SAVINGS_ID))).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(jwt(), confirm(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferAccessDeniedException.class);
        verify(repository).delete(reserved);
        verify(standingInstructionFineractClient, never()).createStandingInstruction(any());
    }

    @Test
    void confirmKeepsTheReservationWhenTheOutcomeUpstreamIsUnknown() {
        ScheduledTransfer reserved = reserved();
        when(repository.saveAndFlush(any(ScheduledTransfer.class))).thenReturn(reserved);
        when(standingInstructionFineractClient.createStandingInstruction(any())).thenThrow(feignStatus(503));

        assertThatThrownBy(() -> service.confirm(jwt(), confirm(ScheduledTransferFrequency.MONTHLY)))
                .isInstanceOf(ScheduledTransferUpstreamUnavailableException.class)
                .isInstanceOf(AbstractConsumerException.class);
        verify(repository, never()).delete(any());
        assertThat(idempotencyKeyHolder.get()).isNull();
    }

    private ScheduledTransfersCommandServiceImpl service(boolean enabled) {
        return new ScheduledTransfersCommandServiceImpl(accessPolicyEvaluator, userQueryService,
                beneficiariesQueryService, otpService, stepUpTokenService, standingInstructionFineractClient,
                savingsFineractClient, transferFineractClient, businessDateFineractClient, idempotencyKeyHolder,
                repository, transactionTemplate, eventPublisher, enabled);
    }

    private StandingInstructionCreationRequest sentRequest() {
        ArgumentCaptor<StandingInstructionCreationRequest> request =
                ArgumentCaptor.forClass(StandingInstructionCreationRequest.class);
        verify(standingInstructionFineractClient).createStandingInstruction(request.capture());
        return request.getValue();
    }

    private static ScheduledTransfer reserved() {
        return ScheduledTransfer.reserve(UUID.fromString("3f2c8a1e-0000-4000-8000-0000000000aa"), USER_ID,
                IdempotencyKeyDeriver.derive(PUBLIC_ID, IDEMPOTENCY_KEY).getValue(), FROM_SAVINGS_ID, TO_SAVINGS_ID);
    }

    private static FeignException feignStatus(int status) {
        Request request = Request.create(Request.HttpMethod.POST, "http://fineract/standinginstructions", Map.of(),
                new byte[0], StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("createStandingInstruction",
                feign.Response.builder().status(status).reason("x").request(request).headers(Map.of()).build());
    }

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
                .name("Landlord")
                .fineractAccountId(TO_SAVINGS_ID)
                .transferLimit(transferLimit)
                .build();
    }

    private static SavingsAccountData account(Long clientId, String currencyCode) {
        return new SavingsAccountData().clientId(clientId).currency(new CurrencyData().code(currencyCode));
    }

    private static InitiateScheduledTransferCommand initiate(ScheduledTransferFrequency frequency) {
        return initiateBuilder().frequency(frequency).build();
    }

    private static InitiateScheduledTransferCommand.InitiateScheduledTransferCommandBuilder initiateBuilder() {
        return InitiateScheduledTransferCommand.builder()
                .fromAccountId(FROM_SAVINGS_ID)
                .toAccountId(TO_SAVINGS_ID)
                .amount(AMOUNT)
                .frequency(ScheduledTransferFrequency.MONTHLY)
                .startDate(START)
                .endDate(END)
                .deviceFingerprint(DEVICE_FINGERPRINT);
    }

    private static ConfirmScheduledTransferCommand confirm(ScheduledTransferFrequency frequency) {
        return confirmBuilder(frequency).build();
    }

    private static ConfirmScheduledTransferCommand.ConfirmScheduledTransferCommandBuilder confirmBuilder(
            ScheduledTransferFrequency frequency) {
        return ConfirmScheduledTransferCommand.builder()
                .stepUpToken(STEP_UP_TOKEN)
                .otp(OTP)
                .fromAccountId(FROM_SAVINGS_ID)
                .toAccountId(TO_SAVINGS_ID)
                .amount(AMOUNT)
                .frequency(frequency)
                .startDate(START)
                .endDate(END)
                .deviceFingerprint(DEVICE_FINGERPRINT)
                .idempotencyKey(IDEMPOTENCY_KEY);
    }
}
