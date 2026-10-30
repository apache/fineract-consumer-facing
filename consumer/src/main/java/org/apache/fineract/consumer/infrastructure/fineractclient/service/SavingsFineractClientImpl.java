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

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsProductApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
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

    private final SavingsAccountApi savingsAccountApi;
    private final SavingsChargesApi savingsChargesApi;
    private final SavingsAccountTransactionsApi savingsAccountTransactionsApi;
    private final SavingsProductApi savingsProductApi;
    private final ClientApi clientApi;

    @Override
    public GetClientsClientIdAccountsResponse retrieveAllClientAccounts(Long clientId) {
        return clientApi.retrieveAllClientAccounts(clientId);
    }

    @Override
    public SavingsAccountData retrieveSavingsAccount(Long accountId, Boolean staffInSelectedOfficeOnly,
                                                     String chargeStatus, String associations) {
        return savingsAccountApi.retrieveSavingsAccount(accountId, staffInSelectedOfficeOnly, chargeStatus, associations);
    }

    @Override
    public List<GetSavingsAccountsSavingsAccountIdChargesResponse> retrieveAllSavingsAccountCharges(
            Long accountId, String chargeStatus) {
        return savingsChargesApi.retrieveAllSavingsAccountCharges(accountId, chargeStatus);
    }

    @Override
    public SavingsAccountTransactionsSearchResponse searchSavingsAccountTransactions(
            Long accountId, String fromDate, String toDate, String unappliedTransferTransactionId,
            String transactionType, BigDecimal minAmount, BigDecimal maxAmount, String includeDeleted,
            Boolean credit, Boolean debit, Integer offset, Integer limit,
            String sortCriteriaOrderBy, String sortCriteriaSortOrder, String locale, String dateFormat) {
        return savingsAccountTransactionsApi.searchSavingsAccountTransactions(
                accountId, fromDate, toDate, unappliedTransferTransactionId, transactionType,
                minAmount, maxAmount, includeDeleted, credit, debit,
                offset, limit, sortCriteriaOrderBy, sortCriteriaSortOrder, locale, dateFormat);
    }

    @Override
    public SavingsAccountTransactionData retrieveOneSavingsAccountTransaction(Long accountId, Long transactionId) {
        return savingsAccountTransactionsApi.retrieveOneSavingsAccountTransaction(accountId, transactionId);
    }

    @Override
    public List<GetSavingsProductsResponse> retrieveAllSavingsProducts() {
        return savingsProductApi.retrieveAllSavingsProducts();
    }

    @Override
    public GetSavingsProductsProductIdResponse retrieveOneSavingsProduct(Long productId) {
        return savingsProductApi.retrieveOneSavingsProduct(productId);
    }
}