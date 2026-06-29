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

package org.apache.fineract.consumer.cucumber.steps;

import static org.assertj.core.api.Assertions.assertThat;

import feign.FeignException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import java.util.Objects;
import org.apache.fineract.consumer.client.ApiClient;
import org.apache.fineract.consumer.client.api.LoansCommandControllerApi;
import org.apache.fineract.consumer.client.api.LoansQueryControllerApi;
import org.apache.fineract.consumer.client.model.LoanAccountListItemQueryData;
import org.apache.fineract.consumer.client.model.LoanAccountQueryData;
import org.apache.fineract.consumer.cucumber.helpers.FineractSeeder;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;

public class LoansSteps {

    private static final String BFF_BASE_URL = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    private static final String DEVICE_FINGERPRINT = "cucumber-loans-device";
    private static final String BEARER_AUTH = "bearerAuth";
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final long ARBITRARY_CHARGE_ID = 1L;

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final FineractSeeder fineractSeeder = new FineractSeeder();
    private final LoginHelper loginHelper = new LoginHelper();

    private RegistrationHelper.BoundUserWithAccounts user;
    private LoansQueryControllerApi loansApi;
    private LoansCommandControllerApi loansCommandApi;
    private long foreignLoanId;

    private List<LoanAccountListItemQueryData> listResult;
    private LoanAccountQueryData accountResult;
    private int errorStatus;

    @Given("a logged-in loans customer with seeded accounts")
    public void loggedInLoansCustomer() {
        user = registrationHelper.registerBoundUserWithAccounts();
        String accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        loansApi = authenticatedClient(accessToken);
        loansCommandApi = authenticatedCommandClient(accessToken);
    }

    @When("I list my loan accounts")
    public void listLoans() {
        listResult = loansApi.listLoanAccounts();
    }

    @Then("the loan list contains my seeded loan account")
    public void listContainsSeededLoan() {
        assertThat(listResult).anyMatch(item -> Objects.equals(item.getId(), user.loanAccountId()));
    }

    @When("I get my seeded loan account")
    public void getSeededLoan() {
        accountResult = loansApi.getLoanAccount(user.loanAccountId());
    }

    @Then("my loan account details are returned")
    public void loanDetailsReturned() {
        assertThat(accountResult).isNotNull();
        assertThat(accountResult.getId()).isEqualTo(user.loanAccountId());
        assertThat(accountResult.getTotalOutstanding()).isNotNull();
    }

    @When("I list loan accounts without a session")
    public void listLoansWithoutSession() {
        errorStatus = captureErrorStatus(() -> unauthenticatedClient().listLoanAccounts());
    }

    @Then("the loan request is rejected as unauthorized")
    public void loanRejectedUnauthorized() {
        assertThat(errorStatus).isEqualTo(UNAUTHORIZED);
    }

    @Given("another client owns a loan account")
    public void anotherClientOwnsLoan() {
        foreignLoanId = fineractSeeder.seedActiveClientWithAccounts().loanAccountId();
    }

    @When("I get the other client's loan account")
    public void getForeignLoan() {
        errorStatus = captureErrorStatus(() -> loansApi.getLoanAccount(foreignLoanId));
    }

    @Then("the loan request is denied as forbidden")
    public void loanDeniedForbidden() {
        assertThat(errorStatus).isEqualTo(FORBIDDEN);
    }

    @When("I initiate a loan charge payment without a session")
    public void initiateChargePaymentWithoutSession() {
        errorStatus = captureErrorStatus(() -> unauthenticatedCommandClient().initiateLoanChargePayment(
                DEVICE_FINGERPRINT, user.loanAccountId(), ARBITRARY_CHARGE_ID));
    }

    @When("I initiate a loan charge payment on the other client's loan account")
    public void initiateChargePaymentOnForeignLoan() {
        errorStatus = captureErrorStatus(() -> loansCommandApi.initiateLoanChargePayment(
                DEVICE_FINGERPRINT, foreignLoanId, ARBITRARY_CHARGE_ID));
    }

    private static int captureErrorStatus(Runnable call) {
        try {
            call.run();
            throw new AssertionError("Expected the request to be rejected, but it succeeded");
        } catch (FeignException e) {
            return e.status();
        }
    }

    private static LoansQueryControllerApi authenticatedClient(String bearerToken) {
        ApiClient apiClient = new ApiClient(BEARER_AUTH);
        apiClient.setBasePath(BFF_BASE_URL);
        apiClient.setBearerToken(bearerToken);
        return apiClient.buildClient(LoansQueryControllerApi.class);
    }

    private static LoansQueryControllerApi unauthenticatedClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(BFF_BASE_URL);
        return apiClient.buildClient(LoansQueryControllerApi.class);
    }

    private static LoansCommandControllerApi authenticatedCommandClient(String bearerToken) {
        ApiClient apiClient = new ApiClient(BEARER_AUTH);
        apiClient.setBasePath(BFF_BASE_URL);
        apiClient.setBearerToken(bearerToken);
        return apiClient.buildClient(LoansCommandControllerApi.class);
    }

    private static LoansCommandControllerApi unauthenticatedCommandClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(BFF_BASE_URL);
        return apiClient.buildClient(LoansCommandControllerApi.class);
    }
}
