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
import org.apache.fineract.consumer.client.ApiClient;
import org.apache.fineract.consumer.client.api.TransfersCommandControllerApi;
import org.apache.fineract.consumer.client.model.ConfirmTransferCommandRequest;
import org.apache.fineract.consumer.client.model.InitiateTransferCommandRequest;
import org.apache.fineract.consumer.client.model.TransferChallengeCommandData;
import org.apache.fineract.consumer.client.model.TransferCommandData;
import org.apache.fineract.consumer.cucumber.clients.MailpitClient;
import org.apache.fineract.consumer.cucumber.helpers.FineractSeeder;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.transfers.command.data.TransferConstants;

public class TransfersSteps {

    private static final String BFF_BASE_URL = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    private static final String DEVICE_FINGERPRINT = "cucumber-transfers-device";
    private static final String BEARER_AUTH = "bearerAuth";
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("100.00");

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final FineractSeeder fineractSeeder = new FineractSeeder();
    private final LoginHelper loginHelper = new LoginHelper();
    private final MailpitClient mailpit = new MailpitClient();

    private RegistrationHelper.BoundUserWithAccounts user;
    private TransfersCommandControllerApi transfersApi;
    private long foreignSavingsId;
    private TransferCommandData transferResult;
    private int errorStatus;

    @Given("a logged-in customer with a funded savings account and a loan")
    public void loggedInCustomerWithFundedSavingsAndLoan() {
        user = registrationHelper.registerBoundUserWithAccounts();
        fineractSeeder.depositToSavings(user.savingsAccountId(), DEPOSIT_AMOUNT);
        String accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        transfersApi = authenticatedClient(accessToken);
    }

    @When("I transfer money from my savings account to my loan")
    public void transferSavingsToLoan() {
        mailpit.deleteMessages(user.email());
        TransferChallengeCommandData challenge = transfersApi.initiateTransfer(DEVICE_FINGERPRINT,
                new InitiateTransferCommandRequest()
                        .fromAccountId(user.savingsAccountId())
                        .toAccountId(user.loanAccountId())
                        .toAccountType(TransferConstants.LOAN_TYPE_NAME)
                        .amount(TRANSFER_AMOUNT));
        String otp = mailpit.waitForOtp(user.email());
        transferResult = transfersApi.confirmTransfer(DEVICE_FINGERPRINT,
                new ConfirmTransferCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp)
                        .fromAccountId(user.savingsAccountId())
                        .toAccountId(user.loanAccountId())
                        .toAccountType(TransferConstants.LOAN_TYPE_NAME)
                        .amount(TRANSFER_AMOUNT));
    }

    @Then("the transfer is accepted with a transfer id")
    public void transferAccepted() {
        assertThat(transferResult).isNotNull();
        assertThat(transferResult.getTransferId()).isNotNull();
        assertThat(transferResult.getFromAccountId()).isEqualTo(user.savingsAccountId());
        assertThat(transferResult.getToAccountId()).isEqualTo(user.loanAccountId());
        assertThat(transferResult.getAmount()).isEqualByComparingTo(TRANSFER_AMOUNT);
    }

    @When("I initiate a transfer without a session")
    public void initiateTransferWithoutSession() {
        errorStatus = captureErrorStatus(() -> unauthenticatedClient().initiateTransfer(DEVICE_FINGERPRINT,
                new InitiateTransferCommandRequest()
                        .fromAccountId(user.savingsAccountId())
                        .toAccountId(user.loanAccountId())
                        .toAccountType(TransferConstants.LOAN_TYPE_NAME)
                        .amount(TRANSFER_AMOUNT)));
    }

    @Then("the transfer request is rejected as unauthorized")
    public void transferRejectedUnauthorized() {
        assertThat(errorStatus).isEqualTo(UNAUTHORIZED);
    }

    @Given("another client owns a savings account I can target")
    public void anotherClientOwnsSavings() {
        foreignSavingsId = fineractSeeder.seedActiveClientWithAccounts().savingsAccountId();
    }

    @When("I initiate a transfer from the other client's savings account")
    public void initiateTransferFromForeignSavings() {
        errorStatus = captureErrorStatus(() -> transfersApi.initiateTransfer(DEVICE_FINGERPRINT,
                new InitiateTransferCommandRequest()
                        .fromAccountId(foreignSavingsId)
                        .toAccountId(user.savingsAccountId())
                        .toAccountType(TransferConstants.SAVINGS_TYPE_NAME)
                        .amount(TRANSFER_AMOUNT)));
    }

    @Then("the transfer request is denied as forbidden")
    public void transferDeniedForbidden() {
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

    private static TransfersCommandControllerApi authenticatedClient(String bearerToken) {
        ApiClient apiClient = new ApiClient(BEARER_AUTH);
        apiClient.setBasePath(BFF_BASE_URL);
        apiClient.setBearerToken(bearerToken);
        return apiClient.buildClient(TransfersCommandControllerApi.class);
    }

    private static TransfersCommandControllerApi unauthenticatedClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(BFF_BASE_URL);
        return apiClient.buildClient(TransfersCommandControllerApi.class);
    }
}
