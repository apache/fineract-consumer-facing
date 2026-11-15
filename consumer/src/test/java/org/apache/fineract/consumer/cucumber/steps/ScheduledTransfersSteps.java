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
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryLimitBelowScheduledTransferException;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryScheduledTransferActiveException;
import org.apache.fineract.consumer.client.api.BeneficiariesCommandControllerApi;
import org.apache.fineract.consumer.client.api.BeneficiariesQueryControllerApi;
import org.apache.fineract.consumer.client.api.SavingsQueryControllerApi;
import org.apache.fineract.consumer.client.api.ScheduledTransfersCommandControllerApi;
import org.apache.fineract.consumer.client.api.ScheduledTransfersQueryControllerApi;
import org.apache.fineract.consumer.client.model.BeneficiaryChallengeCommandData;
import org.apache.fineract.consumer.client.model.BeneficiaryCommandData;
import org.apache.fineract.consumer.client.model.BeneficiaryQueryData;
import org.apache.fineract.consumer.client.model.ConfirmAddBeneficiaryCommandRequest;
import org.apache.fineract.consumer.client.model.ConfirmCancelScheduledTransferCommandRequest;
import org.apache.fineract.consumer.client.model.ConfirmScheduledTransferCommandRequest;
import org.apache.fineract.consumer.client.model.ConfirmUpdateBeneficiaryCommandRequest;
import org.apache.fineract.consumer.client.model.InitiateAddBeneficiaryCommandRequest;
import org.apache.fineract.consumer.client.model.InitiateUpdateBeneficiaryCommandRequest;
import org.apache.fineract.consumer.client.model.InitiateScheduledTransferCommandRequest;
import org.apache.fineract.consumer.client.model.ScheduledTransferChallengeCommandData;
import org.apache.fineract.consumer.client.model.ScheduledTransferCommandData;
import org.apache.fineract.consumer.client.model.ScheduledTransferQueryData;
import org.apache.fineract.consumer.client.model.ScheduledTransferRunQueryData;
import org.apache.fineract.consumer.cucumber.clients.MailpitClient;
import org.apache.fineract.consumer.cucumber.helpers.BeneficiaryRowLock;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.FineractBusinessDate;
import org.apache.fineract.consumer.cucumber.helpers.FineractJobRunner;
import org.apache.fineract.consumer.cucumber.helpers.FineractSeeder;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetPageItemsStandingInstructionSwagger;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferConstants;
import org.apache.fineract.consumer.scheduledtransfers.command.data.ScheduledTransferFrequency;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferAccessDeniedException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferBeneficiaryLimitExceededException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferInvalidException;
import org.apache.fineract.consumer.scheduledtransfers.command.exception.ScheduledTransferNotFoundException;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunStatus;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryAccessDeniedException;
import org.apache.fineract.consumer.scheduledtransfers.query.exception.ScheduledTransferQueryNotFoundException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class ScheduledTransfersSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-scheduled-transfers-device";
    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int NOT_FOUND = 404;
    private static final int CONFLICT = 409;
    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("25.50");
    private static final BigDecimal TRANSFER_LIMIT = new BigDecimal("500.00");
    private static final BigDecimal BELOW_TRANSFER_AMOUNT = new BigDecimal("20.00");
    private static final BigDecimal RAISED_TRANSFER_LIMIT = new BigDecimal("750.00");
    private static final BigDecimal ABOVE_LIMIT_AMOUNT = new BigDecimal("600.00");
    private static final BigDecimal FUNDED_BALANCE = new BigDecimal("100.00");
    private static final BigDecimal UNAFFORDABLE_AMOUNT = new BigDecimal("1000.00");
    private static final String BENEFICIARY_NAME = "Cuke Scheduled Beneficiary";
    // A week out, so a clock difference between this JVM and Fineract cannot make the start date "today".
    private static final int START_IN_DAYS = 7;
    // Two days out, so a test clock up to a day behind Fineract's still yields a start date the BFF accepts.
    private static final int RUN_START_IN_DAYS = 2;
    private static final int RUN_FOR_MONTHS = 3;
    private static final int LONGEST_MONTH_DAYS = 31;
    private static final String DELETED_NAME_SUFFIX = "_deleted_";
    // BusinessDateFineractClientImpl caches Fineract's today for 30 seconds; this leaves room for one refresh.
    private static final Duration BFF_TODAY_CATCH_UP = Duration.ofSeconds(45);
    private static final Duration LIST_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration PENDING_REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final String JDBC_URL = System.getenv()
            .getOrDefault("BFF_DB_URL", "jdbc:postgresql://localhost:5432/consumerapp");
    private static final String JDBC_USER = System.getenv().getOrDefault("BFF_DB_USER", "consumerapp");
    private static final String JDBC_PASSWORD = System.getenv().getOrDefault("BFF_DB_PASSWORD", "password");
    private static final String INSERT_SCHEDULED_TRANSFER_SQL = """
            INSERT INTO scheduled_transfers (
                public_id, user_id, idempotency_key, fineract_from_account_id, fineract_to_account_id,
                created_at, updated_at)
            SELECT ?, id, ?, ?, ?, now(), now() FROM users WHERE email = ?
            """;
    private static final String COUNT_SCHEDULED_TRANSFERS_SQL = """
            SELECT count(*) FROM scheduled_transfers
            WHERE user_id = (SELECT id FROM users WHERE email = ?)
            """;
    private static final String COUNT_RESERVED_SCHEDULED_TRANSFERS_SQL = """
            SELECT count(*) FROM scheduled_transfers
            WHERE user_id = (SELECT id FROM users WHERE email = ?)
              AND fineract_standing_instruction_id IS NULL AND claimed_until > now()
            """;

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final FineractSeeder fineractSeeder = new FineractSeeder();
    private final LoginHelper loginHelper = new LoginHelper();
    private final MailpitClient mailpit = new MailpitClient();
    private final FineractJobRunner fineractJobRunner = new FineractJobRunner();
    private final FineractBusinessDate fineractBusinessDate = new FineractBusinessDate();

    private RegistrationHelper.BoundUserWithAccounts user;
    private ScheduledTransfersCommandControllerApi scheduledTransfersApi;
    private ScheduledTransfersQueryControllerApi scheduledTransfersQueryApi;
    private SavingsQueryControllerApi savingsApi;
    private BeneficiariesCommandControllerApi beneficiariesApi;
    private BeneficiariesQueryControllerApi beneficiariesQueryApi;
    private BeneficiaryCommandData beneficiary;
    private long secondSavingsId;
    private FineractSeeder.SeededTransferTarget beneficiaryTarget;
    private long unregisteredSavingsId;
    private LocalDate startDate;
    private LocalDate endDate;
    private ScheduledTransferCommandData result;
    private ScheduledTransferCommandData secondResult;
    private ScheduledTransferCommandData affordable;
    private ScheduledTransferCommandData unaffordable;
    private ScheduledTransferCommandData endsTheDayBefore;
    private ScheduledTransferChallengeCommandData pendingChallenge;
    private String pendingOtp;
    private BeneficiaryRowLock beneficiaryLock;
    private CompletableFuture<Optional<FeignException>> pendingRequest;
    private UUID otherCustomersTransferId;
    private UUID unownedSourceTransferId;
    private FeignException lastError;

    @Given("a logged-in customer setting up scheduled transfers")
    public void loggedInCustomer() {
        user = registrationHelper.registerBoundUserWithAccounts();
        String accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        scheduledTransfersApi = ConsumerApiClientFactory.authenticated(
                ScheduledTransfersCommandControllerApi.class, accessToken, DEVICE_FINGERPRINT);
        scheduledTransfersQueryApi = ConsumerApiClientFactory.authenticated(
                ScheduledTransfersQueryControllerApi.class, accessToken, DEVICE_FINGERPRINT);
        savingsApi = ConsumerApiClientFactory.authenticated(
                SavingsQueryControllerApi.class, accessToken, DEVICE_FINGERPRINT);
        beneficiariesApi = ConsumerApiClientFactory.authenticated(
                BeneficiariesCommandControllerApi.class, accessToken, DEVICE_FINGERPRINT);
        beneficiariesQueryApi = ConsumerApiClientFactory.authenticated(
                BeneficiariesQueryControllerApi.class, accessToken, DEVICE_FINGERPRINT);
        startDate = LocalDate.now(ZoneId.systemDefault()).plusDays(START_IN_DAYS);
    }

    @Given("I own a second savings account for scheduled transfers")
    public void ownSecondSavingsAccount() {
        secondSavingsId = fineractSeeder.openAnotherSavingsAccount(user.savingsAccountId());
    }

    @Given("another client's savings account is my scheduled transfer beneficiary with a transfer limit")
    public void beneficiaryWithTransferLimit() {
        addBeneficiary(TRANSFER_LIMIT);
    }

    @Given("another client's savings account is my scheduled transfer beneficiary with no transfer limit")
    public void beneficiaryWithNoTransferLimit() {
        addBeneficiary(null);
    }

    private void addBeneficiary(BigDecimal transferLimit) {
        beneficiaryTarget = fineractSeeder.seedTransferTarget();
        mailpit.deleteMessages(user.email());
        BeneficiaryChallengeCommandData challenge = beneficiariesApi.initiateAddBeneficiary(DEVICE_FINGERPRINT,
                new InitiateAddBeneficiaryCommandRequest()
                        .name(BENEFICIARY_NAME)
                        .officeName(beneficiaryTarget.officeName())
                        .accountNumber(beneficiaryTarget.savingsAccountNumber())
                        .transferLimit(transferLimit));
        String otp = mailpit.waitForOtp(user.email());
        beneficiary = beneficiariesApi.confirmAddBeneficiary(DEVICE_FINGERPRINT,
                new ConfirmAddBeneficiaryCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp)
                        .name(BENEFICIARY_NAME)
                        .officeName(beneficiaryTarget.officeName())
                        .accountNumber(beneficiaryTarget.savingsAccountNumber())
                        .transferLimit(transferLimit));
    }

    @Given("another client owns a savings account that is not my scheduled transfer beneficiary")
    public void anotherClientsUnregisteredSavings() {
        unregisteredSavingsId = fineractSeeder.seedTransferTarget().savingsAccountId();
    }

    /**
     * Fineract cannot move a savings account to another client, so the row is written directly: to the list it looks
     * exactly like a transfer set up from an account the customer has since stopped owning.
     */
    @Given("I have a scheduled transfer on record from that account")
    public void scheduledTransferOnRecordFromUnownedAccount() {
        unownedSourceTransferId = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(INSERT_SCHEDULED_TRANSFER_SQL)) {
            statement.setObject(1, unownedSourceTransferId);
            statement.setString(2, "cuke-unowned-" + unownedSourceTransferId);
            statement.setLong(3, unregisteredSavingsId);
            statement.setLong(4, user.savingsAccountId());
            statement.setString(5, user.email());
            assertThat(statement.executeUpdate()).isEqualTo(1);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to insert a scheduled_transfers row at " + JDBC_URL + "; is the Docker Compose stack up?", e);
        }
    }

    @Given("my savings account is funded for scheduled transfers")
    public void fundSavingsAccount() {
        fineractSeeder.depositToSavings(user.savingsAccountId(), FUNDED_BALANCE);
    }

    @Given("the bank set up a standing instruction between my savings accounts")
    public void bankSetUpStandingInstruction() {
        fineractSeeder.seedBankStandingInstruction(user.savingsAccountId(), secondSavingsId);
    }

    @Given("the bank sets up a standing instruction from my savings account to that beneficiary")
    public void bankSetsUpStandingInstructionToTheBeneficiary() {
        fineractSeeder.seedBankStandingInstruction(user.savingsAccountId(), beneficiaryTarget.savingsAccountId());
    }

    @Given("bank staff raise the scheduled transfer's amount in Fineract above the beneficiary's limit")
    public void bankStaffRaiseTheScheduledAmount() {
        fineractSeeder.updateStandingInstructionAmount(onlyInstructionFromMySavings().getId(), ABOVE_LIMIT_AMOUNT);
    }

    @Given("I have initiated a monthly transfer within the limit to that beneficiary")
    public void initiatedMonthlyToBeneficiary() {
        mailpit.deleteMessages(user.email());
        pendingChallenge = scheduledTransfersApi.initiateScheduledTransfer(DEVICE_FINGERPRINT,
                initiateRequest(user.savingsAccountId(), beneficiaryTarget.savingsAccountId(), TRANSFER_AMOUNT,
                        ScheduledTransferFrequency.MONTHLY, startDate, null));
        pendingOtp = mailpit.waitForOtp(user.email());
    }

    @Given("another customer has set up a scheduled transfer")
    public void anotherCustomerSetUpAScheduledTransfer() {
        RegistrationHelper.BoundUserWithAccounts other = registrationHelper.registerBoundUserWithAccounts();
        String accessToken = loginHelper.login(other.email(), other.password(), DEVICE_FINGERPRINT);
        ScheduledTransfersCommandControllerApi otherApi = ConsumerApiClientFactory.authenticated(
                ScheduledTransfersCommandControllerApi.class, accessToken, DEVICE_FINGERPRINT);
        long otherSecondSavingsId = fineractSeeder.openAnotherSavingsAccount(other.savingsAccountId());
        otherCustomersTransferId = scheduleAs(other, otherApi, otherSecondSavingsId, TRANSFER_AMOUNT,
                ScheduledTransferFrequency.WEEKLY, null, UUID.randomUUID().toString()).getPublicId();
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

    @When("I schedule a monthly transfer to that beneficiary")
    public void scheduleMonthlyToBeneficiaryWithNoLimit() {
        scheduleMonthlyToBeneficiary();
    }

    @When("I schedule a weekly transfer to that beneficiary with an end date")
    public void scheduleWeeklyToBeneficiaryWithAnEndDate() {
        endDate = startDate.plusMonths(RUN_FOR_MONTHS);
        result = schedule(beneficiaryTarget.savingsAccountId(), TRANSFER_AMOUNT, ScheduledTransferFrequency.WEEKLY,
                endDate, UUID.randomUUID().toString());
    }

    @When("I schedule a weekly transfer to my second savings account ending the day before")
    public void scheduleWeeklyToSecondSavingsEndingTheDayBefore() {
        endsTheDayBefore = schedule(secondSavingsId, TRANSFER_AMOUNT, ScheduledTransferFrequency.WEEKLY,
                endDate.minusDays(1), UUID.randomUUID().toString());
    }

    /**
     * The BFF caches Fineract's today, so the step waits until the BFF has caught up: its list leaves out a transfer
     * past its end date, so on the end date it shows the transfer to the beneficiary but not the one ending the day
     * before.
     */
    @When("Fineract's business date is my end date")
    public void fineractsBusinessDateIsMyEndDate() {
        fineractBusinessDate.moveTo(endDate);
        awaitListed(transfers -> transfers.equals(Set.of(result.getPublicId())));
    }

    @When("Fineract's business date is the day after my end date")
    public void fineractsBusinessDateIsTheDayAfterMyEndDate() {
        fineractBusinessDate.moveTo(endDate.plusDays(1));
        awaitListed(Set::isEmpty);
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
        // Yesterday here is before Fineract's today even if this JVM and the tenant sit on different dates.
        lastError = captureError(() -> scheduledTransfersApi.initiateScheduledTransfer(DEVICE_FINGERPRINT,
                initiateRequest(user.savingsAccountId(), secondSavingsId, TRANSFER_AMOUNT,
                        ScheduledTransferFrequency.WEEKLY, LocalDate.now(ZoneId.systemDefault()).minusDays(1), null)));
    }

    @When("I schedule a monthly transfer to my second savings account starting on the 31st")
    public void scheduleMonthlyStartingOnThe31st() {
        YearMonth month = YearMonth.now(ZoneId.systemDefault()).plusMonths(1);
        while (month.lengthOfMonth() < LONGEST_MONTH_DAYS) {
            month = month.plusMonths(1);
        }
        startDate = month.atEndOfMonth();
        result = schedule(secondSavingsId, TRANSFER_AMOUNT, ScheduledTransferFrequency.MONTHLY, null,
                UUID.randomUUID().toString());
    }

    @When("I schedule a weekly transfer I can afford and one I cannot, both to my second savings account soon")
    public void scheduleAffordableAndUnaffordable() {
        startDate = LocalDate.now(ZoneId.systemDefault()).plusDays(RUN_START_IN_DAYS);
        affordable = schedule(secondSavingsId, TRANSFER_AMOUNT, ScheduledTransferFrequency.WEEKLY, null,
                UUID.randomUUID().toString());
        unaffordable = schedule(secondSavingsId, UNAFFORDABLE_AMOUNT, ScheduledTransferFrequency.WEEKLY, null,
                UUID.randomUUID().toString());
    }

    @When("I schedule a monthly transfer I can afford on the 31st of a month before a shorter one")
    public void scheduleMonthlyOnThe31stBeforeAShorterMonth() {
        YearMonth month = YearMonth.now(ZoneId.systemDefault()).plusMonths(1);
        while (month.lengthOfMonth() < LONGEST_MONTH_DAYS
                || month.plusMonths(1).lengthOfMonth() == LONGEST_MONTH_DAYS) {
            month = month.plusMonths(1);
        }
        startDate = month.atEndOfMonth();
        affordable = schedule(secondSavingsId, TRANSFER_AMOUNT, ScheduledTransferFrequency.MONTHLY, null,
                UUID.randomUUID().toString());
    }

    @When("Fineract runs the standing instructions due on their start date")
    public void fineractRunsTheInstructionsDueOnTheStartDate() {
        fineractJobRunner.runStandingInstructionsDueOn(startDate);
    }

    @When("Fineract runs the standing instructions due on the last day of the next month")
    public void fineractRunsTheInstructionsDueAtTheEndOfTheNextMonth() {
        fineractJobRunner.runStandingInstructionsDueOn(YearMonth.from(startDate).plusMonths(1).atEndOfMonth());
    }

    @When("I request the run history of the other customer's scheduled transfer")
    public void requestOtherCustomersRunHistory() {
        lastError = captureError(
                () -> scheduledTransfersQueryApi.listScheduledTransferRuns(otherCustomersTransferId, null, null));
    }

    @When("I request the run history of the transfer from that account")
    public void requestUnownedSourceRunHistory() {
        lastError = captureError(
                () -> scheduledTransfersQueryApi.listScheduledTransferRuns(unownedSourceTransferId, null, null));
    }

    @When("I list scheduled transfers without a session")
    public void listWithoutSession() {
        lastError = captureError(() -> ConsumerApiClientFactory
                .unauthenticated(ScheduledTransfersQueryControllerApi.class)
                .listScheduledTransfers());
    }

    @When("I confirm the same scheduled transfer twice with the same idempotency key")
    public void confirmTwiceWithSameKey() {
        String idempotencyKey = UUID.randomUUID().toString();
        result = schedule(secondSavingsId, TRANSFER_AMOUNT, ScheduledTransferFrequency.WEEKLY, null, idempotencyKey);
        secondResult =
                schedule(secondSavingsId, TRANSFER_AMOUNT, ScheduledTransferFrequency.WEEKLY, null, idempotencyKey);
    }

    @When("I cancel the scheduled transfer")
    public void cancelTheScheduledTransfer() {
        mailpit.deleteMessages(user.email());
        ScheduledTransferChallengeCommandData challenge =
                scheduledTransfersApi.initiateCancelScheduledTransfer(DEVICE_FINGERPRINT, result.getPublicId());
        String otp = mailpit.waitForOtp(user.email());
        scheduledTransfersApi.confirmCancelScheduledTransfer(DEVICE_FINGERPRINT, UUID.randomUUID().toString(),
                result.getPublicId(), new ConfirmCancelScheduledTransferCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp));
    }

    @When("I try to delete that beneficiary")
    public void tryToDeleteTheBeneficiary() {
        lastError = captureError(() -> beneficiariesApi.deleteBeneficiary(beneficiary.getPublicId()));
    }

    @When("I delete that beneficiary")
    public void deleteTheBeneficiary() {
        beneficiariesApi.deleteBeneficiary(beneficiary.getPublicId());
    }

    @When("I try to lower that beneficiary's transfer limit below the scheduled amount")
    public void tryToLowerTheLimitBelowTheScheduledAmount() {
        tryToChangeTheLimit(BELOW_TRANSFER_AMOUNT);
    }

    @When("I try to set that beneficiary's transfer limit below the scheduled amount")
    public void tryToSetTheLimitBelowTheScheduledAmount() {
        tryToChangeTheLimit(BELOW_TRANSFER_AMOUNT);
    }

    @When("I try to lower that beneficiary's transfer limit to the scheduled amount")
    public void tryToLowerTheLimitToTheScheduledAmount() {
        tryToChangeTheLimit(TRANSFER_AMOUNT);
    }

    @When("I lower that beneficiary's transfer limit to the scheduled amount")
    public void lowerTheLimitToTheScheduledAmount() {
        changeTheLimit(TRANSFER_AMOUNT);
    }

    @When("I lower that beneficiary's transfer limit below the scheduled amount")
    public void lowerTheLimitBelowTheScheduledAmount() {
        changeTheLimit(BELOW_TRANSFER_AMOUNT);
    }

    @When("I raise that beneficiary's transfer limit")
    public void raiseTheLimit() {
        changeTheLimit(RAISED_TRANSFER_LIMIT);
    }

    @When("I remove that beneficiary's transfer limit")
    public void removeTheLimit() {
        changeTheLimit(null);
    }

    @When("I confirm the transfer while a beneficiary delete holds that beneficiary")
    public void confirmWhileADeleteHoldsTheBeneficiary() {
        beneficiaryLock = BeneficiaryRowLock.forUpdate(beneficiary.getPublicId());
        pendingRequest = CompletableFuture.supplyAsync(() -> outcomeOf(() -> confirmAs(user, scheduledTransfersApi,
                pendingChallenge, pendingOtp, beneficiaryTarget.savingsAccountId(), TRANSFER_AMOUNT,
                ScheduledTransferFrequency.MONTHLY, null, UUID.randomUUID().toString())));
        beneficiaryLock.awaitWaiter(pendingRequest);
    }

    @When("the delete marks that beneficiary deleted and commits")
    public void theDeleteCommits() {
        beneficiaryLock.deactivateAndCommit();
        lastError = awaitPendingRequest().orElse(null);
    }

    @When("I delete that beneficiary while a scheduled transfer confirm holds it for its check")
    public void deleteWhileAConfirmHoldsTheBeneficiary() {
        beneficiaryLock = BeneficiaryRowLock.forShare(beneficiary.getPublicId());
        pendingRequest = CompletableFuture.supplyAsync(
                () -> outcomeOf(() -> beneficiariesApi.deleteBeneficiary(beneficiary.getPublicId())));
        beneficiaryLock.awaitWaiter(pendingRequest);
    }

    @When("the confirm's check commits")
    public void theConfirmsCheckCommits() {
        beneficiaryLock.commit();
        lastError = awaitPendingRequest().orElse(null);
    }

    @When("I initiate cancelling the other customer's scheduled transfer")
    public void initiateCancellingOtherCustomersTransfer() {
        lastError = captureError(() -> scheduledTransfersApi.initiateCancelScheduledTransfer(
                DEVICE_FINGERPRINT, otherCustomersTransferId));
    }

    @When("I initiate cancelling a scheduled transfer without a session")
    public void initiateCancelWithoutSession() {
        lastError = captureError(() -> ConsumerApiClientFactory
                .unauthenticated(ScheduledTransfersCommandControllerApi.class)
                .initiateCancelScheduledTransfer(DEVICE_FINGERPRINT, UUID.randomUUID()));
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

    @Then("Fineract holds one monthly standing instruction to my second savings account from the 31st")
    public void fineractHoldsMonthlyInstructionFromThe31st() {
        GetPageItemsStandingInstructionSwagger instruction = onlyInstructionFromMySavings();
        assertInstructionMatches(instruction, secondSavingsId, ScheduledTransferConstants.MONTHS_FREQUENCY);
        assertThat(instruction.getValidFrom().getDayOfMonth()).isEqualTo(LONGEST_MONTH_DAYS);
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

    @Then("my scheduled transfers list shows the weekly transfer to my second savings account until my end date")
    public void listShowsTheWeeklyTransfer() {
        assertThat(scheduledTransfersQueryApi.listScheduledTransfers()).singleElement().satisfies(transfer -> {
            assertThat(transfer.getPublicId()).isEqualTo(result.getPublicId());
            assertThat(transfer.getFromAccountId()).isEqualTo(user.savingsAccountId());
            assertThat(transfer.getToAccountId()).isEqualTo(secondSavingsId);
            assertThat(transfer.getAmount()).isEqualByComparingTo(TRANSFER_AMOUNT);
            assertThat(transfer.getFrequency()).isEqualTo(
                    ScheduledTransferQueryData.FrequencyEnum.fromValue(ScheduledTransferFrequency.WEEKLY.name()));
            assertThat(transfer.getStartDate()).isEqualTo(startDate);
            assertThat(transfer.getEndDate()).isEqualTo(endDate);
        });
    }

    /**
     * Fineract marks a deleted instruction and appends {@code _deleted_<id>} to its name on every delete, so the name
     * also shows that a second cancel did not delete it again.
     */
    @Then("Fineract holds the scheduled transfer's standing instruction as deleted")
    public void fineractHoldsTheInstructionAsDeleted() {
        GetPageItemsStandingInstructionSwagger instruction = onlyInstructionFromMySavings();
        assertThat(instruction.getStatus().getId()).isEqualTo(ScheduledTransferConstants.DELETED_STATUS_ID);
        assertThat(instruction.getName()).isEqualTo(ScheduledTransferConstants.INSTRUCTION_NAME_PREFIX
                + result.getPublicId() + DELETED_NAME_SUFFIX + instruction.getId());
    }

    @Then("the run history of the cancelled transfer is still readable")
    public void cancelledTransfersRunHistoryIsReadable() {
        assertThat(runsOf(result)).isEmpty();
    }

    @Then("the scheduled transfer cancellation is rejected as not found")
    public void cancellationRejectedNotFound() {
        assertError(NOT_FOUND, ScheduledTransferNotFoundException.CODE);
    }

    @Then("my scheduled transfers list is empty")
    public void listIsEmpty() {
        assertThat(scheduledTransfersQueryApi.listScheduledTransfers()).isEmpty();
    }

    @Then("the run history of the transfer I can afford shows one successful run")
    public void affordableTransferRanOnce() {
        assertThat(runsOf(affordable)).singleElement().satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo(runStatus(ScheduledTransferRunStatus.SUCCEEDED));
            assertRanToday(run);
            assertThat(run.getAmount()).isEqualByComparingTo(TRANSFER_AMOUNT);
        });
    }

    @Then("the run history of the transfer I cannot afford shows one failed run that moved nothing")
    public void unaffordableTransferFailedOnce() {
        assertThat(runsOf(unaffordable)).singleElement().satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo(runStatus(ScheduledTransferRunStatus.FAILED));
            assertRanToday(run);
            assertThat(run.getAmount()).isNull();
        });
    }

    @Then("my second savings account received only the amount of the transfer I can afford")
    public void secondAccountReceivedOnlyTheAffordableAmount() {
        assertThat(savingsApi.getSavingsAccount(secondSavingsId).getBalance()).isEqualByComparingTo(TRANSFER_AMOUNT);
    }

    @Then("the beneficiary change is rejected because a scheduled transfer to it is active")
    public void beneficiaryChangeRejectedForActiveTransfer() {
        assertError(CONFLICT, BeneficiaryScheduledTransferActiveException.CODE);
    }

    @Then("the beneficiary change is rejected because its limit is below a scheduled transfer")
    public void beneficiaryChangeRejectedForLimitBelowTransfer() {
        assertError(CONFLICT, BeneficiaryLimitBelowScheduledTransferException.CODE);
    }

    @Then("that beneficiary is no longer in my beneficiary list")
    public void beneficiaryNoLongerListed() {
        assertThat(beneficiariesQueryApi.listBeneficiaries())
                .extracting(BeneficiaryQueryData::getPublicId)
                .doesNotContain(beneficiary.getPublicId());
    }

    @Then("that beneficiary's transfer limit is the scheduled amount")
    public void beneficiaryLimitIsTheScheduledAmount() {
        assertThat(beneficiary.getTransferLimit()).isEqualByComparingTo(TRANSFER_AMOUNT);
    }

    @Then("that beneficiary's transfer limit is below the scheduled amount")
    public void beneficiaryLimitIsBelowTheScheduledAmount() {
        assertThat(beneficiary.getTransferLimit()).isEqualByComparingTo(BELOW_TRANSFER_AMOUNT);
    }

    @Then("that beneficiary's transfer limit is the raised limit")
    public void beneficiaryLimitIsTheRaisedLimit() {
        assertThat(beneficiary.getTransferLimit()).isEqualByComparingTo(RAISED_TRANSFER_LIMIT);
    }

    @Then("that beneficiary has no transfer limit")
    public void beneficiaryHasNoLimit() {
        assertThat(beneficiary.getTransferLimit()).isNull();
    }

    @Then("no verification code was emailed to me")
    public void noVerificationCodeWasEmailed() {
        assertThat(mailpit.hasMessages(user.email())).isFalse();
    }

    @Then("my confirm waits with the transfer reserved")
    public void confirmWaitsWithTheTransferReserved() {
        assertThat(pendingRequest).isNotDone();
        assertThat(countScheduledTransfers(COUNT_RESERVED_SCHEDULED_TRANSFERS_SQL)).isEqualTo(1);
    }

    @Then("my delete waits for the confirm's check")
    public void deleteWaitsForTheConfirmsCheck() {
        assertThat(pendingRequest).isNotDone();
    }

    @Then("my delete succeeds")
    public void deleteSucceeds() {
        assertThat(lastError).isNull();
    }

    @Then("Fineract holds no standing instruction from my savings account")
    public void fineractHoldsNoInstruction() {
        assertThat(fineractSeeder.standingInstructionsFrom(user.savingsAccountId())).isEmpty();
    }

    @Then("I have no scheduled transfer on record")
    public void noScheduledTransferOnRecord() {
        assertThat(countScheduledTransfers(COUNT_SCHEDULED_TRANSFERS_SQL)).isZero();
    }

    /** Lets a request still waiting on a lock this scenario holds go on, so it cannot hold up the next scenario. */
    @After
    public void releaseTheBeneficiaryLock() {
        if (beneficiaryLock != null) {
            beneficiaryLock.close();
        }
        if (pendingRequest != null && !pendingRequest.isDone()) {
            awaitPendingRequest();
        }
    }

    /**
     * Puts Fineract's business date back and waits until the BFF has too, so the next scenario's start dates are
     * checked against the real today: the transfer ending the day before is listed again.
     */
    @After
    public void putFineractsBusinessDateBack() {
        if (!fineractBusinessDate.isMoved()) {
            return;
        }
        fineractBusinessDate.restore();
        if (endsTheDayBefore != null) {
            awaitListed(transfers -> transfers.contains(endsTheDayBefore.getPublicId()));
        } else {
            sleep(BFF_TODAY_CATCH_UP);
        }
    }

    @Then("the scheduled transfer request is rejected as not found")
    public void rejectedNotFound() {
        assertError(NOT_FOUND, ScheduledTransferQueryNotFoundException.CODE);
    }

    @Then("the scheduled transfer request is rejected because it exceeds the beneficiary limit")
    public void rejectedAboveLimit() {
        assertError(FORBIDDEN, ScheduledTransferBeneficiaryLimitExceededException.CODE);
    }

    @Then("the scheduled transfer request is denied as forbidden")
    public void deniedForbidden() {
        assertError(FORBIDDEN, ScheduledTransferAccessDeniedException.CODE);
    }

    @Then("the scheduled transfer history request is denied as forbidden")
    public void historyDeniedForbidden() {
        assertError(FORBIDDEN, ScheduledTransferQueryAccessDeniedException.CODE);
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
        return scheduleAs(user, scheduledTransfersApi, toAccountId, amount, frequency, end, idempotencyKey);
    }

    /** Sets up a transfer out of {@code customer}'s savings account, starting on {@link #startDate}. */
    private ScheduledTransferCommandData scheduleAs(RegistrationHelper.BoundUserWithAccounts customer,
            ScheduledTransfersCommandControllerApi api, long toAccountId, BigDecimal amount,
            ScheduledTransferFrequency frequency, LocalDate end, String idempotencyKey) {
        mailpit.deleteMessages(customer.email());
        ScheduledTransferChallengeCommandData challenge = api.initiateScheduledTransfer(DEVICE_FINGERPRINT,
                initiateRequest(customer.savingsAccountId(), toAccountId, amount, frequency, startDate, end));
        String otp = mailpit.waitForOtp(customer.email());
        return confirmAs(customer, api, challenge, otp, toAccountId, amount, frequency, end, idempotencyKey);
    }

    private ScheduledTransferCommandData confirmAs(RegistrationHelper.BoundUserWithAccounts customer,
            ScheduledTransfersCommandControllerApi api, ScheduledTransferChallengeCommandData challenge, String otp,
            long toAccountId, BigDecimal amount, ScheduledTransferFrequency frequency, LocalDate end,
            String idempotencyKey) {
        return api.confirmScheduledTransfer(DEVICE_FINGERPRINT, idempotencyKey,
                new ConfirmScheduledTransferCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp)
                        .fromAccountId(customer.savingsAccountId())
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

    private List<ScheduledTransferRunQueryData> runsOf(ScheduledTransferCommandData transfer) {
        return scheduledTransfersQueryApi.listScheduledTransferRuns(transfer.getPublicId(), null, null).getContent();
    }

    /**
     * Fineract dates a run by its own clock when the job ran, not by the business date this scenario moved, so the run
     * is dated today in Fineract's time zone: within a day of this JVM's today.
     */
    private static void assertRanToday(ScheduledTransferRunQueryData run) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        assertThat(run.getDate()).isBetween(today.minusDays(1), today.plusDays(1));
    }

    private static ScheduledTransferRunQueryData.StatusEnum runStatus(ScheduledTransferRunStatus status) {
        return ScheduledTransferRunQueryData.StatusEnum.fromValue(status.name());
    }

    /** Initiates a change to the beneficiary's limit, clearing the mailbox first so a step can tell no OTP went out. */
    private void tryToChangeTheLimit(BigDecimal transferLimit) {
        mailpit.deleteMessages(user.email());
        lastError = captureError(() -> beneficiariesApi.initiateUpdateBeneficiary(DEVICE_FINGERPRINT,
                beneficiary.getPublicId(), new InitiateUpdateBeneficiaryCommandRequest()
                        .name(BENEFICIARY_NAME)
                        .transferLimit(transferLimit)));
    }

    private void changeTheLimit(BigDecimal transferLimit) {
        mailpit.deleteMessages(user.email());
        BeneficiaryChallengeCommandData challenge = beneficiariesApi.initiateUpdateBeneficiary(DEVICE_FINGERPRINT,
                beneficiary.getPublicId(), new InitiateUpdateBeneficiaryCommandRequest()
                        .name(BENEFICIARY_NAME)
                        .transferLimit(transferLimit));
        String otp = mailpit.waitForOtp(user.email());
        beneficiary = beneficiariesApi.confirmUpdateBeneficiary(DEVICE_FINGERPRINT, beneficiary.getPublicId(),
                new ConfirmUpdateBeneficiaryCommandRequest()
                        .stepUpToken(challenge.getStepUpToken())
                        .otp(otp)
                        .name(BENEFICIARY_NAME)
                        .transferLimit(transferLimit));
    }

    /** Polls the scheduled transfers list until the public ids it shows satisfy {@code expected}. */
    private void awaitListed(Predicate<Set<UUID>> expected) {
        long deadline = System.nanoTime() + BFF_TODAY_CATCH_UP.toNanos();
        Set<UUID> listed = Set.of();
        while (System.nanoTime() < deadline) {
            listed = scheduledTransfersQueryApi.listScheduledTransfers().stream()
                    .map(ScheduledTransferQueryData::getPublicId)
                    .collect(Collectors.toUnmodifiableSet());
            if (expected.test(listed)) {
                return;
            }
            sleep(LIST_POLL_INTERVAL);
        }
        throw new AssertionError("the scheduled transfers list still shows " + listed + " after "
                + BFF_TODAY_CATCH_UP.toSeconds() + "s; has the BFF caught up with Fineract's business date?");
    }

    private Optional<FeignException> awaitPendingRequest() {
        try {
            return pendingRequest.get(PENDING_REQUEST_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the request behind the lock", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("the request behind the lock failed", e.getCause());
        } catch (TimeoutException e) {
            throw new AssertionError("the request behind the lock did not finish within "
                    + PENDING_REQUEST_TIMEOUT.toSeconds() + "s", e);
        }
    }

    private long countScheduledTransfers(String sql) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.email());
            try (ResultSet count = statement.executeQuery()) {
                assertThat(count.next()).isTrue();
                return count.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to count scheduled_transfers rows at " + JDBC_URL + "; is the Docker Compose stack up?", e);
        }
    }

    private static Optional<FeignException> outcomeOf(Runnable call) {
        try {
            call.run();
            return Optional.empty();
        } catch (FeignException e) {
            return Optional.of(e);
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting", e);
        }
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
