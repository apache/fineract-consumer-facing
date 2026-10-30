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

package org.apache.fineract.consumer.infrastructure.fineractclient.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SearchApiApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSearchResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BeneficiariesFineractClientImpl implements BeneficiariesFineractClient {

    private final SavingsAccountApi savingsAccountApi;
    private final SearchApiApi searchApiApi;
    private final ClientApi clientApi;

    @Override
    public List<GetSearchResponse> searchData(String query, String resource, Boolean exactMatch) {
        return searchApiApi.searchData(query, resource, exactMatch);
    }

    @Override
    public SavingsAccountData retrieveSavingsAccount(Long accountId, Boolean staffInSelectedOfficeOnly,
                                                     String chargeStatus, String associations) {
        return savingsAccountApi.retrieveSavingsAccount(
                accountId, staffInSelectedOfficeOnly, chargeStatus, associations);
    }

    @Override
    public GetClientsClientIdResponse retrieveOneClient(Long clientId, Boolean staffInSelectedOfficeOnly) {
        return clientApi.retrieveOneClient(clientId, staffInSelectedOfficeOnly);
    }
}