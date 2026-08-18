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
import java.util.List;
import java.util.Objects;
import org.apache.fineract.consumer.client.api.SavingsQueryControllerApi;
import org.apache.fineract.consumer.client.model.SavingsAccountListItemQueryData;
import org.apache.fineract.consumer.client.model.SavingsAccountQueryData;
import org.apache.fineract.consumer.client.model.SavingsChargeQueryData;
import org.apache.fineract.consumer.client.model.SavingsTransactionQueryResponse;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.FineractSeeder;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;

public class SavingsSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-savings-device";
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("125.00");

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final FineractSeeder fineractSeeder = new FineractSeeder();
    private final LoginHelper loginHelper = new LoginHelper();

    private RegistrationHelper.BoundUserWithAccounts user;
    private SavingsQueryControllerApi savingsApi;
    private long foreignSavingsId;

    private List<SavingsAccountListItemQueryData> listResult;
    private SavingsAccountQueryData accountResult;
    private List<SavingsChargeQueryData> chargesResult;
    private SavingsTransactionQueryResponse transactionsResult;
    private int errorStatus;

    @Given("a logged-in savings customer with seeded accounts")
    public void loggedInSavingsCustomer() {
        user = registrationHelper.registerBoundUserWithAccounts();
        String accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        savingsApi = authenticatedClient(accessToken);
    }

    @When("I list my savings accounts")
    public void listSavings() {
        listResult = savingsApi.listSavingsAccounts();
    }

    @Then("the savings list contains my seeded savings account")
    public void listContainsSeededSavings() {
        assertThat(listResult).anyMatch(item -> Objects.equals(item.getId(), user.savingsAccountId()));
    }

    @When("I get my seeded savings account")
    public void getSeededSavings() {
        accountResult = savingsApi.getSavingsAccount(user.savingsAccountId());
    }

    @Then("my savings account details are returned")
    public void savingsDetailsReturned() {
        assertThat(accountResult).isNotNull();
        assertThat(accountResult.getId()).isEqualTo(user.savingsAccountId());
        assertThat(accountResult.getBalance()).isNotNull();
        assertThat(accountResult.getCurrency()).isNotBlank();
    }

    @Given("my savings account has a deposit")
    public void savingsAccountHasDeposit() {
        fineractSeeder.depositToSavings(user.savingsAccountId(), DEPOSIT_AMOUNT);
    }

    @When("I list my savings account charges")
    public void listSavingsCharges() {
        chargesResult = savingsApi.getSavingsCharges(user.savingsAccountId());
    }

    @When("I list my savings account transactions")
    public void listSavingsTransactions() {
        transactionsResult = savingsApi.searchSavingsTransactions(
                user.savingsAccountId(), null, null, null, null, null);
    }

    @Then("the savings amounts carry a currency")
    public void savingsAmountsCarryCurrency() {
        assertThat(accountResult.getCurrency()).isNotBlank();
        assertThat(chargesResult).allSatisfy(charge -> assertThat(charge.getCurrency()).isNotBlank());
        assertThat(transactionsResult.getContent()).isNotEmpty()
                .allSatisfy(transaction -> assertThat(transaction.getCurrency()).isNotBlank());
    }

    @When("I list savings accounts without a session")
    public void listSavingsWithoutSession() {
        errorStatus = captureErrorStatus(() -> unauthenticatedClient().listSavingsAccounts());
    }

    @Then("the savings request is rejected as unauthorized")
    public void savingsRejectedUnauthorized() {
        assertThat(errorStatus).isEqualTo(UNAUTHORIZED);
    }

    @Given("another client owns a savings account")
    public void anotherClientOwnsSavings() {
        foreignSavingsId = fineractSeeder.seedActiveClientWithAccounts().savingsAccountId();
    }

    @When("I get the other client's savings account")
    public void getForeignSavings() {
        errorStatus = captureErrorStatus(() -> savingsApi.getSavingsAccount(foreignSavingsId));
    }

    @Then("the savings request is denied as forbidden")
    public void savingsDeniedForbidden() {
        assertThat(errorStatus).isEqualTo(FORBIDDEN);
    }

    private static int captureErrorStatus(Runnable call) {
        try {
            call.run();
            throw new AssertionError("Expected the request to be rejected, but it succeeded");
        } catch (FeignException e) {
            return e.status();
        }
    }

    private static SavingsQueryControllerApi authenticatedClient(String bearerToken) {
        return ConsumerApiClientFactory.authenticated(SavingsQueryControllerApi.class, bearerToken, DEVICE_FINGERPRINT);
    }

    private static SavingsQueryControllerApi unauthenticatedClient() {
        return ConsumerApiClientFactory.unauthenticated(SavingsQueryControllerApi.class);
    }
}
