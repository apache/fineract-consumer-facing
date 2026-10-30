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
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsAccountsSavingsAccountIdChargesResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsProductsProductIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsProductsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionsSearchResponse;

public interface SavingsFineractClient {

    GetClientsClientIdAccountsResponse retrieveAllClientAccounts(Long clientId);

    SavingsAccountData retrieveSavingsAccount(Long accountId, Boolean staffInSelectedOfficeOnly,
                                              String chargeStatus, String associations);

    List<GetSavingsAccountsSavingsAccountIdChargesResponse> retrieveAllSavingsAccountCharges(
            Long accountId, String chargeStatus);

    SavingsAccountTransactionsSearchResponse searchSavingsAccountTransactions(
            Long accountId, String fromDate, String toDate, String unappliedTransferTransactionId,
            String transactionType, BigDecimal minAmount, BigDecimal maxAmount, String includeDeleted,
            Boolean credit, Boolean debit, Integer offset, Integer limit,
            String sortCriteriaOrderBy, String sortCriteriaSortOrder, String locale, String dateFormat);

    SavingsAccountTransactionData retrieveOneSavingsAccountTransaction(Long accountId, Long transactionId);

    List<GetSavingsProductsResponse> retrieveAllSavingsProducts();

    GetSavingsProductsProductIdResponse retrieveOneSavingsProduct(Long productId);
}