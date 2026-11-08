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
import java.util.List;
import java.util.UUID;
import org.apache.fineract.consumer.client.api.BeneficiariesCommandControllerApi;
import org.apache.fineract.consumer.client.api.ScheduledTransfersCommandControllerApi;
import org.apache.fineract.consumer.client.model.BeneficiaryChallengeCommandData;
import org.apache.fineract.consumer.client.model.ConfirmAddBeneficiaryCommandRequest;
import org.apache.fineract.consumer.client.model.ConfirmScheduledTransferCommandRequest;
import org.apache.fineract.consumer.client.model.InitiateAddBeneficiaryCommandRequest;
import org.apache.fineract.consumer.client.model.InitiateScheduledTransferCommandRequest;
import org.apache.fineract.consumer.client.model.ScheduledTransferChallengeCommandData;
import org.apache.fineract.consumer.client.model.ScheduledTransferCommandData;
import org.apache.fineract.consumer.cucumber.clients.MailpitClient;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.FineractSeeder;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetPageItemsStandingInstructionSwagger;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferConstants;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferFrequency;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferAccessDeniedException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferBeneficiaryLimitExceededException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferInvalidException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class ScheduledTransfersSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-scheduled-transfers-device";
    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("25.50");
    private static final BigDecimal TRANSFER_LIMIT = new BigDecimal("500.00");
    private static final BigDecimal ABOVE_LIMIT_AMOUNT = new BigDecimal("600.00");
    private static final String BENEFICIARY_NAME = "Cuke Scheduled Beneficiary";
    // A week out, so a clock difference between this JVM and the BFF cannot make the start date "today".
    private static final int START_IN_DAYS = 7;
    private static final int RUN_FOR_MONTHS = 3;
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final FineractSeeder fineractSeeder = new FineractSeeder();
    private final LoginHelper loginHelper = new LoginHelper();
    private final MailpitClient mailpit = new MailpitClient();

    private RegistrationHelper.BoundUserWithAccounts user;
    private ScheduledTransfersCommandControllerApi scheduledTransfersApi;
    private BeneficiariesCommandControllerApi beneficiariesApi;
    private long secondSavingsId;
    private FineractSeeder.SeededTransferTarget beneficiaryTarget;
    private long unregisteredSavingsId;
    private LocalDate startDate;
    private LocalDate endDate;
    private ScheduledTransferCommandData result;
    private ScheduledTransferCommandData secondResult;
    private FeignException lastError;

    @Given("a logged-in customer setting up scheduled transfers")
    public void loggedInCustomer() {
        user = registrationHelper.registerBoundUserWithAccounts();
        String accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        scheduledTransfersApi = ConsumerApiClientFactory.authenticated(
                ScheduledTransfersCommandControllerApi.class, accessToken, DEVICE_FINGERPRINT);
        beneficiariesApi = ConsumerApiClientFactory.authenticated(
                BeneficiariesCommandControllerApi.class, accessToken, DEVICE_FINGERPRINT);
        startDate = LocalDate.now().plusDays(START_IN_DAYS);
    }

    @Given("I own a second savings account for scheduled transfers")
    public void ownSecondSavingsAccount() {
        secondSavingsId = fineractSeeder.openAnotherSavingsAccount(user.savingsAccountId());
    }

    @Given("another client's savings account is my scheduled transfer beneficiary with a transfer limit")
    public void beneficiaryWithTransferLimit() {
        beneficiaryTarget = fineractSeeder.seedTransferTarget();
        mailpit.deleteMessages(user.email());
        BeneficiaryChallengeCommandData challenge = beneficiariesApi.initiateAddBeneficiary(DEVICE_FINGERPRINT,
                new InitiateAddBeneficiaryCommandRequest()
                        .name(BENEFICIARY_NAME)
                        .officeName(beneficiaryTarget.officeName())
                        .accountNumber(beneficiaryTarget.savingsAccountNumber())
                        .transferLimit(TRANSFER_LIMIT));
        String otp = mailpit.waitForOtp(user.email());
        beneficiariesApi.confirmAddBeneficiary(DEVICE_FINGERPRINT,
                new ConfirmAddBeneficiaryCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp)
                        .name(BENEFICIARY_NAME)
                        .officeName(beneficiaryTarget.officeName())
                        .accountNumber(beneficiaryTarget.savingsAccountNumber())
                        .transferLimit(TRANSFER_LIMIT));
    }

    @Given("another client owns a savings account that is not my scheduled transfer beneficiary")
    public void anotherClientsUnregisteredSavings() {
        unregisteredSavingsId = fineractSeeder.seedTransferTarget().savingsAccountId();
    }

    @When("I schedule a weekly transfer to my second savings account with an end date")
    public void scheduleWeeklyToSecondSavings() {
        endDate = startDate.plusMonths(RUN_FOR_MONTHS);
        result = schedule(secondSavingsId, TRANSFER_AMOUNT, ScheduledTransferFrequency.WEEKLY, endDate,
                UUID.randomUUID().toString());
    }

    @When("I schedule a monthly transfer within the limit to that beneficiary")
    public void scheduleMonthlyToBeneficiary() {
        result = schedule(beneficiaryTarget.savingsAccountId(), TRANSFER_AMOUNT, ScheduledTransferFrequency.MONTHLY,
                null, UUID.randomUUID().toString());
    }

    @When("I initiate a scheduled transfer above the limit to that beneficiary")
    public void initiateAboveLimit() {
        lastError = captureError(() -> scheduledTransfersApi.initiateScheduledTransfer(DEVICE_FINGERPRINT,
                initiateRequest(user.savingsAccountId(), beneficiaryTarget.savingsAccountId(), ABOVE_LIMIT_AMOUNT,
                        ScheduledTransferFrequency.MONTHLY, startDate, null)));
    }

    @When("I initiate a scheduled transfer to that unregistered account")
    public void initiateToUnregisteredAccount() {
        lastError = captureError(() -> scheduledTransfersApi.initiateScheduledTransfer(DEVICE_FINGERPRINT,
                initiateRequest(user.savingsAccountId(), unregisteredSavingsId, TRANSFER_AMOUNT,
                        ScheduledTransferFrequency.WEEKLY, startDate, null)));
    }

    @When("I initiate a scheduled transfer from that unregistered account to my savings account")
    public void initiateFromUnregisteredAccount() {
        lastError = captureError(() -> scheduledTransfersApi.initiateScheduledTransfer(DEVICE_FINGERPRINT,
                initiateRequest(unregisteredSavingsId, user.savingsAccountId(), TRANSFER_AMOUNT,
                        ScheduledTransferFrequency.WEEKLY, startDate, null)));
    }

    @When("I initiate a scheduled transfer to my second savings account starting yesterday")
    public void initiateStartingYesterday() {
        // Yesterday here is before the BFF's tomorrow even if the two clocks sit on different dates.
        lastError = captureError(() -> scheduledTransfersApi.initiateScheduledTransfer(DEVICE_FINGERPRINT,
                initiateRequest(user.savingsAccountId(), secondSavingsId, TRANSFER_AMOUNT,
                        ScheduledTransferFrequency.WEEKLY, LocalDate.now().minusDays(1), null)));
    }

    @When("I confirm the same scheduled transfer twice with the same idempotency key")
    public void confirmTwiceWithSameKey() {
        String idempotencyKey = UUID.randomUUID().toString();
        result = schedule(secondSavingsId, TRANSFER_AMOUNT, ScheduledTransferFrequency.WEEKLY, null, idempotencyKey);
        secondResult =
                schedule(secondSavingsId, TRANSFER_AMOUNT, ScheduledTransferFrequency.WEEKLY, null, idempotencyKey);
    }

    @When("I initiate a scheduled transfer without a session")
    public void initiateWithoutSession() {
        lastError = captureError(() -> ConsumerApiClientFactory
                .unauthenticated(ScheduledTransfersCommandControllerApi.class)
                .initiateScheduledTransfer(DEVICE_FINGERPRINT, initiateRequest(user.savingsAccountId(),
                        user.savingsAccountId() + 1, TRANSFER_AMOUNT, ScheduledTransferFrequency.WEEKLY, startDate,
                        null)));
    }

    @Then("the scheduled transfer is accepted with a public id")
    public void acceptedWithPublicId() {
        assertThat(result).isNotNull();
        assertThat(result.getPublicId()).isNotNull();
    }

    @Then("Fineract holds one weekly standing instruction for the scheduled transfer that stops after my end date")
    public void fineractHoldsWeeklyInstruction() {
        GetPageItemsStandingInstructionSwagger instruction = onlyInstructionFromMySavings();
        assertInstructionMatches(instruction, secondSavingsId, ScheduledTransferConstants.WEEKS_FREQUENCY);
        // Fineract's validTill is exclusive, so the day after the consumer's inclusive end date.
        assertThat(instruction.getValidTill()).isEqualTo(endDate.plusDays(1));
    }

    @Then("Fineract holds one monthly standing instruction for the scheduled transfer with no end date")
    public void fineractHoldsMonthlyInstruction() {
        GetPageItemsStandingInstructionSwagger instruction = onlyInstructionFromMySavings();
        assertInstructionMatches(instruction, beneficiaryTarget.savingsAccountId(),
                ScheduledTransferConstants.MONTHS_FREQUENCY);
        assertThat(instruction.getValidTill()).isNull();
    }

    @Then("both scheduled transfer confirmations return the same public id")
    public void bothConfirmationsReturnSamePublicId() {
        assertThat(result.getPublicId()).isNotNull();
        assertThat(secondResult.getPublicId()).isEqualTo(result.getPublicId());
    }

    @Then("Fineract holds exactly one standing instruction from my savings account")
    public void fineractHoldsExactlyOneInstruction() {
        assertThat(onlyInstructionFromMySavings().getName())
                .isEqualTo(ScheduledTransferConstants.INSTRUCTION_NAME_PREFIX + result.getPublicId());
    }

    @Then("the scheduled transfer request is rejected because it exceeds the beneficiary limit")
    public void rejectedAboveLimit() {
        assertError(FORBIDDEN, ScheduledTransferBeneficiaryLimitExceededException.CODE);
    }

    @Then("the scheduled transfer request is denied as forbidden")
    public void deniedForbidden() {
        assertError(FORBIDDEN, ScheduledTransferAccessDeniedException.CODE);
    }

    @Then("the scheduled transfer request is rejected as invalid")
    public void rejectedInvalid() {
        assertError(BAD_REQUEST, ScheduledTransferInvalidException.CODE);
    }

    @Then("the scheduled transfer request is rejected as unauthorized")
    public void rejectedUnauthorized() {
        assertThat(lastError).isNotNull();
        assertThat(lastError.status()).isEqualTo(UNAUTHORIZED);
    }

    private ScheduledTransferCommandData schedule(long toAccountId, BigDecimal amount,
            ScheduledTransferFrequency frequency, LocalDate end, String idempotencyKey) {
        mailpit.deleteMessages(user.email());
        ScheduledTransferChallengeCommandData challenge = scheduledTransfersApi.initiateScheduledTransfer(
                DEVICE_FINGERPRINT,
                initiateRequest(user.savingsAccountId(), toAccountId, amount, frequency, startDate, end));
        String otp = mailpit.waitForOtp(user.email());
        return scheduledTransfersApi.confirmScheduledTransfer(DEVICE_FINGERPRINT, idempotencyKey,
                new ConfirmScheduledTransferCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp)
                        .fromAccountId(user.savingsAccountId())
                        .toAccountId(toAccountId)
                        .amount(amount)
                        .frequency(ConfirmScheduledTransferCommandRequest.FrequencyEnum.fromValue(frequency.name()))
                        .startDate(startDate)
                        .endDate(end));
    }

    private static InitiateScheduledTransferCommandRequest initiateRequest(long fromAccountId, long toAccountId,
            BigDecimal amount, ScheduledTransferFrequency frequency, LocalDate start, LocalDate end) {
        return new InitiateScheduledTransferCommandRequest()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .frequency(InitiateScheduledTransferCommandRequest.FrequencyEnum.fromValue(frequency.name()))
                .startDate(start)
                .endDate(end);
    }

    private GetPageItemsStandingInstructionSwagger onlyInstructionFromMySavings() {
        List<GetPageItemsStandingInstructionSwagger> instructions =
                fineractSeeder.standingInstructionsFrom(user.savingsAccountId());
        assertThat(instructions).hasSize(1);
        return instructions.getFirst();
    }

    private void assertInstructionMatches(GetPageItemsStandingInstructionSwagger instruction, long toAccountId,
            String recurrenceFrequency) {
        assertThat(instruction.getName())
                .isEqualTo(ScheduledTransferConstants.INSTRUCTION_NAME_PREFIX + result.getPublicId());
        assertThat(instruction.getToAccount().getId()).isEqualTo(toAccountId);
        assertThat(instruction.getAmount()).isEqualByComparingTo(TRANSFER_AMOUNT);
        assertThat(instruction.getValidFrom()).isEqualTo(startDate);
        assertThat(instruction.getRecurrenceFrequency().getId()).isEqualTo(Integer.valueOf(recurrenceFrequency));
        assertThat(instruction.getRecurrenceInterval())
                .isEqualTo(Integer.valueOf(ScheduledTransferConstants.EVERY_PERIOD));
        assertThat(instruction.getPriority().getId())
                .isEqualTo(Integer.valueOf(ScheduledTransferConstants.LOW_PRIORITY));
        assertThat(instruction.getStatus().getId())
                .isEqualTo(Integer.valueOf(ScheduledTransferConstants.ACTIVE_STATUS));
        assertThat(instruction.getAllowPartialTransfer()).isFalse();
    }

    private void assertError(int status, String code) {
        assertThat(lastError).isNotNull();
        assertThat(lastError.status()).isEqualTo(status);
        assertThat(readCode(lastError.contentUTF8())).isEqualTo(code);
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
}
