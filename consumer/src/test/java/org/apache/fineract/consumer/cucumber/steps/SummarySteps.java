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

package org.apache.fineract.consumer.cucumber.steps;

import static org.assertj.core.api.Assertions.assertThat;

import feign.FeignException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.util.Objects;
import org.apache.fineract.consumer.client.api.SummaryQueryControllerApi;
import org.apache.fineract.consumer.client.model.AccountsSummaryQueryData;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.FineractSeeder;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;

public class SummarySteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-summary-device";
    private static final int UNAUTHORIZED = 401;
    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("1000.00");

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final FineractSeeder fineractSeeder = new FineractSeeder();
    private final LoginHelper loginHelper = new LoginHelper();

    private RegistrationHelper.BoundUserWithAccounts user;
    private SummaryQueryControllerApi summaryApi;

    private AccountsSummaryQueryData summaryResult;
    private int errorStatus;

    @Given("a logged-in summary customer with seeded accounts")
    public void loggedInSummaryCustomer() {
        user = registrationHelper.registerBoundUserWithAccounts();
        fineractSeeder.depositToSavings(user.savingsAccountId(), DEPOSIT_AMOUNT);
        String accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        summaryApi = ConsumerApiClientFactory.authenticated(
                SummaryQueryControllerApi.class, accessToken, DEVICE_FINGERPRINT);
    }

    @When("I get my accounts summary")
    public void getAccountsSummary() {
        summaryResult = summaryApi.getAccountsSummary();
    }

    @Then("the summary contains my seeded savings account with a balance")
    public void summaryContainsSeededSavings() {
        assertThat(summaryResult).isNotNull();
        assertThat(summaryResult.getSavings())
                .anyMatch(item -> Objects.equals(item.getId(), user.savingsAccountId())
                        && item.getAccountBalance() != null);
    }

    @Then("the summary contains my seeded loan account with an outstanding balance")
    public void summaryContainsSeededLoan() {
        assertThat(summaryResult.getLoans())
                .anyMatch(item -> Objects.equals(item.getId(), user.loanAccountId())
                        && item.getLoanBalance() != null);
    }

    @When("I get the accounts summary without a session")
    public void getAccountsSummaryWithoutSession() {
        try {
            ConsumerApiClientFactory.unauthenticated(SummaryQueryControllerApi.class).getAccountsSummary();
            throw new AssertionError("Expected the request to be rejected, but it succeeded");
        } catch (FeignException e) {
            errorStatus = e.status();
        }
    }

    @Then("the summary request is rejected as unauthorized")
    public void summaryRejectedUnauthorized() {
        assertThat(errorStatus).isEqualTo(UNAUTHORIZED);
    }
}
