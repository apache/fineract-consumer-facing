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
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsAccountsSavingsAccountIdChargesResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsProductsProductIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsProductsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionsSearchResponse;

public interface SavingsFineractClient {

    List<GetClientsSavingsAccounts> listClientSavingsAccounts(Long clientId);

    SavingsAccountData getAccount(Long savingsId);

    List<GetSavingsAccountsSavingsAccountIdChargesResponse> getCharges(Long savingsId);

    SavingsAccountTransactionsSearchResponse searchTransactions(
            Long savingsId, LocalDate fromDate, LocalDate toDate, int offset, int limit,
            String sortOrderBy, String sortOrder);

    SavingsAccountTransactionData getTransaction(Long savingsId, Long transactionId);

    List<GetSavingsProductsResponse> listSavingsProducts();

    GetSavingsProductsProductIdResponse getSavingsProduct(Long productId);
}