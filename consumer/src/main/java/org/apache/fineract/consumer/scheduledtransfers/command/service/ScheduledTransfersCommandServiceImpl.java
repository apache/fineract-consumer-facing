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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.fineract.consumer.beneficiaries.query.data.BeneficiaryQueryData;
import org.apache.fineract.consumer.beneficiaries.query.service.BeneficiariesQueryService;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.data.ResourceType;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.audit.data.TransactionalAuditEvent;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.StandingInstructionCreationRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.BusinessDateFineractClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.FineractCaller;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.SavingsFineractClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.TransferFineractClient;
import org.apache.fineract.consumer.infrastructure.idempotency.data.DerivedIdempotencyKey;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyDeriver;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyHolder;
import org.apache.fineract.consumer.infrastructure.jwt.data.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpConstants;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpDestination;
import org.apache.fineract.consumer.infrastructure.otp.data.OtpPurpose;
import org.apache.fineract.consumer.infrastructure.otp.service.OtpService;
import org.apache.fineract.consumer.infrastructure.stepup.data.StepUpConstants;
import org.apache.fineract.consumer.infrastructure.stepup.service.StepUpTokenService;
import org.apache.fineract.consumer.infrastructure.web.service.EmailMaskingService;
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
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferInProgressException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferInvalidException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferNotFoundException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferStepUpInvalidException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferUpstreamUnavailableException;
import org.apache.fineract.consumer.scheduledtransfers.command.repository.ScheduledTransferCommandRepository;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ScheduledTransfersCommandServiceImpl implements ScheduledTransfersCommandService {

    // Checked per call rather than with @ConditionalOnProperty: AOT evaluates bean conditions at build time, so a
    // native image would ignore the runtime setting.
    private static final String ENABLED_PROPERTY = "${consumer.scheduled-transfers.enabled:false}";
    private static final DateTimeFormatter MONTH_DAY =
            DateTimeFormatter.ofPattern(ScheduledTransferConstants.MONTH_DAY_FORMAT, Locale.ENGLISH);
    static final String AUDIT_DETAIL_PUBLIC_ID = "scheduledTransferPublicId";
    /**
     * How long a confirm holds an unlinked row. Well over one confirm's Fineract calls at Feign's default timeouts, so
     * a claim only lapses when the confirm holding it died.
     */
    static final Duration CLAIM_TTL = Duration.ofMinutes(5);

    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final UserQueryService userQueryService;
    private final BeneficiariesQueryService beneficiariesQueryService;
    private final OtpService otpService;
    private final StepUpTokenService stepUpTokenService;
    private final StandingInstructionFineractClient standingInstructionFineractClient;
    private final SavingsFineractClient savingsFineractClient;
    private final TransferFineractClient transferFineractClient;
    private final BusinessDateFineractClient businessDateFineractClient;
    private final IdempotencyKeyHolder idempotencyKeyHolder;
    private final ScheduledTransferCommandRepository scheduledTransferCommandRepository;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean enabled;

    public ScheduledTransfersCommandServiceImpl(AccessPolicyEvaluator accessPolicyEvaluator,
            UserQueryService userQueryService, BeneficiariesQueryService beneficiariesQueryService, OtpService otpService,
            StepUpTokenService stepUpTokenService, StandingInstructionFineractClient standingInstructionFineractClient,
            SavingsFineractClient savingsFineractClient, TransferFineractClient transferFineractClient,
            BusinessDateFineractClient businessDateFineractClient, IdempotencyKeyHolder idempotencyKeyHolder,
            ScheduledTransferCommandRepository scheduledTransferCommandRepository,
            TransactionTemplate transactionTemplate, ApplicationEventPublisher eventPublisher,
            @Value(ENABLED_PROPERTY) boolean enabled) {
        this.accessPolicyEvaluator = accessPolicyEvaluator;
        this.userQueryService = userQueryService;
        this.beneficiariesQueryService = beneficiariesQueryService;
        this.otpService = otpService;
        this.stepUpTokenService = stepUpTokenService;
        this.standingInstructionFineractClient = standingInstructionFineractClient;
        this.savingsFineractClient = savingsFineractClient;
        this.transferFineractClient = transferFineractClient;
        this.businessDateFineractClient = businessDateFineractClient;
        this.idempotencyKeyHolder = idempotencyKeyHolder;
        this.scheduledTransferCommandRepository = scheduledTransferCommandRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.enabled = enabled;
    }

    @Override
    public ScheduledTransferChallengeCommandData initiate(Jwt jwt, InitiateScheduledTransferCommand command) {
        requireEnabled();
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SCHEDULED_TRANSFER_CREATE, command.getFromAccountId(),
                ScheduledTransferAccessDeniedException::new);
        requireValidSchedule(command.getFromAccountId(), command.getToAccountId(), command.getFrequency(),
                command.getStartDate(), command.getEndDate());

        UUID publicId = publicId(jwt);
        UserQueryData user = userQueryService.findByPublicId(publicId);
        requireDestinationAllowed(jwt, user.getId(), command.getToAccountId(), command.getAmount());
        resolveDestinationClientId(command.getFromAccountId(), command.getToAccountId());

        otpService.createOtp(publicId, OtpPurpose.SCHEDULED_TRANSFER, OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(user.getEmail())
                .build());
        String actionFingerprint = createActionFingerprint(command.getFromAccountId(), command.getToAccountId(),
                command.getFrequency(), command.getAmount(), command.getStartDate(), command.getEndDate());
        IssuedJwt issued = stepUpTokenService.issue(
                publicId, command.getDeviceFingerprint(), actionFingerprint, StepUpConstants.STEPUP_TTL);

        return ScheduledTransferChallengeCommandData.builder()
                .stepUpToken(issued.getTokenValue())
                .expiresAt(issued.getExpiresAt())
                .sentTo(EmailMaskingService.mask(user.getEmail()))
                .build();
    }

    @Override
    public ScheduledTransferCommandData confirm(Jwt jwt, ConfirmScheduledTransferCommand command) {
        requireEnabled();
        UUID publicId = publicId(jwt);
        String actionFingerprint = createActionFingerprint(command.getFromAccountId(), command.getToAccountId(),
                command.getFrequency(), command.getAmount(), command.getStartDate(), command.getEndDate());
        if (!stepUpTokenService.verify(
                command.getStepUpToken(), publicId, command.getDeviceFingerprint(), actionFingerprint)) {
            throw new ScheduledTransferStepUpInvalidException();
        }
        otpService.validateOtp(publicId, OtpPurpose.SCHEDULED_TRANSFER, command.getOtp(),
                ScheduledTransferStepUpInvalidException::new);
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SCHEDULED_TRANSFER_CREATE, command.getFromAccountId(),
                ScheduledTransferAccessDeniedException::new);
        requireValidSchedule(command.getFromAccountId(), command.getToAccountId(), command.getFrequency(),
                command.getStartDate(), command.getEndDate());

        UserQueryData user = userQueryService.findByPublicId(publicId);
        DerivedIdempotencyKey key = IdempotencyKeyDeriver.derive(publicId, command.getIdempotencyKey());
        ScheduledTransfer reserved = reserve(user.getId(), key, command.getFromAccountId(), command.getToAccountId());
        if (reserved.isLinked()) {
            return toCommandData(reserved);
        }

        Long standingInstructionId;
        try {
            // Re-checked after the reservation, so a beneficiary removed meanwhile is either seen here or sees the
            // reserved row.
            requireDestinationAllowed(jwt, user.getId(), command.getToAccountId(), command.getAmount());
            Long destinationClientId =
                    resolveDestinationClientId(command.getFromAccountId(), command.getToAccountId());
            standingInstructionId =
                    createStandingInstruction(reserved.getPublicId(), user, destinationClientId, command, key);
        } catch (ScheduledTransferUpstreamUnavailableException | ScheduledTransferInProgressException e) {
            // Fineract may have created the instruction: keep the reservation so a retry with the same key links it,
            // and give up the claim so that retry need not wait for it to lapse.
            releaseClaim(reserved.getId());
            throw e;
        } catch (RuntimeException e) {
            release(reserved.getId());
            throw e;
        }
        ScheduledTransfer linked = link(reserved.getId(), standingInstructionId, command.getDeviceFingerprint());
        return toCommandData(linked);
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new ScheduledTransferDisabledException();
        }
    }

    /**
     * Fineract accepts any start date, but its job runs an instruction only on a date that matches the schedule
     * exactly and never catches up a missed one. So the start date is checked against Fineract's own today rather
     * than this server's, and a monthly start date must fall on a day every month has: Fineract skips a month without
     * that day instead of running on its last day.
     */
    private void requireValidSchedule(Long fromAccountId, Long toAccountId, ScheduledTransferFrequency frequency,
            LocalDate startDate, LocalDate endDate) {
        if (fromAccountId.equals(toAccountId)
                || (endDate != null && endDate.isBefore(startDate))
                || (frequency == ScheduledTransferFrequency.MONTHLY
                        && startDate.getDayOfMonth() > ScheduledTransferConstants.LAST_MONTHLY_START_DAY)) {
            throw new ScheduledTransferInvalidException();
        }
        LocalDate fineractToday = call(businessDateFineractClient::today);
        if (fineractToday == null) {
            throw new ScheduledTransferUpstreamUnavailableException(
                    new IllegalStateException("Fineract returned no current date"));
        }
        if (!startDate.isAfter(fineractToday)) {
            throw new ScheduledTransferInvalidException();
        }
    }

    private void requireDestinationAllowed(Jwt jwt, Long userId, Long toAccountId, BigDecimal amount) {
        Optional<BeneficiaryQueryData> beneficiary = beneficiariesQueryService.findActiveByAccount(userId, toAccountId);
        if (beneficiary.isPresent()) {
            BigDecimal limit = beneficiary.get().getTransferLimit();
            if (limit != null && amount.compareTo(limit) > 0) {
                throw new ScheduledTransferBeneficiaryLimitExceededException();
            }
            return;
        }
        if (!accessPolicyEvaluator.ownsResource(jwt, ResourceType.SAVINGS, toAccountId)) {
            throw new ScheduledTransferAccessDeniedException();
        }
    }

    /** Fineract does not check currencies when it accepts an instruction; every run would fail instead. */
    private Long resolveDestinationClientId(Long fromAccountId, Long toAccountId) {
        SavingsAccountData source = call(() -> savingsFineractClient.getAccount(fromAccountId));
        SavingsAccountData destination = call(() -> savingsFineractClient.getAccount(toAccountId));
        String sourceCurrency = currencyCode(source);
        if (sourceCurrency == null || !sourceCurrency.equals(currencyCode(destination))) {
            throw new ScheduledTransferCurrencyMismatchException();
        }
        if (destination.getClientId() == null) {
            throw new ScheduledTransferNotFoundException();
        }
        return destination.getClientId();
    }

    private Long createStandingInstruction(UUID scheduledTransferPublicId, UserQueryData user, Long destinationClientId,
            ConfirmScheduledTransferCommand command, DerivedIdempotencyKey key) {
        Long callerClientId = user.getFineractClientId();
        Long callerOfficeId = call(() -> transferFineractClient.getClientOfficeId(callerClientId));
        Long destinationOfficeId = call(() -> transferFineractClient.getClientOfficeId(destinationClientId));
        boolean monthly = command.getFrequency() == ScheduledTransferFrequency.MONTHLY;

        StandingInstructionCreationRequest request = new StandingInstructionCreationRequest()
                .name(ScheduledTransferConstants.INSTRUCTION_NAME_PREFIX + scheduledTransferPublicId)
                .fromOfficeId(String.valueOf(callerOfficeId))
                .fromClientId(String.valueOf(callerClientId))
                .fromAccountType(ScheduledTransferConstants.SAVINGS_ACCOUNT_TYPE)
                .fromAccountId(String.valueOf(command.getFromAccountId()))
                .toOfficeId(String.valueOf(destinationOfficeId))
                .toClientId(String.valueOf(destinationClientId))
                .toAccountType(ScheduledTransferConstants.SAVINGS_ACCOUNT_TYPE)
                .toAccountId(String.valueOf(command.getToAccountId()))
                .transferType(ScheduledTransferConstants.ACCOUNT_TRANSFER_TYPE)
                .priority(ScheduledTransferConstants.LOW_PRIORITY)
                .status(ScheduledTransferConstants.ACTIVE_STATUS)
                .instructionType(ScheduledTransferConstants.FIXED_INSTRUCTION_TYPE)
                .amount(command.getAmount().toPlainString())
                .validFrom(command.getStartDate().toString())
                // Fineract stops before validTill; the consumer's end date is the last day a run may happen.
                .validTill(command.getEndDate() == null ? null : command.getEndDate().plusDays(1).toString())
                .recurrenceType(ScheduledTransferConstants.PERIODIC_RECURRENCE_TYPE)
                .recurrenceFrequency(monthly
                        ? ScheduledTransferConstants.MONTHS_FREQUENCY
                        : ScheduledTransferConstants.WEEKS_FREQUENCY)
                .recurrenceInterval(ScheduledTransferConstants.EVERY_PERIOD)
                .recurrenceOnMonthDay(monthly ? MONTH_DAY.format(command.getStartDate()) : null)
                .monthDayFormat(monthly ? ScheduledTransferConstants.MONTH_DAY_FORMAT : null)
                .allowPartialTransfer(false)
                .locale(ScheduledTransferConstants.LOCALE)
                .dateFormat(ScheduledTransferConstants.DATE_FORMAT);

        idempotencyKeyHolder.set(key);
        try {
            Long standingInstructionId =
                    callWrite(() -> standingInstructionFineractClient.createStandingInstruction(request));
            if (standingInstructionId == null) {
                throw new ScheduledTransferUpstreamUnavailableException(
                        new IllegalStateException("Fineract returned no standing instruction id"));
            }
            return standingInstructionId;
        } finally {
            idempotencyKeyHolder.clear();
        }
    }

    /**
     * Returns the row for this key, either already linked or claimed by this confirm. A row another confirm with the
     * same key is still working on is not shared: that confirm may yet release it, and an instruction created here
     * would then have no row. The caller gets HTTP 425 and retries.
     */
    private ScheduledTransfer reserve(Long userId, DerivedIdempotencyKey key, Long fromAccountId, Long toAccountId) {
        Optional<ScheduledTransfer> existing = transactionTemplate.execute(
                status -> scheduledTransferCommandRepository.findByIdempotencyKey(key.getValue()));
        if (existing != null && existing.isPresent()) {
            return claimExisting(existing.get());
        }
        try {
            return transactionTemplate.execute(status -> scheduledTransferCommandRepository.saveAndFlush(
                    ScheduledTransfer.reserve(UUID.randomUUID(), userId, key.getValue(), fromAccountId, toAccountId,
                            Instant.now().plus(CLAIM_TTL))));
        } catch (DataIntegrityViolationException e) {
            // A concurrent confirm with the same key reserved first.
            ScheduledTransfer reservedFirst = transactionTemplate.execute(status -> scheduledTransferCommandRepository
                    .findByIdempotencyKey(key.getValue())
                    .orElseThrow(() -> e));
            return claimExisting(reservedFirst);
        }
    }

    private ScheduledTransfer claimExisting(ScheduledTransfer transfer) {
        if (transfer.isLinked()) {
            return transfer;
        }
        Instant now = Instant.now();
        Integer claimed = transactionTemplate.execute(
                status -> scheduledTransferCommandRepository.claim(transfer.getId(), now.plus(CLAIM_TTL), now));
        if (claimed == null || claimed == 0) {
            throw new ScheduledTransferInProgressException(
                    new IllegalStateException("another confirm with the same key is in progress"));
        }
        return transfer;
    }

    private void releaseClaim(Long scheduledTransferId) {
        transactionTemplate.executeWithoutResult(
                status -> scheduledTransferCommandRepository.releaseClaim(scheduledTransferId));
    }

    /** Deletes the row after a definite failure. Only the confirm holding the claim gets here, so no other uses it. */
    private void release(Long scheduledTransferId) {
        transactionTemplate.executeWithoutResult(status -> scheduledTransferCommandRepository
                .findById(scheduledTransferId)
                .filter(transfer -> !transfer.isLinked())
                .ifPresent(scheduledTransferCommandRepository::delete));
    }

    private ScheduledTransfer link(Long scheduledTransferId, Long standingInstructionId, String deviceFingerprint) {
        return transactionTemplate.execute(status -> {
            ScheduledTransfer transfer = scheduledTransferCommandRepository.findById(scheduledTransferId)
                    .orElseThrow(() -> new IllegalStateException("reserved scheduled transfer disappeared"));
            transfer.link(standingInstructionId);
            scheduledTransferCommandRepository.save(transfer);
            eventPublisher.publishEvent(TransactionalAuditEvent.of(AuditEventType.SCHEDULED_TRANSFER_CREATED,
                    transfer.getUserId(), false, deviceFingerprint,
                    Map.of(AUDIT_DETAIL_PUBLIC_ID, transfer.getPublicId().toString())));
            return transfer;
        });
    }

    private String createActionFingerprint(Long fromAccountId, Long toAccountId, ScheduledTransferFrequency frequency,
            BigDecimal amount, LocalDate startDate, LocalDate endDate) {
        return stepUpTokenService.actionFingerprint(ScheduledTransferConstants.CREATE_ACTION_ID,
                fromAccountId, toAccountId, frequency.name(), amount, startDate.toString(),
                endDate == null ? "" : endDate.toString());
    }

    private static ScheduledTransferCommandData toCommandData(ScheduledTransfer transfer) {
        return ScheduledTransferCommandData.builder().publicId(transfer.getPublicId()).build();
    }

    private static String currencyCode(SavingsAccountData account) {
        return account == null || account.getCurrency() == null ? null : account.getCurrency().getCode();
    }

    private static UUID publicId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private <T> T call(Supplier<T> upstream) {
        return FineractCaller.call(upstream,
                e -> new ScheduledTransferNotFoundException(),
                ScheduledTransferInvalidException::new,
                ScheduledTransferUpstreamUnavailableException::new);
    }

    private <T> T callWrite(Supplier<T> upstream) {
        return FineractCaller.call(upstream,
                e -> new ScheduledTransferNotFoundException(),
                ScheduledTransferInvalidException::new,
                ScheduledTransferInProgressException::new,
                ScheduledTransferUpstreamUnavailableException::new);
    }
}
