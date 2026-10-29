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
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.GuarantorsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanProductsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoansQueryFineractClientImpl implements LoansQueryFineractClient {

    private static final String ASSOCIATIONS = "repaymentSchedule,transactions";
    private static final String LOCALE = "en";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final String LOAN_TYPE = "individual";
    private static final String CALCULATE_SCHEDULE_COMMAND = "calculateLoanSchedule";
    static final int FETCH_ALL_PAGE_SIZE = 10_000;

    private final ClientApi clientApi;
    private final LoansApi loansApi;
    private final LoanTransactionsApi loanTransactionsApi;
    private final LoanChargesApi loanChargesApi;
    private final GuarantorsApi guarantorsApi;
    private final LoanProductsApi loanProductsApi;

    @Override
    public List<GetClientsLoanAccounts> listClientLoanAccounts(Long clientId) {
        GetClientsClientIdAccountsResponse accounts = clientApi.retrieveAllClientAccounts(clientId);
        if (accounts == null || accounts.getLoanAccounts() == null) {
            return List.of();
        }
        return accounts.getLoanAccounts().stream().toList();
    }

    @Override
    public GetLoansLoanIdResponse getLoanWithScheduleAndTransactions(Long loanId) {
        return loansApi.retrieveOneLoan(loanId, false, ASSOCIATIONS, null, null);
    }

    @Override
    public GetLoansLoanIdTransactionsResponse getTransactionsPage(Long loanId, int page, int size, String sort) {
        return loanTransactionsApi.retrieveAllLoanTransactions(loanId, null, page, size, sort);
    }

    @Override
    public GetLoansLoanIdTransactionsResponse getAllTransactions(Long loanId, String sort) {
        return loanTransactionsApi.retrieveAllLoanTransactions(loanId, null, 0, FETCH_ALL_PAGE_SIZE, sort);
    }

    @Override
    public GetLoansLoanIdTransactionsTransactionIdResponse getTransaction(Long loanId, Long transactionId) {
        return loanTransactionsApi.retrieveOneLoanTransaction(loanId, transactionId, null);
    }

    @Override
    public List<GetLoansLoanIdChargesChargeIdResponse> getCharges(Long loanId) {
        return loanChargesApi.retrieveAllLoanCharges(loanId);
    }

    @Override
    public GetLoansLoanIdChargesChargeIdResponse getCharge(Long loanId, Long chargeId) {
        return loanChargesApi.retrieveOneLoanCharge(loanId, chargeId);
    }

    @Override
    public List<GuarantorData> getGuarantors(Long loanId) {
        return guarantorsApi.retrieveGuarantorDetails(loanId);
    }

    @Override
    public List<GetLoanProductsResponse> listLoanProducts() {
        return loanProductsApi.retrieveAllLoanProducts();
    }

    @Override
    public GetLoanProductsProductIdResponse getLoanProduct(Long productId) {
        return loanProductsApi.retrieveOneLoanProduct(productId);
    }

    @Override
    public PostLoansResponse previewSchedule(PostLoansRequest request) {
        request.locale(LOCALE).dateFormat(DATE_FORMAT).loanType(LOAN_TYPE);
        return loansApi.calculateOrSubmitLoanApplication(request, CALCULATE_SCHEDULE_COMMAND);
    }
}
