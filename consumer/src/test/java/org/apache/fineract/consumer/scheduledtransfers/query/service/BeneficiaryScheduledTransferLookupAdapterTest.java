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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetFromAccountStandingInstructionSwagger;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetPageItemsStandingInstructionSwagger;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetStatusStandingInstructionSwagger;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.BusinessDateFineractClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClient;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferQueryConstants;
import org.apache.fineract.consumer.scheduledtransfers.query.domain.ScheduledTransferQueryEntity;
import org.apache.fineract.consumer.scheduledtransfers.query.repository.ScheduledTransferQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BeneficiaryScheduledTransferLookupAdapterTest {

    private static final Long USER_ID = 7L;
    private static final Long CLIENT_ID = 42L;
    private static final Long FROM_SAVINGS_ID = 11L;
    private static final Long OTHER_SAVINGS_ID = 12L;
    private static final Long BENEFICIARY_SAVINGS_ID = 99L;
    private static final Long INSTRUCTION_ID = 501L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-0000000000a1");
    private static final BigDecimal AMOUNT = new BigDecimal("25.50");
    private static final LocalDate FINERACT_TODAY = LocalDate.of(2026, 3, 10);
    private static final int DELETED_STATUS_ID = 3;

    @Mock
    private ScheduledTransferQueryRepository repository;
    @Mock
    private StandingInstructionFineractClient standingInstructionFineractClient;
    @Mock
    private BusinessDateFineractClient businessDateFineractClient;

    private BeneficiaryScheduledTransferLookupAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new BeneficiaryScheduledTransferLookupAdapter(
                repository, standingInstructionFineractClient, businessDateFineractClient);
        when(businessDateFineractClient.today()).thenReturn(FINERACT_TODAY);
    }

    @Test
    void noTransferToTheAccountAsksFineractNothing() {
        when(repository.findAllByUserIdAndFineractToAccountId(USER_ID, BENEFICIARY_SAVINGS_ID)).thenReturn(List.of());

        assertThat(adapter.hasActiveTransfersTo(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID)).isFalse();
        assertThat(adapter.hasActiveTransfersAbove(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID, BigDecimal.ONE))
                .isFalse();
        verifyNoInteractions(standingInstructionFineractClient, businessDateFineractClient);
    }

    @Test
    void anActiveLinkedInstructionCounts() {
        rows(linked());
        instructions(active());

        assertThat(adapter.hasActiveTransfersTo(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID)).isTrue();
    }

    @Test
    void aCancelledInstructionDoesNotCount() {
        rows(linked());
        instructions(active().status(new GetStatusStandingInstructionSwagger().id(DELETED_STATUS_ID)));

        assertThat(adapter.hasActiveTransfersTo(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID)).isFalse();
    }

    @Test
    void anInstructionPastItsEndDateDoesNotCount() {
        rows(linked());
        // validTill is exclusive: the last run was the day before.
        instructions(active().validTill(FINERACT_TODAY));

        assertThat(adapter.hasActiveTransfersTo(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID)).isFalse();
    }

    @Test
    void anInstructionWithAnEndDateCountsWhenFineractReportsNoDate() {
        rows(linked());
        instructions(active().validTill(FINERACT_TODAY));
        when(businessDateFineractClient.today()).thenReturn(null);

        assertThat(adapter.hasActiveTransfersTo(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID)).isTrue();
    }

    @Test
    void aBankCreatedInstructionDoesNotCount() {
        rows(linked());
        instructions(active().id(INSTRUCTION_ID + 1).name("Loan repayment"));

        assertThat(adapter.hasActiveTransfersTo(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID)).isFalse();
    }

    @Test
    void anInstructionDebitingAnotherAccountDoesNotCount() {
        rows(linked());
        instructions(active().fromAccount(new GetFromAccountStandingInstructionSwagger().id(OTHER_SAVINGS_ID)));

        assertThat(adapter.hasActiveTransfersTo(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID)).isFalse();
    }

    @Test
    void anUnlinkedRowCountsWhenFineractHasItsInstructionByName() {
        rows(unlinked(null));
        instructions(active());

        assertThat(adapter.hasActiveTransfersTo(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID)).isTrue();
    }

    @Test
    void anUnlinkedRowWithNoInstructionAndNoClaimDoesNotCount() {
        rows(unlinked(Instant.now().minusSeconds(1)));
        instructions();

        assertThat(adapter.hasActiveTransfersTo(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID)).isFalse();
    }

    @Test
    void aRowAConfirmIsWorkingOnCountsWithoutAskingFineract() {
        rows(unlinked(Instant.now().plusSeconds(60)));

        assertThat(adapter.hasActiveTransfersTo(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID)).isTrue();
        assertThat(adapter.hasActiveTransfersAbove(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID, AMOUNT)).isTrue();
        verifyNoInteractions(standingInstructionFineractClient);
    }

    @Test
    void onlyAnAmountAboveTheLimitCounts() {
        rows(linked());
        instructions(active());

        assertThat(adapter.hasActiveTransfersAbove(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID,
                new BigDecimal("25.49"))).isTrue();
        assertThat(adapter.hasActiveTransfersAbove(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID, AMOUNT)).isFalse();
    }

    @Test
    void anInstructionWithNoAmountCountsAgainstAnyLimit() {
        rows(linked());
        instructions(active().amount(null));

        assertThat(adapter.hasActiveTransfersAbove(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID, AMOUNT)).isTrue();
    }

    @Test
    void fineractFailuresReachTheCaller() {
        rows(linked());
        FeignException failure = mock(FeignException.InternalServerError.class);
        when(standingInstructionFineractClient.listClientStandingInstructions(any())).thenThrow(failure);

        assertThatThrownBy(() -> adapter.hasActiveTransfersTo(USER_ID, CLIENT_ID, BENEFICIARY_SAVINGS_ID))
                .isSameAs(failure);
    }

    private void rows(ScheduledTransferQueryEntity... rows) {
        when(repository.findAllByUserIdAndFineractToAccountId(USER_ID, BENEFICIARY_SAVINGS_ID))
                .thenReturn(List.of(rows));
    }

    private void instructions(GetPageItemsStandingInstructionSwagger... instructions) {
        when(standingInstructionFineractClient.listClientStandingInstructions(CLIENT_ID))
                .thenReturn(List.of(instructions));
    }

    private static ScheduledTransferQueryEntity linked() {
        return ScheduledTransferQueryEntity.of(1L, PUBLIC_ID, USER_ID, FROM_SAVINGS_ID, BENEFICIARY_SAVINGS_ID,
                INSTRUCTION_ID, null);
    }

    private static ScheduledTransferQueryEntity unlinked(Instant claimedUntil) {
        return ScheduledTransferQueryEntity.of(1L, PUBLIC_ID, USER_ID, FROM_SAVINGS_ID, BENEFICIARY_SAVINGS_ID,
                null, claimedUntil);
    }

    private static GetPageItemsStandingInstructionSwagger active() {
        return new GetPageItemsStandingInstructionSwagger()
                .id(INSTRUCTION_ID)
                .name(ScheduledTransferQueryConstants.INSTRUCTION_NAME_PREFIX + PUBLIC_ID)
                .fromAccount(new GetFromAccountStandingInstructionSwagger().id(FROM_SAVINGS_ID))
                .status(new GetStatusStandingInstructionSwagger().id(ScheduledTransferQueryConstants.ACTIVE_STATUS_ID))
                .amount(AMOUNT);
    }
}
