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

import java.math.BigDecimal;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.fineractclient.data.LoanPayoffData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.AccountTransfersApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.AccountTransferRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetAccountTransfersPageItems;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetAccountTransfersResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdStatus;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdTransactionsTemplateResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostAccountTransfersResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransferFineractClientImpl implements TransferFineractClient {

    private static final String PREPAY_COMMAND = "prepayLoan";
    private static final String NO_ASSOCIATIONS = "";

    private final ClientApi clientApi;
    private final SavingsAccountApi savingsAccountApi;
    private final AccountTransfersApi accountTransfersApi;
    private final LoansApi loansApi;
    private final LoanTransactionsApi loanTransactionsApi;

    @Override
    public Long getClientOfficeId(Long clientId) {
        GetClientsClientIdResponse client = clientApi.retrieveOneClient(clientId, false);
        return client == null ? null : client.getOfficeId();
    }

    @Override
    public Long getSavingsAccountClientId(Long savingsAccountId) {
        SavingsAccountData destination = savingsAccountApi.retrieveSavingsAccount(savingsAccountId, null, null, null);
        return destination == null ? null : destination.getClientId();
    }

    @Override
    public Long createAccountTransfer(AccountTransferRequest request) {
        PostAccountTransfersResponse response = accountTransfersApi.createAccountTransfer(request);
        return response == null ? null : response.getResourceId();
    }

    @Override
    public Long getAccountTransferId(Long transferDetailId) {
        // Without the filter Fineract lists every transfer in the tenant.
        if (transferDetailId == null) {
            return null;
        }
        GetAccountTransfersResponse page =
                accountTransfersApi.retrieveAllAccountTransfers(null, null, null, null, null, transferDetailId);
        Collection<GetAccountTransfersPageItems> transfers = page == null ? null : page.getPageItems();
        if (transfers == null || transfers.size() != 1) {
            return null;
        }
        return transfers.iterator().next().getId();
    }

    @Override
    public LoanPayoffData getLoanPayoff(Long loanId) {
        GetLoansLoanIdResponse loan = loansApi.retrieveOneLoan(loanId, false, NO_ASSOCIATIONS, null, null);
        GetLoansLoanIdStatus status = loan == null ? null : loan.getStatus();
        boolean active = status != null && Boolean.TRUE.equals(status.getActive());
        if (!active) {
            return LoanPayoffData.builder()
                    .active(false)
                    .payoffAmount(BigDecimal.ZERO)
                    .build();
        }
        GetLoansLoanIdTransactionsTemplateResponse template =
                loanTransactionsApi.retrieveTemplateLoanTransaction(loanId, PREPAY_COMMAND, null, null, null, null);
        BigDecimal amount = template == null || template.getAmount() == null
                ? BigDecimal.ZERO
                : template.getAmount();
        return LoanPayoffData.builder()
                .active(true)
                .payoffAmount(amount)
                .build();
    }

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
    public GetAccountTransfersPageItems retrieveOneAccountTransfer(Long resourceId) {
        return accountTransfersApi.retrieveOneAccountTransfer(resourceId);
    }
}