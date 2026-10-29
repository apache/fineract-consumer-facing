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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoansQueryFineractClientImplTest {

    private static final Long CLIENT_ID = 10L;
    private static final Long LOAN_ID = 30L;
    private static final Long TRANSACTION_ID = 40L;
    private static final Long CHARGE_ID = 50L;
    private static final Long PRODUCT_ID = 60L;
    private static final String SORT = "date,desc";

    @Mock
    private ClientApi clientApi;

    @Mock
    private LoansApi loansApi;

    @Mock
    private LoanTransactionsApi loanTransactionsApi;

    @Mock
    private LoanChargesApi loanChargesApi;

    @Mock
    private GuarantorsApi guarantorsApi;

    @Mock
    private LoanProductsApi loanProductsApi;

    @InjectMocks
    private LoansQueryFineractClientImpl client;

    @Test
    void listClientLoanAccountsReturnsLoanAccounts() {
        GetClientsLoanAccounts first = new GetClientsLoanAccounts().id(1L);
        GetClientsLoanAccounts second = new GetClientsLoanAccounts().id(2L);
        LinkedHashSet<GetClientsLoanAccounts> loanAccounts = new LinkedHashSet<>(List.of(first, second));
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse().loanAccounts(loanAccounts));

        List<GetClientsLoanAccounts> accounts = client.listClientLoanAccounts(CLIENT_ID);

        assertThat(accounts).containsExactly(first, second);
        verify(clientApi).retrieveAllClientAccounts(CLIENT_ID);
    }

    @Test
    void listClientLoanAccountsReturnsEmptyWhenResponseNull() {
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenReturn(null);

        List<GetClientsLoanAccounts> accounts = client.listClientLoanAccounts(CLIENT_ID);

        assertThat(accounts).isEmpty();
    }

    @Test
    void listClientLoanAccountsReturnsEmptyWhenLoanAccountsNull() {
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse().loanAccounts(null));

        List<GetClientsLoanAccounts> accounts = client.listClientLoanAccounts(CLIENT_ID);

        assertThat(accounts).isEmpty();
    }

    @Test
    void getLoanWithScheduleAndTransactionsRequestsBothAssociations() {
        GetLoansLoanIdResponse loan = new GetLoansLoanIdResponse().id(LOAN_ID);
        when(loansApi.retrieveOneLoan(LOAN_ID, false, "repaymentSchedule,transactions", null, null)).thenReturn(loan);

        GetLoansLoanIdResponse result = client.getLoanWithScheduleAndTransactions(LOAN_ID);

        assertThat(result).isSameAs(loan);
        verify(loansApi).retrieveOneLoan(LOAN_ID, false, "repaymentSchedule,transactions", null, null);
    }

    @Test
    void getTransactionsPagePassesPagingThrough() {
        GetLoansLoanIdTransactionsResponse page = new GetLoansLoanIdTransactionsResponse();
        when(loanTransactionsApi.retrieveAllLoanTransactions(LOAN_ID, null, 2, 25, SORT)).thenReturn(page);

        GetLoansLoanIdTransactionsResponse result = client.getTransactionsPage(LOAN_ID, 2, 25, SORT);

        assertThat(result).isSameAs(page);
        verify(loanTransactionsApi).retrieveAllLoanTransactions(LOAN_ID, null, 2, 25, SORT);
    }

    @Test
    void getAllTransactionsFetchesFirstPageAtFetchAllSize() {
        GetLoansLoanIdTransactionsResponse page = new GetLoansLoanIdTransactionsResponse();
        when(loanTransactionsApi.retrieveAllLoanTransactions(
                LOAN_ID, null, 0, LoansQueryFineractClientImpl.FETCH_ALL_PAGE_SIZE, SORT)).thenReturn(page);

        GetLoansLoanIdTransactionsResponse result = client.getAllTransactions(LOAN_ID, SORT);

        assertThat(result).isSameAs(page);
        verify(loanTransactionsApi).retrieveAllLoanTransactions(
                LOAN_ID, null, 0, LoansQueryFineractClientImpl.FETCH_ALL_PAGE_SIZE, SORT);
    }

    @Test
    void getTransactionRetrievesOneTransaction() {
        GetLoansLoanIdTransactionsTransactionIdResponse transaction =
                new GetLoansLoanIdTransactionsTransactionIdResponse().id(TRANSACTION_ID);
        when(loanTransactionsApi.retrieveOneLoanTransaction(LOAN_ID, TRANSACTION_ID, null)).thenReturn(transaction);

        GetLoansLoanIdTransactionsTransactionIdResponse result = client.getTransaction(LOAN_ID, TRANSACTION_ID);

        assertThat(result).isSameAs(transaction);
        verify(loanTransactionsApi).retrieveOneLoanTransaction(LOAN_ID, TRANSACTION_ID, null);
    }

    @Test
    void getChargesRetrievesAllLoanCharges() {
        List<GetLoansLoanIdChargesChargeIdResponse> charges =
                List.of(new GetLoansLoanIdChargesChargeIdResponse().id(CHARGE_ID));
        when(loanChargesApi.retrieveAllLoanCharges(LOAN_ID)).thenReturn(charges);

        List<GetLoansLoanIdChargesChargeIdResponse> result = client.getCharges(LOAN_ID);

        assertThat(result).isSameAs(charges);
        verify(loanChargesApi).retrieveAllLoanCharges(LOAN_ID);
    }

    @Test
    void getChargeRetrievesOneLoanCharge() {
        GetLoansLoanIdChargesChargeIdResponse charge = new GetLoansLoanIdChargesChargeIdResponse().id(CHARGE_ID);
        when(loanChargesApi.retrieveOneLoanCharge(LOAN_ID, CHARGE_ID)).thenReturn(charge);

        GetLoansLoanIdChargesChargeIdResponse result = client.getCharge(LOAN_ID, CHARGE_ID);

        assertThat(result).isSameAs(charge);
        verify(loanChargesApi).retrieveOneLoanCharge(LOAN_ID, CHARGE_ID);
    }

    @Test
    void getGuarantorsRetrievesGuarantorDetails() {
        List<GuarantorData> guarantors = List.of(new GuarantorData().id(1L));
        when(guarantorsApi.retrieveGuarantorDetails(LOAN_ID)).thenReturn(guarantors);

        List<GuarantorData> result = client.getGuarantors(LOAN_ID);

        assertThat(result).isSameAs(guarantors);
        verify(guarantorsApi).retrieveGuarantorDetails(LOAN_ID);
    }

    @Test
    void listLoanProductsRetrievesAllLoanProducts() {
        List<GetLoanProductsResponse> products = List.of(new GetLoanProductsResponse().id(PRODUCT_ID));
        when(loanProductsApi.retrieveAllLoanProducts()).thenReturn(products);

        List<GetLoanProductsResponse> result = client.listLoanProducts();

        assertThat(result).isSameAs(products);
        verify(loanProductsApi).retrieveAllLoanProducts();
    }

    @Test
    void getLoanProductRetrievesOneLoanProduct() {
        GetLoanProductsProductIdResponse product = new GetLoanProductsProductIdResponse().id(PRODUCT_ID);
        when(loanProductsApi.retrieveOneLoanProduct(PRODUCT_ID)).thenReturn(product);

        GetLoanProductsProductIdResponse result = client.getLoanProduct(PRODUCT_ID);

        assertThat(result).isSameAs(product);
        verify(loanProductsApi).retrieveOneLoanProduct(PRODUCT_ID);
    }

    @Test
    void previewScheduleSetsFormattingFieldsAndCalculatesSchedule() {
        PostLoansRequest request = new PostLoansRequest()
                .clientId(CLIENT_ID)
                .productId(PRODUCT_ID)
                .principal(new BigDecimal("1000.00"))
                .numberOfRepayments(12)
                .expectedDisbursementDate("2026-01-15")
                .submittedOnDate("2026-01-10");
        PostLoansResponse response = new PostLoansResponse();
        ArgumentCaptor<PostLoansRequest> captor = ArgumentCaptor.forClass(PostLoansRequest.class);
        when(loansApi.calculateOrSubmitLoanApplication(captor.capture(), eq("calculateLoanSchedule")))
                .thenReturn(response);

        PostLoansResponse result = client.previewSchedule(request);

        assertThat(result).isSameAs(response);
        PostLoansRequest sent = captor.getValue();
        assertThat(sent.getLocale()).isEqualTo("en");
        assertThat(sent.getDateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(sent.getLoanType()).isEqualTo("individual");
        assertThat(sent.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(sent.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(sent.getPrincipal()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(sent.getNumberOfRepayments()).isEqualTo(12);
        assertThat(sent.getExpectedDisbursementDate()).isEqualTo("2026-01-15");
        assertThat(sent.getSubmittedOnDate()).isEqualTo("2026-01-10");
    }
}
