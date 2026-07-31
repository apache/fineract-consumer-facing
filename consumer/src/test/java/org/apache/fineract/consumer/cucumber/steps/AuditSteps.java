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
import static org.assertj.core.api.Assertions.fail;

import feign.FeignException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.fineract.consumer.audit.command.data.AuditEventSource;
import org.apache.fineract.consumer.audit.command.exception.AuditBatchTooLargeException;
import org.apache.fineract.consumer.client.api.AuditCommandControllerApi;
import org.apache.fineract.consumer.client.model.ApiResponse;
import org.apache.fineract.consumer.client.model.AuditEventsSubmittedCommandData;
import org.apache.fineract.consumer.client.model.AuditEventCommandRequest;
import org.apache.fineract.consumer.client.model.SubmitAuditEventsCommandRequest;
import org.apache.fineract.consumer.cucumber.clients.AuthenticationClient;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.infrastructure.access.filter.DeviceFingerprintFilter;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class AuditSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-audit-device";
    private static final String OTHER_DEVICE_FINGERPRINT = "cucumber-audit-other-device";
    private static final String WRONG_PASSWORD = "Wrong-password-123";
    private static final int ACCEPTED = 202;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int PAYLOAD_TOO_LARGE = 413;
    private static final int OVERSIZED_BATCH_SIZE = 51;
    private static final String PII_EMAIL_VALUE = "cucumber-pii@example.com";
    private static final String EXPECTED_AUDIT_REJECTED = "expected the audit submission to be rejected";
    private static final String EXPECTED_LOGIN_REJECTED = "expected login to be rejected";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static final long SERVER_EVENT_TIMEOUT_MILLIS = 5_000L;
    private static final long POLL_INTERVAL_MILLIS = 200L;

    private static final String JDBC_URL = System.getenv()
            .getOrDefault("BFF_DB_URL", "jdbc:postgresql://localhost:5432/consumerapp");
    private static final String JDBC_USER = System.getenv().getOrDefault("BFF_DB_USER", "consumerapp");
    private static final String JDBC_PASSWORD = System.getenv().getOrDefault("BFF_DB_PASSWORD", "password");
    private static final String COUNT_BY_EVENT_UUID_AND_SOURCE_SQL =
            "SELECT count(*) FROM audit_events WHERE event_uuid = ? AND source = ?";
    private static final String COUNT_KNOWN_USER_SERVER_EVENT_SQL =
            "SELECT count(*) FROM audit_events WHERE event_type = ? AND source = ? "
                    + "AND device_fingerprint = ? AND user_id IS NOT NULL AND unknown_principal = false";

    private static final List<AuditEventType> CLIENT_EVENT_TYPES = List.of(
            AuditEventType.NAVIGATION, AuditEventType.SENSITIVE_VIEW, AuditEventType.API_FAILURE);

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final LoginHelper loginHelper = new LoginHelper();
    private final AuthenticationClient authClient = new AuthenticationClient();

    private RegistrationHelper.BoundUser user;
    private String accessToken;
    private AuditCommandControllerApi auditApi;
    private ApiResponse<AuditEventsSubmittedCommandData> lastResult;
    private FeignException lastError;
    private List<String> submittedEventUuids;
    private String piiEventUuid;
    private String replayedEventUuid;

    @Given("a logged-in audit customer")
    public void loggedInAuditCustomer() {
        user = registrationHelper.registerBoundUser();
        accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        auditApi = ConsumerApiClientFactory.authenticated(
                AuditCommandControllerApi.class, accessToken, DEVICE_FINGERPRINT);
    }

    @When("I submit a batch of {int} valid audit events")
    public void submitValidBatch(int count) {
        List<AuditEventCommandRequest> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(validEvent(CLIENT_EVENT_TYPES.get(i % CLIENT_EVENT_TYPES.size())));
        }
        submittedEventUuids = events.stream().map(AuditEventCommandRequest::getEventUuid).toList();
        lastResult = auditApi.submitAuditEventsWithHttpInfo(DEVICE_FINGERPRINT, batch(events));
    }

    @When("I submit a batch containing one event whose details include an email address")
    public void submitBatchWithPiiEvent() {
        AuditEventCommandRequest piiEvent = validEvent(AuditEventType.CLIENT_ERROR)
                .details(Map.of("message", PII_EMAIL_VALUE));
        piiEventUuid = piiEvent.getEventUuid();
        List<AuditEventCommandRequest> events = List.of(
                validEvent(AuditEventType.NAVIGATION), validEvent(AuditEventType.SENSITIVE_VIEW), piiEvent);
        lastResult = auditApi.submitAuditEventsWithHttpInfo(DEVICE_FINGERPRINT, batch(events));
    }

    @When("I submit the same audit event twice in one batch")
    public void submitDuplicateEventUuid() {
        AuditEventCommandRequest event = validEvent(AuditEventType.NAVIGATION);
        replayedEventUuid = event.getEventUuid();
        AuditEventCommandRequest replay = validEvent(AuditEventType.NAVIGATION)
                .eventUuid(replayedEventUuid);
        lastResult = auditApi.submitAuditEventsWithHttpInfo(DEVICE_FINGERPRINT, batch(List.of(event, replay)));
    }

    @When("I submit an audit batch without being authenticated")
    public void submitBatchUnauthenticated() {
        AuditCommandControllerApi unauthenticatedApi =
                ConsumerApiClientFactory.unauthenticated(AuditCommandControllerApi.class);
        lastError = captureError(() -> unauthenticatedApi.submitAuditEvents(
                DEVICE_FINGERPRINT, batch(List.of(validEvent(AuditEventType.NAVIGATION)))));
    }

    @When("I submit an audit batch that exceeds the maximum batch size")
    public void submitOversizedBatch() {
        List<AuditEventCommandRequest> events = new ArrayList<>();
        for (int i = 0; i < OVERSIZED_BATCH_SIZE; i++) {
            events.add(validEvent(AuditEventType.NAVIGATION));
        }
        lastError = captureError(() -> auditApi.submitAuditEvents(DEVICE_FINGERPRINT, batch(events)));
    }

    @When("I submit an audit batch with a different device fingerprint")
    public void submitBatchFromDifferentDevice() {
        lastError = captureError(() -> auditApi.submitAuditEvents(
                OTHER_DEVICE_FINGERPRINT, batch(List.of(validEvent(AuditEventType.NAVIGATION)))));
    }

    @When("I attempt to log in with a wrong password")
    public void attemptLoginWithWrongPassword() {
        try {
            authClient.login(user.email(), WRONG_PASSWORD, DEVICE_FINGERPRINT);
            fail(EXPECTED_LOGIN_REJECTED);
        } catch (FeignException e) {
            assertThat(e.status()).isEqualTo(UNAUTHORIZED);
        }
    }

    @Then("the audit batch is accepted with {int} accepted and {int} rejected")
    public void batchAcceptedWithCounts(int accepted, int rejected) {
        assertThat(lastResult.getStatusCode()).isEqualTo(ACCEPTED);
        assertThat(lastResult.getData().getAccepted()).isEqualTo(accepted);
        assertThat(lastResult.getData().getRejected()).isEqualTo(rejected);
    }

    @Then("the submitted events are stored as CLIENT audit rows")
    public void submittedEventsStoredAsClientRows() {
        for (String eventUuid : submittedEventUuids) {
            assertThat(countClientRows(eventUuid))
                    .as("CLIENT audit row for event %s", eventUuid)
                    .isEqualTo(1);
        }
    }

    @Then("the PII-bearing event is not stored")
    public void piiEventNotStored() {
        assertThat(countClientRows(piiEventUuid)).isZero();
    }

    @Then("only one audit row is stored for the replayed event uuid")
    public void onlyOneRowForReplayedEventUuid() {
        assertThat(countClientRows(replayedEventUuid)).isEqualTo(1);
    }

    @Then("the audit request is rejected as unauthenticated")
    public void auditRequestRejectedAsUnauthenticated() {
        assertThat(lastError.status()).isEqualTo(UNAUTHORIZED);
    }

    @Then("the audit request is rejected as too large")
    public void auditRequestRejectedAsTooLarge() {
        assertThat(lastError.status()).isEqualTo(PAYLOAD_TOO_LARGE);
        assertThat(readCode(lastError.contentUTF8())).isEqualTo(AuditBatchTooLargeException.CODE);
    }

    @Then("the audit request is forbidden with the device mismatch code")
    public void auditRequestForbiddenWithDeviceMismatchCode() {
        assertThat(lastError.status()).isEqualTo(FORBIDDEN);
        assertThat(readCode(lastError.contentUTF8())).isEqualTo(DeviceFingerprintFilter.MISMATCH_CODE);
    }

    @Then("a server-side login failure audit event is recorded for my user")
    public void serverLoginFailureEventRecorded() {
        long rows = pollForRows(COUNT_KNOWN_USER_SERVER_EVENT_SQL,
                AuditEventType.LOGIN_FAILURE.name(), AuditEventSource.SERVER.name(), DEVICE_FINGERPRINT);
        assertThat(rows)
                .as("SERVER %s audit row linked to a known user", AuditEventType.LOGIN_FAILURE)
                .isGreaterThanOrEqualTo(1);
    }

    private static AuditEventCommandRequest validEvent(AuditEventType eventType) {
        return new AuditEventCommandRequest()
                .eventUuid(UUID.randomUUID().toString())
                .eventType(eventType.name())
                .occurredAt(OffsetDateTime.now())
                .details(Map.of("route", "/accounts"));
    }

    private static SubmitAuditEventsCommandRequest batch(List<AuditEventCommandRequest> events) {
        return new SubmitAuditEventsCommandRequest().events(events);
    }

    private long countClientRows(String eventUuid) {
        return countRows(COUNT_BY_EVENT_UUID_AND_SOURCE_SQL,
                UUID.fromString(eventUuid), AuditEventSource.CLIENT.name());
    }

    private long pollForRows(String sql, Object... params) {
        long deadline = System.currentTimeMillis() + SERVER_EVENT_TIMEOUT_MILLIS;
        long count = countRows(sql, params);
        while (count == 0 && System.currentTimeMillis() < deadline) {
            sleep(POLL_INTERVAL_MILLIS);
            count = countRows(sql, params);
        }
        return count;
    }

    private long countRows(String sql, Object... params) {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to query audit_events at " + JDBC_URL + " — is the Docker Compose stack up?", e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for a server audit row", e);
        }
    }

    private static FeignException captureError(Runnable call) {
        try {
            call.run();
            throw new AssertionError(EXPECTED_AUDIT_REJECTED);
        } catch (FeignException e) {
            return e;
        }
    }

    private static String readCode(String body) {
        return JSON.readTree(body).path("code").asString();
    }
}
