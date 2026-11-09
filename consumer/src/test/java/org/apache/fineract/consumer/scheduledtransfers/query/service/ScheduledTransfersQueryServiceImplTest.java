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
package org.apache.fineract.consumer.scheduledtransfers.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import feign.FeignException;
import feign.Request;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetFromAccountStandingInstructionSwagger;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetPageItemsStandingInstructionSwagger;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetRecurrenceFrequencyStandingInstructionSwagger;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetStandingInstructionHistoryPageItemsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetStatusStandingInstructionSwagger;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClient;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferFrequency;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferQueryConstants;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferQueryData;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunHistoryQuery;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunQueryData;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunQueryResponse;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunStatus;
import org.apache.fineract.consumer.scheduledtransfers.query.domain.ScheduledTransferQueryEntity;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryAccessDeniedException;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryDisabledException;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryInvalidException;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryNotFoundException;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryUpstreamUnavailableException;
import org.apache.fineract.consumer.scheduledtransfers.query.repository.ScheduledTransferQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduledTransfersQueryServiceImplTest {

    private static final UUID SUBJECT = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final UUID PUBLIC_ID = UUID.fromString("7a1b2c3d-0000-4000-8000-000000000001");
    private static final UUID OTHER_PUBLIC_ID = UUID.fromString("7a1b2c3d-0000-4000-8000-000000000002");
    private static final Long USER_ID = 1L;
    private static final Long CLIENT_ID = 42L;
    private static final Long FROM_SAVINGS_ID = 7L;
    private static final Long OTHER_SAVINGS_ID = 9L;
    private static final Long TO_SAVINGS_ID = 8L;
    private static final Long INSTRUCTION_ID = 555L;
    private static final Long OTHER_INSTRUCTION_ID = 777L;
    private static final Long BANK_INSTRUCTION_ID = 900L;
    private static final int DELETED_STATUS_ID = 3;
    private static final int DISABLED_STATUS_ID = 2;
    private static final BigDecimal AMOUNT = new BigDecimal("25.50");
    private static final LocalDate TODAY = LocalDate.now(ZoneId.systemDefault());
    private static final LocalDate START = TODAY.plusDays(10);
    private static final LocalDate END = START.plusMonths(3);

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;
    @Mock
    private UserClientResolver userClientResolver;
    @Mock
    private StandingInstructionFineractClient standingInstructionFineractClient;
    @Mock
    private ScheduledTransferQueryRepository repository;

    private ScheduledTransfersQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = service(true);
        when(userClientResolver.resolveUserId(any())).thenReturn(USER_ID);
        when(userClientResolver.resolveClientId(any())).thenReturn(CLIENT_ID);
        when(repository.findByPublicIdAndUserId(PUBLIC_ID, USER_ID))
                .thenReturn(Optional.of(row(PUBLIC_ID, FROM_SAVINGS_ID, INSTRUCTION_ID)));
    }

    @Test
    void disabledFeatureRejectsTheListBeforeAnyCheck() {
        service = service(false);

        assertThatThrownBy(() -> service.listScheduledTransfers(jwt()))
                .isInstanceOf(ScheduledTransferQueryDisabledException.class)
                .hasFieldOrPropertyWithValue("code", ScheduledTransferQueryDisabledException.CODE);
        verifyNoInteractions(accessPolicyEvaluator, repository, standingInstructionFineractClient);
    }

    @Test
    void disabledFeatureRejectsRunHistoryBeforeAnyCheck() {
        service = service(false);

        assertThatThrownBy(() -> service.listRuns(jwt(), runs(0, 20)))
                .isInstanceOf(ScheduledTransferQueryDisabledException.class);
        verifyNoInteractions(accessPolicyEvaluator, repository, standingInstructionFineractClient);
    }

    @Test
    void listAuthorizesTheListActionBeforeReadingAnything() {
        doThrow(new ScheduledTransferQueryAccessDeniedException()).when(accessPolicyEvaluator)
                .authorize(any(), eq(ConsumerAction.SCHEDULED_TRANSFER_LIST));

        assertThatThrownBy(() -> service.listScheduledTransfers(jwt()))
                .isInstanceOf(ScheduledTransferQueryAccessDeniedException.class);
        verifyNoInteractions(repository, standingInstructionFineractClient);
    }

    @Test
    void listWithNoScheduledTransfersAsksFineractNothing() {
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of());

        assertThat(service.listScheduledTransfers(jwt())).isEmpty();
        verifyNoInteractions(standingInstructionFineractClient);
    }

    @Test
    void listAuthorizesSavingsViewOnEverySourceAccountOnce() {
        UUID third = UUID.randomUUID();
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(
                row(PUBLIC_ID, FROM_SAVINGS_ID, INSTRUCTION_ID),
                row(OTHER_PUBLIC_ID, OTHER_SAVINGS_ID, OTHER_INSTRUCTION_ID),
                row(third, FROM_SAVINGS_ID, BANK_INSTRUCTION_ID)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID)).thenReturn(List.of());

        service.listScheduledTransfers(jwt());

        verify(accessPolicyEvaluator, times(1))
                .authorize(any(), eq(ConsumerAction.SAVINGS_VIEW), eq(FROM_SAVINGS_ID), any());
        verify(accessPolicyEvaluator, times(1))
                .authorize(any(), eq(ConsumerAction.SAVINGS_VIEW), eq(OTHER_SAVINGS_ID), any());
    }

    @Test
    void listIsDeniedWhenASourceAccountIsNotTheCallers() {
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(row(PUBLIC_ID, FROM_SAVINGS_ID, INSTRUCTION_ID)));
        doThrow(new ScheduledTransferQueryAccessDeniedException()).when(accessPolicyEvaluator)
                .authorize(any(), eq(ConsumerAction.SAVINGS_VIEW), eq(FROM_SAVINGS_ID), any());

        assertThatThrownBy(() -> service.listScheduledTransfers(jwt()))
                .isInstanceOf(ScheduledTransferQueryAccessDeniedException.class)
                .hasFieldOrPropertyWithValue("code", ScheduledTransferQueryAccessDeniedException.CODE);
        verifyNoInteractions(standingInstructionFineractClient);
    }

    @Test
    void listMapsAWeeklyTransferWithItsInclusiveEndDate() {
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(row(PUBLIC_ID, FROM_SAVINGS_ID, INSTRUCTION_ID)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID)).thenReturn(List.of(
                weekly(INSTRUCTION_ID, PUBLIC_ID).validTill(END.plusDays(1))));

        assertThat(service.listScheduledTransfers(jwt())).containsExactly(ScheduledTransferQueryData.builder()
                .publicId(PUBLIC_ID)
                .fromAccountId(FROM_SAVINGS_ID)
                .toAccountId(TO_SAVINGS_ID)
                .amount(AMOUNT)
                .frequency(ScheduledTransferFrequency.WEEKLY)
                .startDate(START)
                .endDate(END)
                .build());
    }

    @Test
    void listMapsAMonthlyTransferWithNoEndDate() {
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(row(PUBLIC_ID, FROM_SAVINGS_ID, INSTRUCTION_ID)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID)).thenReturn(List.of(
                instruction(INSTRUCTION_ID, PUBLIC_ID, FROM_SAVINGS_ID,
                        ScheduledTransferQueryConstants.MONTHS_FREQUENCY_ID)));

        List<ScheduledTransferQueryData> listed = service.listScheduledTransfers(jwt());

        assertThat(listed).singleElement().satisfies(transfer -> {
            assertThat(transfer.getFrequency()).isEqualTo(ScheduledTransferFrequency.MONTHLY);
            assertThat(transfer.getEndDate()).isNull();
        });
    }

    @Test
    void listLeavesOutInstructionsThatAreNoLongerActive() {
        UUID disabled = UUID.randomUUID();
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(
                row(PUBLIC_ID, FROM_SAVINGS_ID, INSTRUCTION_ID),
                row(disabled, FROM_SAVINGS_ID, OTHER_INSTRUCTION_ID)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID)).thenReturn(List.of(
                weekly(INSTRUCTION_ID, PUBLIC_ID)
                        .status(new GetStatusStandingInstructionSwagger().id(DELETED_STATUS_ID)),
                weekly(OTHER_INSTRUCTION_ID, disabled)
                        .status(new GetStatusStandingInstructionSwagger().id(DISABLED_STATUS_ID))));

        assertThat(service.listScheduledTransfers(jwt())).isEmpty();
    }

    @Test
    void listKeepsATransferUntilItsLastRunDayHasPassed() {
        UUID endsToday = UUID.randomUUID();
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(
                row(PUBLIC_ID, FROM_SAVINGS_ID, INSTRUCTION_ID),
                row(endsToday, FROM_SAVINGS_ID, OTHER_INSTRUCTION_ID)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID)).thenReturn(List.of(
                weekly(INSTRUCTION_ID, PUBLIC_ID).validTill(TODAY),
                weekly(OTHER_INSTRUCTION_ID, endsToday).validTill(TODAY.plusDays(1))));

        assertThat(service.listScheduledTransfers(jwt()))
                .extracting(ScheduledTransferQueryData::getPublicId)
                .containsExactly(endsToday);
    }

    @Test
    void listLeavesOutStandingInstructionsTheBankSetUp() {
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(row(PUBLIC_ID, FROM_SAVINGS_ID, INSTRUCTION_ID)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID)).thenReturn(List.of(
                weekly(BANK_INSTRUCTION_ID, UUID.randomUUID()).name("Loan repayment"),
                weekly(INSTRUCTION_ID, PUBLIC_ID)));

        assertThat(service.listScheduledTransfers(jwt()))
                .extracting(ScheduledTransferQueryData::getPublicId)
                .containsExactly(PUBLIC_ID);
    }

    @Test
    void listKeepsTheNewestFirstOrderOfTheRows() {
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(
                row(OTHER_PUBLIC_ID, FROM_SAVINGS_ID, OTHER_INSTRUCTION_ID),
                row(PUBLIC_ID, FROM_SAVINGS_ID, INSTRUCTION_ID)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID)).thenReturn(List.of(
                weekly(INSTRUCTION_ID, PUBLIC_ID), weekly(OTHER_INSTRUCTION_ID, OTHER_PUBLIC_ID)));

        assertThat(service.listScheduledTransfers(jwt()))
                .extracting(ScheduledTransferQueryData::getPublicId)
                .containsExactly(OTHER_PUBLIC_ID, PUBLIC_ID);
    }

    @Test
    void listFindsTheInstructionOfAnUnlinkedRowByItsName() {
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(row(PUBLIC_ID, FROM_SAVINGS_ID, null)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID))
                .thenReturn(List.of(weekly(INSTRUCTION_ID, PUBLIC_ID)));

        assertThat(service.listScheduledTransfers(jwt()))
                .extracting(ScheduledTransferQueryData::getPublicId)
                .containsExactly(PUBLIC_ID);
    }

    @Test
    void listIgnoresAnInstructionWithTheRowsNameOnAnotherSourceAccount() {
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(row(PUBLIC_ID, FROM_SAVINGS_ID, null)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID)).thenReturn(List.of(
                instruction(INSTRUCTION_ID, PUBLIC_ID, OTHER_SAVINGS_ID,
                        ScheduledTransferQueryConstants.WEEKS_FREQUENCY_ID)));

        assertThat(service.listScheduledTransfers(jwt())).isEmpty();
    }

    @Test
    void listReportsUpstreamUnavailableWhenFineractFails() {
        when(repository.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID))
                .thenReturn(List.of(row(PUBLIC_ID, FROM_SAVINGS_ID, INSTRUCTION_ID)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID)).thenThrow(feignStatus(503));

        assertThatThrownBy(() -> service.listScheduledTransfers(jwt()))
                .isInstanceOf(ScheduledTransferQueryUpstreamUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", ScheduledTransferQueryUpstreamUnavailableException.CODE);
    }

    @Test
    void runHistoryRejectsAnInvalidPageBeforeAnyCheck() {
        for (ScheduledTransferRunHistoryQuery query : List.of(runs(-1, 20), runs(0, 0),
                runs(0, ScheduledTransferQueryConstants.MAX_PAGE_SIZE + 1))) {
            assertThatThrownBy(() -> service.listRuns(jwt(), query))
                    .isInstanceOf(ScheduledTransferQueryInvalidException.class)
                    .hasFieldOrPropertyWithValue("code", ScheduledTransferQueryInvalidException.CODE);
        }
        verifyNoInteractions(accessPolicyEvaluator, repository, standingInstructionFineractClient);
    }

    @Test
    void runHistoryOfAScheduledTransferTheCallerDoesNotOwnIsNotFound() {
        when(repository.findByPublicIdAndUserId(OTHER_PUBLIC_ID, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listRuns(jwt(), runs(OTHER_PUBLIC_ID, 0, 20)))
                .isInstanceOf(ScheduledTransferQueryNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", ScheduledTransferQueryNotFoundException.CODE);
        verifyNoInteractions(standingInstructionFineractClient);
    }

    @Test
    void runHistoryAuthorizesSavingsViewOnTheSourceAccount() {
        doThrow(new ScheduledTransferQueryAccessDeniedException()).when(accessPolicyEvaluator)
                .authorize(any(), eq(ConsumerAction.SAVINGS_VIEW), eq(FROM_SAVINGS_ID), any());

        assertThatThrownBy(() -> service.listRuns(jwt(), runs(0, 20)))
                .isInstanceOf(ScheduledTransferQueryAccessDeniedException.class);
        verify(accessPolicyEvaluator).authorize(any(), eq(ConsumerAction.SCHEDULED_TRANSFER_LIST));
        verifyNoInteractions(standingInstructionFineractClient);
    }

    @Test
    void runHistoryShowsOnlyThisTransfersRunsNewestFirstWithoutFineractsErrorLog() {
        LocalDate first = START;
        LocalDate second = START.plusWeeks(1);
        when(standingInstructionFineractClient.listSavingsAccountRunHistory(CLIENT_ID, FROM_SAVINGS_ID))
                .thenReturn(List.of(
                        run(INSTRUCTION_ID, "success", AMOUNT, first),
                        run(OTHER_INSTRUCTION_ID, "success", AMOUNT, first),
                        run(INSTRUCTION_ID, "failed", BigDecimal.ZERO, second).errorLog("Insufficient balance")));

        ScheduledTransferRunQueryResponse page = service.listRuns(jwt(), runs(0, 20));

        assertThat(page.getContent()).containsExactly(
                ScheduledTransferRunQueryData.builder().date(second).status(ScheduledTransferRunStatus.FAILED).build(),
                ScheduledTransferRunQueryData.builder()
                        .date(first)
                        .amount(AMOUNT)
                        .status(ScheduledTransferRunStatus.SUCCEEDED)
                        .build());
        assertThat(page.getTotalElements()).isEqualTo(2);
        verify(standingInstructionFineractClient, never()).listClientStandingInstructions(any());
    }

    @Test
    void runHistoryLeavesOutRunStatusesTheBffNeverCreates() {
        when(standingInstructionFineractClient.listSavingsAccountRunHistory(CLIENT_ID, FROM_SAVINGS_ID))
                .thenReturn(List.of(run(INSTRUCTION_ID, "partial", AMOUNT, START)));

        assertThat(service.listRuns(jwt(), runs(0, 20)).getContent()).isEmpty();
    }

    @Test
    void runHistoryIsPaged() {
        when(standingInstructionFineractClient.listSavingsAccountRunHistory(CLIENT_ID, FROM_SAVINGS_ID))
                .thenReturn(List.of(
                        run(INSTRUCTION_ID, "success", AMOUNT, START),
                        run(INSTRUCTION_ID, "success", AMOUNT, START.plusWeeks(1)),
                        run(INSTRUCTION_ID, "success", AMOUNT, START.plusWeeks(2))));

        ScheduledTransferRunQueryResponse page = service.listRuns(jwt(), runs(1, 2));

        assertThat(page.getContent()).extracting(ScheduledTransferRunQueryData::getDate).containsExactly(START);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void runHistoryOfAnUnlinkedRowFindsTheInstructionByItsName() {
        when(repository.findByPublicIdAndUserId(PUBLIC_ID, USER_ID))
                .thenReturn(Optional.of(row(PUBLIC_ID, FROM_SAVINGS_ID, null)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID))
                .thenReturn(List.of(weekly(INSTRUCTION_ID, PUBLIC_ID)));
        when(standingInstructionFineractClient.listSavingsAccountRunHistory(CLIENT_ID, FROM_SAVINGS_ID))
                .thenReturn(List.of(run(INSTRUCTION_ID, "success", AMOUNT, START)));

        assertThat(service.listRuns(jwt(), runs(0, 20)).getContent()).hasSize(1);
    }

    @Test
    void runHistoryOfAnUnlinkedRowWithNoInstructionIsEmpty() {
        when(repository.findByPublicIdAndUserId(PUBLIC_ID, USER_ID))
                .thenReturn(Optional.of(row(PUBLIC_ID, FROM_SAVINGS_ID, null)));
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID)).thenReturn(List.of());

        ScheduledTransferRunQueryResponse page = service.listRuns(jwt(), runs(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verify(standingInstructionFineractClient, never()).listSavingsAccountRunHistory(any(), any());
    }

    @Test
    void runHistoryReportsUpstreamUnavailableWhenFineractFails() {
        when(standingInstructionFineractClient.listSavingsAccountRunHistory(CLIENT_ID, FROM_SAVINGS_ID))
                .thenThrow(feignStatus(500));

        assertThatThrownBy(() -> service.listRuns(jwt(), runs(0, 20)))
                .isInstanceOf(ScheduledTransferQueryUpstreamUnavailableException.class);
    }

    private ScheduledTransfersQueryServiceImpl service(boolean enabled) {
        return new ScheduledTransfersQueryServiceImpl(accessPolicyEvaluator, userClientResolver,
                standingInstructionFineractClient, repository, enabled);
    }

    private static ScheduledTransferQueryEntity row(UUID publicId, Long fromSavingsId, Long instructionId) {
        return ScheduledTransferQueryEntity.of(instructionId, publicId, USER_ID, fromSavingsId, TO_SAVINGS_ID,
                instructionId);
    }

    private static GetPageItemsStandingInstructionSwagger weekly(Long id, UUID publicId) {
        return instruction(id, publicId, FROM_SAVINGS_ID, ScheduledTransferQueryConstants.WEEKS_FREQUENCY_ID);
    }

    private static GetPageItemsStandingInstructionSwagger instruction(Long id, UUID publicId, Long fromSavingsId,
            int frequencyId) {
        return new GetPageItemsStandingInstructionSwagger()
                .id(id)
                .name(ScheduledTransferQueryConstants.INSTRUCTION_NAME_PREFIX + publicId)
                .fromAccount(new GetFromAccountStandingInstructionSwagger().id(fromSavingsId))
                .status(new GetStatusStandingInstructionSwagger().id(ScheduledTransferQueryConstants.ACTIVE_STATUS_ID))
                .recurrenceFrequency(new GetRecurrenceFrequencyStandingInstructionSwagger().id(frequencyId))
                .amount(AMOUNT)
                .validFrom(START);
    }

    private static GetStandingInstructionHistoryPageItemsResponse run(Long instructionId, String status,
            BigDecimal amount, LocalDate date) {
        return new GetStandingInstructionHistoryPageItemsResponse()
                .standingInstructionId(instructionId)
                .status(status)
                .amount(amount)
                .executionTime(date);
    }

    private static ScheduledTransferRunHistoryQuery runs(int page, int size) {
        return runs(PUBLIC_ID, page, size);
    }

    private static ScheduledTransferRunHistoryQuery runs(UUID publicId, int page, int size) {
        return ScheduledTransferRunHistoryQuery.builder().publicId(publicId).page(page).size(size).build();
    }

    private static FeignException feignStatus(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "http://fineract/standinginstructions", Map.of(),
                new byte[0], StandardCharsets.UTF_8, null);
        return FeignException.errorStatus("listStandingInstructions",
                feign.Response.builder().status(status).reason("x").request(request).headers(Map.of()).build());
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(SUBJECT.toString())
                .claim("scope", "read")
                .build();
    }
}
