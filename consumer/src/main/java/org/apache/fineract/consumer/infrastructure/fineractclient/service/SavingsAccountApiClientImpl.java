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

import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsAccountIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsAccountIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutSavingsAccountsAccountIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutSavingsAccountsAccountIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsAccountApiClientImpl implements SavingsAccountApiClient {

    private final SavingsAccountApi savingsAccountApi;

    @Override
    public PostSavingsAccountsResponse submitSavingsApplication(PostSavingsAccountsRequest request) {
        return savingsAccountApi.submitSavingsApplication(request);
    }

    @Override
    public PutSavingsAccountsAccountIdResponse updateSavingsAccount(Long accountId, PutSavingsAccountsAccountIdRequest request, String command) {
        return savingsAccountApi.updateSavingsAccount(accountId, request, command);
    }

    @Override
    public PostSavingsAccountsAccountIdResponse handleCommandsSavingsAccount(Long accountId, PostSavingsAccountsAccountIdRequest request, String command) {
        return savingsAccountApi.handleCommandsSavingsAccount(accountId, request, command);
    }

    @Override
    public SavingsAccountData retrieveSavingsAccount(Long accountId) {
        return savingsAccountApi.retrieveSavingsAccount(accountId, null, null, null);
    }
}
