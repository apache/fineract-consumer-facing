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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;
import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StandingInstructionFineractClientImpl implements StandingInstructionFineractClient {
    // Fineract pages these lists by offset and applies no order of its own, so every page is requested in the same
    // order to keep the offsets stable. Instructions are read oldest first and run history newest first. orderBy must
    // name a column through Fineract's own SQL alias (atsi is the instruction table, atsih the run history table);
    // anything else fails with HTTP 500.
    static final int PAGE_SIZE = 200;
    static final String INSTRUCTION_ORDER = "atsi.id";
    static final String RUN_ORDER = "atsih.id";
    static final String ASCENDING = "ASC";
    static final int SAVINGS_ACCOUNT_TYPE = 2;
    static final String DELETE_COMMAND = "delete";
    static final String LOCALE = "en";
    static final String DATE_FORMAT = "yyyy-MM-dd";

    private final StandingInstructionsApi standingInstructionsApi;
    private final StandingInstructionsHistoryApi standingInstructionsHistoryApi;

    @Override
    public Long createStandingInstruction(StandingInstructionCreationRequest request) {
        PostStandingInstructionsResponse response = standingInstructionsApi.createStandingInstruction(request);
        return response == null ? null : response.getResourceId();
    }

    @Override
    public List<GetPageItemsStandingInstructionSwagger> listClientStandingInstructions(Long clientId) {
        return fetchAllPages(
                offset -> standingInstructionsApi.retrieveAllStandingInstructions(
                        null, offset, PAGE_SIZE, INSTRUCTION_ORDER, ASCENDING, null, null, clientId, null, null),
                GetStandingInstructionsResponse::getPageItems,
                GetStandingInstructionsResponse::getTotalFilteredRecords);
    }

    @Override
    public GetStandingInstructionsStandingInstructionIdResponse getStandingInstruction(Long standingInstructionId) {
        return standingInstructionsApi.retrieveOneStandingInstruction(standingInstructionId, null, null, null, null, null);
    }

    @Override
    public void deleteStandingInstruction(Long standingInstructionId) {
        standingInstructionsApi.updateStandingInstruction(
                standingInstructionId, DELETE_COMMAND, new StandingInstructionUpdatesRequest());
    }

    @Override
    public List<GetStandingInstructionHistoryPageItemsResponse> listSavingsAccountRunHistory(
            Long clientId, Long savingsAccountId, LocalDate fromDate, LocalDate toDate) {
        return fetchAllPages(
                offset -> standingInstructionsHistoryApi.retrieveAllStandingInstructionHistory(
                        null, offset, PAGE_SIZE, RUN_ORDER, ASCENDING, null, null, clientId, savingsAccountId,
                        SAVINGS_ACCOUNT_TYPE, LOCALE, DATE_FORMAT, isoDate(fromDate), isoDate(toDate)),
                GetStandingInstructionRunHistoryResponse::getPageItems,
                GetStandingInstructionRunHistoryResponse::getTotalFilteredRecords);
    }

    private static String isoDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static <P, T> List<T> fetchAllPages(IntFunction<P> fetchPage, Function<P, ? extends Collection<T>> itemsOf,
            Function<P, Integer> totalOf) {
        List<T> collected = new ArrayList<>();
        while (true) {
            P page = fetchPage.apply(collected.size());
            Collection<T> items = page == null ? null : itemsOf.apply(page);
            if (items == null || items.isEmpty()) {
                return collected;
            }
            collected.addAll(items);
            Integer total = totalOf.apply(page);
            if (items.size() < PAGE_SIZE || (total != null && collected.size() >= total)) {
                return collected;
            }
        }
    }
}
