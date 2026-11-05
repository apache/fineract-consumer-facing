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
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsProductApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsAccountsSavingsAccountIdChargesResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsProductsProductIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsProductsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionsSearchResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsFineractClientImpl implements SavingsFineractClient {

    private static final String LOCALE = "en";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private final ClientApi clientApi;
    private final SavingsAccountApi savingsAccountApi;
    private final SavingsChargesApi savingsChargesApi;
    private final SavingsAccountTransactionsApi savingsAccountTransactionsApi;
    private final SavingsProductApi savingsProductApi;

    @Override
    public List<GetClientsSavingsAccounts> listClientSavingsAccounts(Long clientId) {
        GetClientsClientIdAccountsResponse accounts = clientApi.retrieveAllClientAccounts(clientId);
        if (accounts == null || accounts.getSavingsAccounts() == null) {
            return List.of();
        }
        return accounts.getSavingsAccounts().stream().toList();
    }

    @Override
    public SavingsAccountData getAccount(Long savingsId) {
        return savingsAccountApi.retrieveSavingsAccount(savingsId, null, null, null);
    }

    @Override
    public List<GetSavingsAccountsSavingsAccountIdChargesResponse> getCharges(Long savingsId) {
        return savingsChargesApi.retrieveAllSavingsAccountCharges(savingsId, null);
    }

    @Override
    public SavingsAccountTransactionsSearchResponse searchTransactions(
            Long savingsId, LocalDate fromDate, LocalDate toDate, int offset, int limit,
            String sortOrderBy, String sortOrder) {
        return savingsAccountTransactionsApi.searchSavingsAccountTransactions(
                savingsId,
                isoDate(fromDate),
                isoDate(toDate),
                null, null, null, null, null, null, null,
                offset,
                limit,
                sortOrderBy,
                sortOrder,
                LOCALE, DATE_FORMAT);
    }

    @Override
    public SavingsAccountTransactionData getTransaction(Long savingsId, Long transactionId) {
        return savingsAccountTransactionsApi.retrieveOneSavingsAccountTransaction(savingsId, transactionId);
    }

    @Override
    public List<GetSavingsProductsResponse> listSavingsProducts() {
        return savingsProductApi.retrieveAllSavingsProducts();
    }

    @Override
    public GetSavingsProductsProductIdResponse getSavingsProduct(Long productId) {
        return savingsProductApi.retrieveOneSavingsProduct(productId);
    }

    private static String isoDate(LocalDate date) {
        return date == null ? null : date.toString();
    }
}