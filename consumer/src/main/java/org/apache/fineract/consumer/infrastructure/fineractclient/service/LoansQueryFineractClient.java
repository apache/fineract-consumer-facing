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

import java.util.List;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanProductsProductIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoanProductsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdChargesChargeIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsTransactionIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GuarantorData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansResponse;

public interface LoansQueryFineractClient {

    List<GetClientsLoanAccounts> listClientLoanAccounts(Long clientId);

    GetLoansLoanIdResponse getLoanWithScheduleAndTransactions(Long loanId);

    GetLoansLoanIdTransactionsResponse getTransactionsPage(Long loanId, int page, int size, String sort);

    GetLoansLoanIdTransactionsResponse getAllTransactions(Long loanId, String sort);

    GetLoansLoanIdTransactionsTransactionIdResponse getTransaction(Long loanId, Long transactionId);

    List<GetLoansLoanIdChargesChargeIdResponse> getCharges(Long loanId);

    GetLoansLoanIdChargesChargeIdResponse getCharge(Long loanId, Long chargeId);

    List<GuarantorData> getGuarantors(Long loanId);

    List<GetLoanProductsResponse> listLoanProducts();

    GetLoanProductsProductIdResponse getLoanProduct(Long productId);

    PostLoansResponse previewSchedule(PostLoansRequest request);
}
