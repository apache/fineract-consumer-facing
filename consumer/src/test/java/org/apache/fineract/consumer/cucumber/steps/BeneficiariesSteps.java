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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.client.api.BeneficiariesCommandControllerApi;
import org.apache.fineract.consumer.client.api.BeneficiariesQueryControllerApi;
import org.apache.fineract.consumer.client.api.TransfersCommandControllerApi;
import org.apache.fineract.consumer.client.model.BeneficiaryChallengeCommandData;
import org.apache.fineract.consumer.client.model.BeneficiaryCommandData;
import org.apache.fineract.consumer.client.model.BeneficiaryQueryData;
import org.apache.fineract.consumer.client.model.ConfirmAddBeneficiaryCommandRequest;
import org.apache.fineract.consumer.client.model.ConfirmTransferCommandRequest;
import org.apache.fineract.consumer.client.model.InitiateAddBeneficiaryCommandRequest;
import org.apache.fineract.consumer.client.model.InitiateTransferCommandRequest;
import org.apache.fineract.consumer.client.model.TransferChallengeCommandData;
import org.apache.fineract.consumer.client.model.TransferCommandData;
import org.apache.fineract.consumer.cucumber.clients.MailpitClient;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.FineractSeeder;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.infrastructure.exception.HttpMessageNotReadableExceptionHandler;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.apache.fineract.consumer.transfers.command.data.TransferConstants;
import org.apache.fineract.consumer.transfers.command.exception.TransferAccessDeniedException;
import org.apache.fineract.consumer.transfers.command.exception.TransferBeneficiaryLimitExceededException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class BeneficiariesSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-beneficiaries-device";
    private static final int BAD_REQUEST = 400;
    private static final int FORBIDDEN = 403;
    private static final String INITIATE_ADD_PATH = "/api/v1/beneficiaries/initiate";
    private static final String BENEFICIARY_NAME = "Cuke Beneficiary";
    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal TRANSFER_LIMIT = new BigDecimal("500.00");
    private static final BigDecimal WITHIN_LIMIT_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal ABOVE_LIMIT_AMOUNT = new BigDecimal("600.00");
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    // Raw HTTP client for requests the typed generated client cannot represent (e.g. a bogus enum value).
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final FineractSeeder fineractSeeder = new FineractSeeder();
    private final LoginHelper loginHelper = new LoginHelper();
    private final MailpitClient mailpit = new MailpitClient();

    private RegistrationHelper.BoundUserWithAccounts user;
    private FineractSeeder.SeededTransferTarget target;
    private BeneficiariesCommandControllerApi beneficiariesCommandApi;
    private BeneficiariesQueryControllerApi beneficiariesQueryApi;
    private TransfersCommandControllerApi transfersApi;
    private BeneficiaryCommandData addedBeneficiary;
    private TransferCommandData transferResult;
    private FeignException lastError;
    private String accessToken;
    private HttpResponse<String> rawResponse;

    @Given("a logged-in customer with a funded savings account")
    public void loggedInCustomerWithFundedSavings() {
        user = registrationHelper.registerBoundUserWithAccounts();
        fineractSeeder.depositToSavings(user.savingsAccountId(), DEPOSIT_AMOUNT);
        accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        beneficiariesCommandApi = authenticatedCommandClient(accessToken);
        beneficiariesQueryApi = authenticatedQueryClient(accessToken);
        transfersApi = authenticatedTransfersClient(accessToken);
    }

    @Given("another client owns a savings account I can register as a beneficiary")
    public void anotherClientOwnsTargetSavings() {
        target = fineractSeeder.seedTransferTarget();
    }

    @When("I register the target account as a beneficiary with a transfer limit")
    public void registerTargetAsBeneficiary() {
        mailpit.deleteMessages(user.email());
        BeneficiaryChallengeCommandData challenge = beneficiariesCommandApi.initiateAddBeneficiary(
                DEVICE_FINGERPRINT,
                new InitiateAddBeneficiaryCommandRequest()
                        .name(BENEFICIARY_NAME)
                        .officeName(target.officeName())
                        .accountNumber(target.savingsAccountNumber())
                        .accountType(InitiateAddBeneficiaryCommandRequest.AccountTypeEnum.SAVINGS)
                        .transferLimit(TRANSFER_LIMIT));
        String otp = mailpit.waitForOtp(user.email());
        addedBeneficiary = beneficiariesCommandApi.confirmAddBeneficiary(
                DEVICE_FINGERPRINT,
                new ConfirmAddBeneficiaryCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp)
                        .name(BENEFICIARY_NAME)
                        .officeName(target.officeName())
                        .accountNumber(target.savingsAccountNumber())
                        .accountType(ConfirmAddBeneficiaryCommandRequest.AccountTypeEnum.SAVINGS)
                        .transferLimit(TRANSFER_LIMIT));
        assertThat(addedBeneficiary.getPublicId()).isNotNull();
    }

    @Then("the beneficiary appears in my beneficiary list")
    public void beneficiaryAppearsInList() {
        List<BeneficiaryQueryData> beneficiaries = beneficiariesQueryApi.listBeneficiaries();
        assertThat(beneficiaries).anySatisfy(beneficiary -> {
            assertThat(beneficiary.getPublicId()).isEqualTo(addedBeneficiary.getPublicId());
            assertThat(beneficiary.getName()).isEqualTo(BENEFICIARY_NAME);
            assertThat(beneficiary.getTransferLimit()).isEqualByComparingTo(TRANSFER_LIMIT);
        });
    }

    @When("I initiate adding a beneficiary with account type {string}")
    public void initiateAddBeneficiaryWithAccountType(String accountType) throws Exception {
        String body = JSON.writeValueAsString(Map.of(
                "name", BENEFICIARY_NAME,
                "officeName", target.officeName(),
                "accountNumber", target.savingsAccountNumber(),
                "accountType", accountType,
                "transferLimit", TRANSFER_LIMIT));
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(ConsumerApiClientFactory.BFF_BASE_URL + INITIATE_ADD_PATH))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.COOKIE,
                        AuthenticationConstants.ACCESS_TOKEN_COOKIE_NAME + "=" + accessToken)
                .header(ConsumerHeaders.DEVICE_FINGERPRINT, DEVICE_FINGERPRINT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        rawResponse = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Then("the beneficiary request is rejected as malformed")
    public void beneficiaryRequestRejectedAsMalformed() {
        assertThat(rawResponse.statusCode()).isEqualTo(BAD_REQUEST);
        assertThat(readCode(rawResponse.body())).isEqualTo(HttpMessageNotReadableExceptionHandler.CODE);
    }

    @When("I transfer an amount within the limit to the target account")
    public void transferWithinLimitToTarget() {
        mailpit.deleteMessages(user.email());
        TransferChallengeCommandData challenge = transfersApi.initiateTransfer(DEVICE_FINGERPRINT,
                transferRequest(WITHIN_LIMIT_AMOUNT));
        String otp = mailpit.waitForOtp(user.email());
        transferResult = transfersApi.confirmTransfer(DEVICE_FINGERPRINT,
                new ConfirmTransferCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp)
                        .fromAccountId(user.savingsAccountId())
                        .toAccountId(target.savingsAccountId())
                        .toAccountType(TransferConstants.SAVINGS_TYPE_NAME)
                        .amount(WITHIN_LIMIT_AMOUNT));
    }

    @Then("the beneficiary transfer is accepted")
    public void beneficiaryTransferAccepted() {
        assertThat(transferResult).isNotNull();
        assertThat(transferResult.getTransferId()).isNotNull();
        assertThat(transferResult.getToAccountId()).isEqualTo(target.savingsAccountId());
        assertThat(transferResult.getAmount()).isEqualByComparingTo(WITHIN_LIMIT_AMOUNT);
    }

    @When("I initiate a transfer above the limit to the target account")
    public void initiateTransferAboveLimit() {
        lastError = captureError(() -> transfersApi.initiateTransfer(DEVICE_FINGERPRINT,
                transferRequest(ABOVE_LIMIT_AMOUNT)));
    }

    @Then("the transfer is rejected for exceeding the beneficiary limit")
    public void rejectedForExceedingLimit() {
        assertThat(lastError.status()).isEqualTo(FORBIDDEN);
        assertThat(readCode(lastError.contentUTF8()))
                .isEqualTo(TransferBeneficiaryLimitExceededException.CODE);
    }

    @When("I initiate a transfer to the target account")
    public void initiateTransferToTarget() {
        lastError = captureError(() -> transfersApi.initiateTransfer(DEVICE_FINGERPRINT,
                transferRequest(WITHIN_LIMIT_AMOUNT)));
    }

    @Then("the transfer is rejected as a forbidden destination")
    public void rejectedAsForbiddenDestination() {
        assertThat(lastError.status()).isEqualTo(FORBIDDEN);
        assertThat(readCode(lastError.contentUTF8())).isEqualTo(TransferAccessDeniedException.CODE);
    }

    @When("I delete the beneficiary")
    public void deleteBeneficiary() {
        beneficiariesCommandApi.deleteBeneficiary(addedBeneficiary.getPublicId());
    }

    @Then("my beneficiary list is empty")
    public void beneficiaryListIsEmpty() {
        assertThat(beneficiariesQueryApi.listBeneficiaries()).isEmpty();
    }

    private InitiateTransferCommandRequest transferRequest(BigDecimal amount) {
        return new InitiateTransferCommandRequest()
                .fromAccountId(user.savingsAccountId())
                .toAccountId(target.savingsAccountId())
                .toAccountType(TransferConstants.SAVINGS_TYPE_NAME)
                .amount(amount);
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

    private static BeneficiariesCommandControllerApi authenticatedCommandClient(String bearerToken) {
        return ConsumerApiClientFactory.authenticated(BeneficiariesCommandControllerApi.class, bearerToken, DEVICE_FINGERPRINT);
    }

    private static BeneficiariesQueryControllerApi authenticatedQueryClient(String bearerToken) {
        return ConsumerApiClientFactory.authenticated(BeneficiariesQueryControllerApi.class, bearerToken, DEVICE_FINGERPRINT);
    }

    private static TransfersCommandControllerApi authenticatedTransfersClient(String bearerToken) {
        return ConsumerApiClientFactory.authenticated(TransfersCommandControllerApi.class, bearerToken, DEVICE_FINGERPRINT);
    }
}
