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
import java.util.UUID;
import java.util.List;
import org.apache.fineract.consumer.client.api.BeneficiariesCommandControllerApi;
import org.apache.fineract.consumer.client.api.SavingsQueryControllerApi;
import org.apache.fineract.consumer.client.api.TransfersCommandControllerApi;
import org.apache.fineract.consumer.client.api.TransfersQueryControllerApi;
import org.apache.fineract.consumer.client.model.BeneficiaryChallengeCommandData;
import org.apache.fineract.consumer.client.model.ConfirmAddBeneficiaryCommandRequest;
import org.apache.fineract.consumer.client.model.ConfirmTransferCommandRequest;
import org.apache.fineract.consumer.client.model.InitiateAddBeneficiaryCommandRequest;
import org.apache.fineract.consumer.client.model.InitiateTransferCommandRequest;
import org.apache.fineract.consumer.client.model.TransferChallengeCommandData;
import org.apache.fineract.consumer.client.model.TransferCommandData;
import org.apache.fineract.consumer.client.model.TransferQueryData;
import org.apache.fineract.consumer.client.model.TransferQueryResponse;
import org.apache.fineract.consumer.transfers.query.data.TransferDirection;
import org.apache.fineract.consumer.cucumber.clients.MailpitClient;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.FineractSeeder;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.transfers.command.data.TransferConstants;

public class TransfersSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-transfers-device";
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final BigDecimal DEPOSIT_AMOUNT = new BigDecimal("1000.00");
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("100.00");
    private static final String BENEFICIARY_NAME = "Cuke Transfer Beneficiary";
    private static final Integer FIRST_PAGE = 0;
    private static final Integer PAGE_SIZE = 20;

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final FineractSeeder fineractSeeder = new FineractSeeder();
    private final LoginHelper loginHelper = new LoginHelper();
    private final MailpitClient mailpit = new MailpitClient();

    private RegistrationHelper.BoundUserWithAccounts user;
    private String accessToken;
    private TransfersCommandControllerApi transfersApi;
    private SavingsQueryControllerApi savingsApi;
    private BeneficiariesCommandControllerApi beneficiariesApi;
    private FineractSeeder.SeededTransferTarget beneficiaryTarget;
    private long foreignSavingsId;
    private long foreignLoanId;
    private TransferCommandData transferResult;
    private TransferCommandData secondTransferResult;
    private int errorStatus;

    @Given("a logged-in customer with a funded savings account and a loan")
    public void loggedInCustomerWithFundedSavingsAndLoan() {
        user = registrationHelper.registerBoundUserWithAccounts();
        fineractSeeder.depositToSavings(user.savingsAccountId(), DEPOSIT_AMOUNT);
        accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        transfersApi = authenticatedClient(accessToken);
        savingsApi = ConsumerApiClientFactory.authenticated(
                SavingsQueryControllerApi.class, accessToken, DEVICE_FINGERPRINT);
        beneficiariesApi = ConsumerApiClientFactory.authenticated(
                BeneficiariesCommandControllerApi.class, accessToken, DEVICE_FINGERPRINT);
    }

    @When("I transfer money from my savings account to my loan")
    public void transferSavingsToLoan() {
        transferResult = confirmTransferToLoan(UUID.randomUUID().toString());
    }

    @Given("another client owns a savings account I registered as a beneficiary")
    public void anotherClientOwnsSavingsRegisteredAsBeneficiary() {
        beneficiaryTarget = fineractSeeder.seedTransferTarget();
        mailpit.deleteMessages(user.email());
        BeneficiaryChallengeCommandData challenge = beneficiariesApi.initiateAddBeneficiary(DEVICE_FINGERPRINT,
                new InitiateAddBeneficiaryCommandRequest()
                        .name(BENEFICIARY_NAME)
                        .officeName(beneficiaryTarget.officeName())
                        .accountNumber(beneficiaryTarget.savingsAccountNumber()));
        String otp = mailpit.waitForOtp(user.email());
        beneficiariesApi.confirmAddBeneficiary(DEVICE_FINGERPRINT,
                new ConfirmAddBeneficiaryCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp)
                        .name(BENEFICIARY_NAME)
                        .officeName(beneficiaryTarget.officeName())
                        .accountNumber(beneficiaryTarget.savingsAccountNumber()));
    }

    @When("I transfer money from my savings account to the beneficiary")
    public void transferSavingsToBeneficiary() {
        transferResult = confirmTransferToBeneficiary();
    }

    @When("I confirm the transfer twice with the same idempotency key")
    public void confirmTransferTwiceWithSameKey() {
        String idempotencyKey = UUID.randomUUID().toString();
        transferResult = confirmTransferToLoan(idempotencyKey);
        secondTransferResult = confirmTransferToLoan(idempotencyKey);
    }

    @When("I confirm the transfer twice with different idempotency keys")
    public void confirmTransferTwiceWithDifferentKeys() {
        transferResult = confirmTransferToLoan(UUID.randomUUID().toString());
        secondTransferResult = confirmTransferToLoan(UUID.randomUUID().toString());
    }

    @Then("both confirmations return the same transfer id")
    public void bothConfirmationsReturnSameTransferId() {
        assertThat(transferResult.getTransferId()).isNotNull();
        assertThat(secondTransferResult.getTransferId()).isEqualTo(transferResult.getTransferId());
    }

    @Then("the confirmations return different transfer ids")
    public void confirmationsReturnDifferentTransferIds() {
        assertThat(transferResult.getTransferId()).isNotNull();
        assertThat(secondTransferResult.getTransferId()).isNotEqualTo(transferResult.getTransferId());
    }

    @Then("my savings account was debited exactly once")
    public void savingsDebitedExactlyOnce() {
        assertThat(savingsApi.getSavingsAccount(user.savingsAccountId()).getBalance())
                .isEqualByComparingTo(DEPOSIT_AMOUNT.subtract(TRANSFER_AMOUNT));
    }

    @Then("my savings account was debited twice")
    public void savingsDebitedTwice() {
        assertThat(savingsApi.getSavingsAccount(user.savingsAccountId()).getBalance())
                .isEqualByComparingTo(DEPOSIT_AMOUNT.subtract(TRANSFER_AMOUNT).subtract(TRANSFER_AMOUNT));
    }

    private TransferCommandData confirmTransferToLoan(String idempotencyKey) {
        mailpit.deleteMessages(user.email());
        TransferChallengeCommandData challenge = transfersApi.initiateTransfer(DEVICE_FINGERPRINT,
                new InitiateTransferCommandRequest()
                        .fromAccountId(user.savingsAccountId())
                        .toAccountId(user.loanAccountId())
                        .toAccountType(TransferConstants.LOAN_TYPE_NAME)
                        .amount(TRANSFER_AMOUNT));
        String otp = mailpit.waitForOtp(user.email());
        return transfersApi.confirmTransfer(DEVICE_FINGERPRINT, idempotencyKey,
                new ConfirmTransferCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp)
                        .fromAccountId(user.savingsAccountId())
                        .toAccountId(user.loanAccountId())
                        .toAccountType(TransferConstants.LOAN_TYPE_NAME)
                        .amount(TRANSFER_AMOUNT));
    }

    private TransferCommandData confirmTransferToBeneficiary() {
        mailpit.deleteMessages(user.email());
        TransferChallengeCommandData challenge = transfersApi.initiateTransfer(DEVICE_FINGERPRINT,
                new InitiateTransferCommandRequest()
                        .fromAccountId(user.savingsAccountId())
                        .toAccountId(beneficiaryTarget.savingsAccountId())
                        .toAccountType(TransferConstants.SAVINGS_TYPE_NAME)
                        .amount(TRANSFER_AMOUNT));
        String otp = mailpit.waitForOtp(user.email());
        return transfersApi.confirmTransfer(DEVICE_FINGERPRINT, UUID.randomUUID().toString(),
                new ConfirmTransferCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp)
                        .fromAccountId(user.savingsAccountId())
                        .toAccountId(beneficiaryTarget.savingsAccountId())
                        .toAccountType(TransferConstants.SAVINGS_TYPE_NAME)
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

    @Given("another client owns a loan I can target")
    public void anotherClientOwnsLoan() {
        foreignLoanId = fineractSeeder.seedActiveClientWithAccounts().loanAccountId();
    }

    @When("I initiate a transfer to the other client's loan")
    public void initiateTransferToForeignLoan() {
        errorStatus = captureErrorStatus(() -> transfersApi.initiateTransfer(DEVICE_FINGERPRINT,
                new InitiateTransferCommandRequest()
                        .fromAccountId(user.savingsAccountId())
                        .toAccountId(foreignLoanId)
                        .toAccountType(TransferConstants.LOAN_TYPE_NAME)
                        .amount(TRANSFER_AMOUNT)));
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

    @Then("my transfer history contains the transfer as an outgoing entry")
    public void transferHistoryContainsOutgoingEntry() {
        TransferQueryData entry = transferHistory().stream()
                .filter(item -> transferResult.getTransferId().equals(item.getTransferId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected the completed transfer in the history"));
        assertThat(entry.getAmount()).isEqualByComparingTo(TRANSFER_AMOUNT);
        assertThat(entry.getDirection().getValue()).isEqualTo(TransferDirection.OUTGOING.name());
        assertThat(entry.getFromAccount()).isNotNull();
        assertThat(entry.getDate()).isNotNull();
    }

    @Then("my transfer history does not contain the transfer")
    public void transferHistoryExcludesTransfer() {
        assertThat(transferHistory())
                .extracting(TransferQueryData::getTransferId)
                .doesNotContain(transferResult.getTransferId());
    }

    @Then("my transfer history reports that no further page exists")
    public void transferHistoryReportsNoFurtherPage() {
        TransferQueryResponse page = transferHistoryPage();
        assertThat(page.getPage()).isEqualTo(FIRST_PAGE);
        assertThat(page.getSize()).isEqualTo(PAGE_SIZE);
        assertThat(page.getTotalElements()).isEqualTo(page.getContent().size());
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    private List<TransferQueryData> transferHistory() {
        return transferHistoryPage().getContent();
    }

    private TransferQueryResponse transferHistoryPage() {
        return ConsumerApiClientFactory
                .authenticated(TransfersQueryControllerApi.class, accessToken, DEVICE_FINGERPRINT)
                .listTransfers(null, null, FIRST_PAGE, PAGE_SIZE);
    }

    @When("I request my transfer history without a session")
    public void requestTransferHistoryWithoutSession() {
        errorStatus = captureErrorStatus(() -> ConsumerApiClientFactory
                .unauthenticated(TransfersQueryControllerApi.class)
                .listTransfers(null, null, FIRST_PAGE, PAGE_SIZE));
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
        return ConsumerApiClientFactory.authenticated(TransfersCommandControllerApi.class, bearerToken, DEVICE_FINGERPRINT);
    }

    private static TransfersCommandControllerApi unauthenticatedClient() {
        return ConsumerApiClientFactory.unauthenticated(TransfersCommandControllerApi.class);
    }
}
