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
import java.util.List;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetPageItemsStandingInstructionSwagger;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetStandingInstructionHistoryPageItemsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetStandingInstructionsStandingInstructionIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.StandingInstructionCreationRequest;

public interface StandingInstructionFineractClient {

    /** Returns the new standing instruction's Fineract id. */
    Long createStandingInstruction(StandingInstructionCreationRequest request);

    /** Every standing instruction whose source account belongs to the client, in any status, oldest first. */
    List<GetPageItemsStandingInstructionSwagger> listClientStandingInstructions(Long clientId);

    GetStandingInstructionsStandingInstructionIdResponse getStandingInstruction(Long standingInstructionId);

    /**
     * Marks the instruction deleted in Fineract. Fineract does not reject a second delete of the same instruction,
     * so callers check its status first.
     */
    void deleteStandingInstruction(Long standingInstructionId);

    /**
     * Every recorded run of the instructions debiting the client's savings account between fromDate and toDate
     * (either may be null for no bound), oldest first, including runs of instructions deleted since.
     */
    List<GetStandingInstructionHistoryPageItemsResponse> listSavingsAccountRunHistory(
            Long clientId, Long savingsAccountId, LocalDate fromDate, LocalDate toDate);
}
