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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.apache.fineract.consumer.client.api.LoansCommandControllerApi;
import org.apache.fineract.consumer.client.api.LoansQueryControllerApi;
import org.apache.fineract.consumer.client.model.LoanAccountListItemQueryData;
import org.apache.fineract.consumer.client.model.LoanAccountQueryData;
import org.apache.fineract.consumer.client.model.LoanApplicationCommandData;
import org.apache.fineract.consumer.client.model.SubmitLoanApplicationCommandRequest;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.FineractSeeder;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.infrastructure.exception.MissingRequestHeaderExceptionHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class LoansSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-loans-device";
    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final LocalDate FIXED_LOCAL_DATE = LocalDate.parse(FineractSeeder.FIXED_DATE,
            DateTimeFormatter.ofPattern(FineractSeeder.DATE_FORMAT, Locale.ENGLISH));
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final FineractSeeder fineractSeeder = new FineractSeeder();
    private final LoginHelper loginHelper = new LoginHelper();

    private RegistrationHelper.BoundUserWithAccounts user;
    private LoansQueryControllerApi loansApi;
    private LoansCommandControllerApi loansCommandApi;
    private long foreignLoanId;

    private List<LoanAccountListItemQueryData> listResult;
    private LoanAccountQueryData accountResult;
    private LoanApplicationCommandData firstSubmission;
    private LoanApplicationCommandData secondSubmission;
    private int loanCountBeforeSubmit;
    private int errorStatus;
    private FeignException lastError;

    @Given("a logged-in loans customer with seeded accounts")
    public void loggedInLoansCustomer() {
        user = registrationHelper.registerBoundUserWithAccounts();
        String accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        loansApi = authenticatedClient(accessToken);
        loansCommandApi = ConsumerApiClientFactory.authenticated(
                LoansCommandControllerApi.class, accessToken, DEVICE_FINGERPRINT);
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

    @When("I submit a loan application twice with the same idempotency key")
    public void submitLoanApplicationTwiceWithSameKey() {
        loanCountBeforeSubmit = loansApi.listLoanAccounts().size();
        String idempotencyKey = UUID.randomUUID().toString();
        firstSubmission = loansCommandApi.submitLoanApplication(idempotencyKey, loanApplicationRequest());
        secondSubmission = loansCommandApi.submitLoanApplication(idempotencyKey, loanApplicationRequest());
    }

    @Then("both submissions return the same loan id")
    public void bothSubmissionsReturnSameLoanId() {
        assertThat(firstSubmission.getLoanId()).isNotNull();
        assertThat(secondSubmission.getLoanId()).isEqualTo(firstSubmission.getLoanId());
    }

    @Then("I have exactly one more loan account")
    public void exactlyOneMoreLoanAccount() {
        assertThat(loansApi.listLoanAccounts()).hasSize(loanCountBeforeSubmit + 1);
    }

    @When("I submit a loan application without an idempotency key")
    public void submitLoanApplicationWithoutKey() {
        lastError = captureError(() -> loansCommandApi.submitLoanApplication(null, loanApplicationRequest()));
    }

    @Then("the loan submission is rejected for the missing idempotency key")
    public void loanSubmissionRejectedForMissingKey() {
        assertThat(lastError.status()).isEqualTo(BAD_REQUEST);
        assertThat(readCode(lastError.contentUTF8())).isEqualTo(MissingRequestHeaderExceptionHandler.CODE);
    }

    private SubmitLoanApplicationCommandRequest loanApplicationRequest() {
        return new SubmitLoanApplicationCommandRequest()
                .productId(fineractSeeder.loanProductId())
                .principal(new BigDecimal("10000"))
                .loanTermFrequency(5)
                .loanTermFrequencyType(2)
                .numberOfRepayments(5)
                .repaymentEvery(1)
                .repaymentFrequencyType(2)
                .interestRatePerPeriod(new BigDecimal("2"))
                .amortizationType(1)
                .interestType(1)
                .interestCalculationPeriodType(1)
                .transactionProcessingStrategyCode(FineractSeeder.TRANSACTION_PROCESSING_STRATEGY)
                .expectedDisbursementDate(FIXED_LOCAL_DATE)
                .submittedOnDate(FIXED_LOCAL_DATE);
    }

    private static int captureErrorStatus(Runnable call) {
        return captureError(call).status();
    }

    private static FeignException captureError(Runnable call) {
        try {
            call.run();
            throw new AssertionError("Expected the request to be rejected, but it succeeded");
        } catch (FeignException e) {
            return e;
        }
    }

    private static String readCode(String body) {
        try {
            return JSON.readTree(body).path("code").asString();
        } catch (Exception e) {
            throw new IllegalStateException("could not parse error response body: " + body, e);
        }
    }

    private static LoansQueryControllerApi authenticatedClient(String bearerToken) {
        return ConsumerApiClientFactory.authenticated(LoansQueryControllerApi.class, bearerToken, DEVICE_FINGERPRINT);
    }

    private static LoansQueryControllerApi unauthenticatedClient() {
        return ConsumerApiClientFactory.unauthenticated(LoansQueryControllerApi.class);
    }
}
