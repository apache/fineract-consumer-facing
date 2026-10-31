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

import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsAccountIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsAccountIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutSavingsAccountsAccountIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutSavingsAccountsAccountIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;

public interface SavingsAccountApiClient {

    PostSavingsAccountsResponse submitSavingsApplication(PostSavingsAccountsRequest request);

    PutSavingsAccountsAccountIdResponse updateSavingsAccount(Long accountId, PutSavingsAccountsAccountIdRequest request, String command);

    PostSavingsAccountsAccountIdResponse handleCommandsSavingsAccount(Long accountId, PostSavingsAccountsAccountIdRequest request, String command);

    SavingsAccountData retrieveSavingsAccount(Long accountId);
}
