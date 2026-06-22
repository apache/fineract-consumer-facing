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

package org.apache.fineract.consumer.savings.query.service;

import java.util.List;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountListItemQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsApplicationTemplateQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsChargeQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionSearchQuery;

public interface SavingsQueryService {

    List<SavingsAccountListItemQueryData> listAccounts(Long clientId);

    SavingsAccountQueryData getAccount(Long clientId, Long savingsId);

    List<SavingsChargeQueryData> getCharges(Long clientId, Long savingsId);

    List<SavingsTransactionQueryData> searchTransactions(Long clientId, SavingsTransactionSearchQuery query);

    SavingsTransactionQueryData getTransaction(Long clientId, Long savingsId, Long transactionId);

    SavingsApplicationTemplateQueryData getApplicationTemplate(Long productId);
}
