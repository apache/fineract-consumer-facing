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
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import org.apache.fineract.consumer.client.api.BeneficiariesQueryControllerApi;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.infrastructure.access.exception.RateLimitExceededException;
import org.apache.fineract.consumer.infrastructure.access.service.RateLimitCounter;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class RateLimitSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-ratelimit-device";
    private static final int TOO_MANY_REQUESTS = 429;
    private static final long WINDOW_SECONDS = RateLimitCounter.WINDOW.toSeconds();
    private static final String EXPECTED_RATE_LIMITED = "expected the request to be rate limited";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final LoginHelper loginHelper = new LoginHelper();

    private BeneficiariesQueryControllerApi beneficiariesQueryApi;
    private FeignException lastError;
    private long loopStartedAtNanos;

    @Given("a registered and logged-in consumer")
    public void registeredAndLoggedInConsumer() {
        RegistrationHelper.BoundUser user = registrationHelper.registerBoundUser();
        String accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        beneficiariesQueryApi = ConsumerApiClientFactory.authenticated(
                BeneficiariesQueryControllerApi.class, accessToken, DEVICE_FINGERPRINT);
    }

    @When("the consumer sends {int} authenticated beneficiary list requests")
    public void sendListRequests(int count) {
        loopStartedAtNanos = System.nanoTime();
        for (int i = 0; i < count; i++) {
            beneficiariesQueryApi.listBeneficiaries();
        }
    }

    @When("the consumer sends one more beneficiary list request")
    public void sendOneMoreListRequest() {
        try {
            beneficiariesQueryApi.listBeneficiaries();
            lastError = null;
        } catch (FeignException e) {
            lastError = e;
        }
    }

    @Then("the request is rejected with status {int}")
    public void requestRejectedWithStatus(int status) {
        assertThat(status).isEqualTo(TOO_MANY_REQUESTS);
        assertThat(lastError).as("%s (%s)", EXPECTED_RATE_LIMITED, windowContext()).isNotNull();
        assertThat(lastError.status()).isEqualTo(status);
    }

    @Then("the response carries the rate-limit error code and a Retry-After header")
    public void responseCarriesRateLimitCodeAndRetryAfter() {
        assertThat(JSON.readTree(lastError.contentUTF8()).path("code").asString())
                .isEqualTo(RateLimitExceededException.CODE);
        assertThat(retryAfterSeconds()).isBetween(1L, WINDOW_SECONDS);
    }

    private String windowContext() {
        long elapsed = Duration.ofNanos(System.nanoTime() - loopStartedAtNanos).toSeconds();
        if (elapsed > WINDOW_SECONDS) {
            return "elapsed " + elapsed + "s exceeded the " + WINDOW_SECONDS
                    + "s rate-limit window, so the counter reset mid-scenario";
        }
        return "elapsed " + elapsed + "s, within the " + WINDOW_SECONDS + "s rate-limit window";
    }

    private long retryAfterSeconds() {
        for (Map.Entry<String, Collection<String>> header : lastError.responseHeaders().entrySet()) {
            if (HttpHeaders.RETRY_AFTER.equalsIgnoreCase(header.getKey())) {
                return Long.parseLong(header.getValue().iterator().next());
            }
        }
        throw new AssertionError("no " + HttpHeaders.RETRY_AFTER + " header on the 429 response");
    }
}
