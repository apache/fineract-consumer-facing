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

package org.apache.fineract.consumer.infrastructure.fineractclient.service;

import static org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClientImpl.ASCENDING;
import static org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClientImpl.DELETE_COMMAND;
import static org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClientImpl.DESCENDING;
import static org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClientImpl.INSTRUCTION_ORDER;
import static org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClientImpl.PAGE_SIZE;
import static org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClientImpl.RUN_ORDER;
import static org.apache.fineract.consumer.infrastructure.fineractclient.service.StandingInstructionFineractClientImpl.SAVINGS_ACCOUNT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.StandingInstructionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.StandingInstructionsHistoryApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetPageItemsStandingInstructionSwagger;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetStandingInstructionHistoryPageItemsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetStandingInstructionRunHistoryResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetStandingInstructionsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetStandingInstructionsStandingInstructionIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostStandingInstructionsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.StandingInstructionCreationRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.StandingInstructionUpdatesRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandingInstructionFineractClientImplTest {

    private static final Long CLIENT_ID = 11L;
    private static final Long SAVINGS_ID = 42L;
    private static final Long INSTRUCTION_ID = 7L;

    @Mock
    private StandingInstructionsApi standingInstructionsApi;

    @Mock
    private StandingInstructionsHistoryApi standingInstructionsHistoryApi;

    @InjectMocks
    private StandingInstructionFineractClientImpl client;

    @Test
    void createReturnsTheNewInstructionId() {
        StandingInstructionCreationRequest request = new StandingInstructionCreationRequest().name("weekly rent");
        when(standingInstructionsApi.createStandingInstruction(request))
                .thenReturn(new PostStandingInstructionsResponse().resourceId(INSTRUCTION_ID));

        assertThat(client.createStandingInstruction(request)).isEqualTo(INSTRUCTION_ID);
    }

    @Test
    void createReturnsNullWhenFineractReturnsNoBody() {
        StandingInstructionCreationRequest request = new StandingInstructionCreationRequest();
        when(standingInstructionsApi.createStandingInstruction(request)).thenReturn(null);

        assertThat(client.createStandingInstruction(request)).isNull();
    }

    @Test
    void listClientStandingInstructionsRequestsTheClientsInstructionsInIdOrder() {
        GetPageItemsStandingInstructionSwagger instruction = new GetPageItemsStandingInstructionSwagger().id(INSTRUCTION_ID);
        when(standingInstructionsApi.retrieveAllStandingInstructions(
                null, 0, PAGE_SIZE, INSTRUCTION_ORDER, ASCENDING, null, null, CLIENT_ID, null, null))
                .thenReturn(new GetStandingInstructionsResponse().pageItems(Set.of(instruction)).totalFilteredRecords(1));

        assertThat(client.listClientStandingInstructions(CLIENT_ID)).containsExactly(instruction);
    }

    @Test
    void listClientStandingInstructionsFollowsPagesUntilTheTotalIsReached() {
        Set<GetPageItemsStandingInstructionSwagger> firstPage = instructions(1, PAGE_SIZE);
        Set<GetPageItemsStandingInstructionSwagger> secondPage = instructions(PAGE_SIZE + 1, PAGE_SIZE + 1);
        int total = PAGE_SIZE + 1;
        when(standingInstructionsApi.retrieveAllStandingInstructions(
                null, 0, PAGE_SIZE, INSTRUCTION_ORDER, ASCENDING, null, null, CLIENT_ID, null, null))
                .thenReturn(new GetStandingInstructionsResponse().pageItems(firstPage).totalFilteredRecords(total));
        when(standingInstructionsApi.retrieveAllStandingInstructions(
                null, PAGE_SIZE, PAGE_SIZE, INSTRUCTION_ORDER, ASCENDING, null, null, CLIENT_ID, null, null))
                .thenReturn(new GetStandingInstructionsResponse().pageItems(secondPage).totalFilteredRecords(total));

        List<GetPageItemsStandingInstructionSwagger> result = client.listClientStandingInstructions(CLIENT_ID);

        assertThat(result).hasSize(total).containsAll(firstPage).containsAll(secondPage);
    }

    @Test
    void listClientStandingInstructionsStopsAtAFullPageThatReachesTheTotal() {
        Set<GetPageItemsStandingInstructionSwagger> onlyPage = instructions(1, PAGE_SIZE);
        when(standingInstructionsApi.retrieveAllStandingInstructions(
                null, 0, PAGE_SIZE, INSTRUCTION_ORDER, ASCENDING, null, null, CLIENT_ID, null, null))
                .thenReturn(new GetStandingInstructionsResponse().pageItems(onlyPage).totalFilteredRecords(PAGE_SIZE));

        assertThat(client.listClientStandingInstructions(CLIENT_ID)).hasSize(PAGE_SIZE);
        verify(standingInstructionsApi, never()).retrieveAllStandingInstructions(
                any(), eq(PAGE_SIZE), anyInt(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void listClientStandingInstructionsIsEmptyWhenFineractReturnsNothing() {
        when(standingInstructionsApi.retrieveAllStandingInstructions(
                null, 0, PAGE_SIZE, INSTRUCTION_ORDER, ASCENDING, null, null, CLIENT_ID, null, null))
                .thenReturn(null);

        assertThat(client.listClientStandingInstructions(CLIENT_ID)).isEmpty();
    }

    @Test
    void getStandingInstructionDelegatesToGeneratedApi() {
        GetStandingInstructionsStandingInstructionIdResponse expected =
                new GetStandingInstructionsStandingInstructionIdResponse().id(INSTRUCTION_ID);
        when(standingInstructionsApi.retrieveOneStandingInstruction(INSTRUCTION_ID, null, null, null, null, null))
                .thenReturn(expected);

        assertThat(client.getStandingInstruction(INSTRUCTION_ID)).isSameAs(expected);
    }

    @Test
    void deleteSendsTheDeleteCommandWithAnEmptyBody() {
        client.deleteStandingInstruction(INSTRUCTION_ID);

        verify(standingInstructionsApi).updateStandingInstruction(
                INSTRUCTION_ID, DELETE_COMMAND, new StandingInstructionUpdatesRequest());
    }

    @Test
    void listSavingsAccountRunHistoryFiltersBySavingsSourceAccountNewestFirst() {
        GetStandingInstructionHistoryPageItemsResponse run = run(INSTRUCTION_ID, "success");
        when(runHistoryRequest(0)).thenReturn(page(List.of(run), 1));

        assertThat(client.listSavingsAccountRunHistory(CLIENT_ID, SAVINGS_ID, null, null)).containsExactly(run);
    }

    @Test
    void listSavingsAccountRunHistoryKeepsIdenticalRuns() {
        // Two failed runs of one instruction on one business date carry identical fields; both must survive.
        List<GetStandingInstructionHistoryPageItemsResponse> failedRuns =
                new ArrayList<>(List.of(run(INSTRUCTION_ID, "failed"), run(INSTRUCTION_ID, "failed")));
        when(runHistoryRequest(0)).thenReturn(page(failedRuns, 2));

        assertThat(client.listSavingsAccountRunHistory(CLIENT_ID, SAVINGS_ID, null, null)).hasSize(2);
    }

    @Test
    void listSavingsAccountRunHistoryKeepsRunsInsideTheWindowAndDropsOlderOnes() {
        LocalDate from = LocalDate.of(2029, 1, 15);
        GetStandingInstructionHistoryPageItemsResponse onTheStart = runOn(1, from);
        GetStandingInstructionHistoryPageItemsResponse inside = runOn(2, from.plusDays(30));
        GetStandingInstructionHistoryPageItemsResponse justBefore = runOn(3, from.minusDays(1));
        // Newest first, as Fineract returns them.
        when(runHistoryRequest(0)).thenReturn(page(List.of(inside, onTheStart, justBefore), 3));

        assertThat(client.listSavingsAccountRunHistory(CLIENT_ID, SAVINGS_ID, from, null))
                .containsExactly(inside, onTheStart);
    }

    @Test
    void listSavingsAccountRunHistoryKeepsRunsUpToAndIncludingTheEndDate() {
        LocalDate to = LocalDate.of(2029, 6, 30);
        GetStandingInstructionHistoryPageItemsResponse after = runOn(1, to.plusDays(1));
        GetStandingInstructionHistoryPageItemsResponse onTheEnd = runOn(2, to);
        GetStandingInstructionHistoryPageItemsResponse before = runOn(3, to.minusDays(1));
        when(runHistoryRequest(0)).thenReturn(page(List.of(after, onTheEnd, before), 3));

        assertThat(client.listSavingsAccountRunHistory(CLIENT_ID, SAVINGS_ID, null, to))
                .containsExactly(onTheEnd, before);
    }

    @Test
    void listSavingsAccountRunHistoryStopsReadingOnceAPageReachesRunsBeforeTheWindow() {
        LocalDate from = LocalDate.of(2029, 1, 15);
        List<GetStandingInstructionHistoryPageItemsResponse> firstPage = new ArrayList<>(runsOn(PAGE_SIZE - 5, from));
        firstPage.addAll(runsOn(5, from.minusDays(1)));
        // The total says far more runs exist, so only the date can end the read.
        when(runHistoryRequest(0)).thenReturn(page(firstPage, PAGE_SIZE * 10));

        assertThat(client.listSavingsAccountRunHistory(CLIENT_ID, SAVINGS_ID, from, null))
                .hasSize(PAGE_SIZE - 5);
        verify(standingInstructionsHistoryApi, never()).retrieveAllStandingInstructionHistory(
                any(), eq(PAGE_SIZE), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void listSavingsAccountRunHistoryKeepsReadingWhileEveryRunIsInsideTheWindow() {
        LocalDate from = LocalDate.of(2029, 1, 15);
        List<GetStandingInstructionHistoryPageItemsResponse> firstPage = runsOn(PAGE_SIZE, from.plusDays(10));
        List<GetStandingInstructionHistoryPageItemsResponse> secondPage = runsOn(1, from);
        int total = PAGE_SIZE + 1;
        when(runHistoryRequest(0)).thenReturn(page(firstPage, total));
        when(runHistoryRequest(PAGE_SIZE)).thenReturn(page(secondPage, total));

        List<GetStandingInstructionHistoryPageItemsResponse> result =
                client.listSavingsAccountRunHistory(CLIENT_ID, SAVINGS_ID, from, null);

        List<GetStandingInstructionHistoryPageItemsResponse> expected = new ArrayList<>(firstPage);
        expected.addAll(secondPage);
        assertThat(result).containsExactlyElementsOf(expected);
    }

    @Test
    void listSavingsAccountRunHistoryKeepsRunsWithNoDate() {
        GetStandingInstructionHistoryPageItemsResponse undated = run(INSTRUCTION_ID, "success");
        when(runHistoryRequest(0)).thenReturn(page(List.of(undated), 1));

        assertThat(client.listSavingsAccountRunHistory(
                CLIENT_ID, SAVINGS_ID, LocalDate.of(2029, 1, 15), LocalDate.of(2029, 12, 31)))
                .containsExactly(undated);
    }

    @Test
    void listSavingsAccountRunHistoryFollowsPagesUntilTheTotalIsReached() {
        List<GetStandingInstructionHistoryPageItemsResponse> firstPage = runs(1, PAGE_SIZE);
        List<GetStandingInstructionHistoryPageItemsResponse> secondPage = runs(PAGE_SIZE + 1L, PAGE_SIZE + 1L);
        int total = PAGE_SIZE + 1;
        when(runHistoryRequest(0)).thenReturn(page(firstPage, total));
        when(runHistoryRequest(PAGE_SIZE)).thenReturn(page(secondPage, total));

        assertThat(client.listSavingsAccountRunHistory(CLIENT_ID, SAVINGS_ID, null, null)).hasSize(total);
    }

    @Test
    void listSavingsAccountRunHistoryStopsAtAFullPageThatReachesTheTotal() {
        when(runHistoryRequest(0)).thenReturn(page(runs(1, PAGE_SIZE), PAGE_SIZE));

        assertThat(client.listSavingsAccountRunHistory(CLIENT_ID, SAVINGS_ID, null, null)).hasSize(PAGE_SIZE);
        verify(standingInstructionsHistoryApi, never()).retrieveAllStandingInstructionHistory(
                any(), eq(PAGE_SIZE), anyInt(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any());
    }

    @Test
    void listSavingsAccountRunHistoryIsEmptyWhenFineractReturnsNothing() {
        when(runHistoryRequest(0)).thenReturn(null);

        assertThat(client.listSavingsAccountRunHistory(CLIENT_ID, SAVINGS_ID, null, null)).isEmpty();
    }

    /**
     * The one Fineract request a run history read makes for a page, so stubs and reads cannot drift apart. Fineract's
     * own date filter fails on PostgreSQL, so no locale, date format or dates are sent.
     */
    private GetStandingInstructionRunHistoryResponse runHistoryRequest(int offset) {
        return standingInstructionsHistoryApi.retrieveAllStandingInstructionHistory(
                null, offset, PAGE_SIZE, RUN_ORDER, DESCENDING, null, null, CLIENT_ID, SAVINGS_ID,
                SAVINGS_ACCOUNT_TYPE, null, null, null, null);
    }

    private static GetStandingInstructionRunHistoryResponse page(
            List<GetStandingInstructionHistoryPageItemsResponse> items, int total) {
        return new GetStandingInstructionRunHistoryResponse().pageItems(items).totalFilteredRecords(total);
    }

    private static GetStandingInstructionHistoryPageItemsResponse run(Long instructionId, String status) {
        return new GetStandingInstructionHistoryPageItemsResponse().standingInstructionId(instructionId).status(status);
    }

    private static GetStandingInstructionHistoryPageItemsResponse runOn(long id, LocalDate date) {
        return run(id, "success").executionTime(date);
    }

    private static List<GetStandingInstructionHistoryPageItemsResponse> runsOn(int count, LocalDate date) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> runOn(id, date)).toList();
    }

    private static List<GetStandingInstructionHistoryPageItemsResponse> runs(long firstId, long lastId) {
        return LongStream.rangeClosed(firstId, lastId).mapToObj(id -> run(id, "success")).toList();
    }

    private static Set<GetPageItemsStandingInstructionSwagger> instructions(long firstId, long lastId) {
        return LongStream.rangeClosed(firstId, lastId)
                .mapToObj(id -> new GetPageItemsStandingInstructionSwagger().id(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
