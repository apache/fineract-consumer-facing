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
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.client.ApiClient;
import org.apache.fineract.consumer.client.api.SavingsQueryControllerApi;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.infrastructure.access.filter.DeviceFingerprintFilter;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class AbacDenialSteps {

    private static final String BFF_BASE_URL = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    private static final String DEVICE_FINGERPRINT = "cucumber-abac-device-a";
    private static final String OTHER_DEVICE_FINGERPRINT = "cucumber-abac-device-b";
    private static final int UNAUTHORIZED = 401;
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final LoginHelper loginHelper = new LoginHelper();

    private String accessToken;
    private FeignException lastError;

    @Given("a logged-in customer on a known device")
    public void loggedInCustomerOnKnownDevice() {
        RegistrationHelper.BoundUserWithAccounts user = registrationHelper.registerBoundUserWithAccounts();
        accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
    }

    @When("I call a protected endpoint with a different device fingerprint")
    public void callWithDifferentFingerprint() {
        SavingsQueryControllerApi client =
                ConsumerApiClientFactory.authenticated(SavingsQueryControllerApi.class, accessToken, OTHER_DEVICE_FINGERPRINT);
        lastError = captureError(client::listSavingsAccounts);
    }

    @When("I call a protected endpoint with no device fingerprint header")
    public void callWithoutFingerprintHeader() {
        lastError = captureError(() -> noFingerprintClient().listSavingsAccounts());
    }

    @Then("the request is rejected with the device mismatch code")
    public void rejectedWithDeviceMismatchCode() {
        assertThat(lastError.status()).isEqualTo(UNAUTHORIZED);
        assertThat(readCode(lastError.contentUTF8())).isEqualTo(DeviceFingerprintFilter.CODE);
    }

    private SavingsQueryControllerApi noFingerprintClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(BFF_BASE_URL);
        apiClient.getFeignBuilder().requestInterceptor(template -> template.header(HttpHeaders.COOKIE,
                AuthenticationConstants.ACCESS_TOKEN_COOKIE_NAME + "=" + accessToken));
        return apiClient.buildClient(SavingsQueryControllerApi.class);
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
        return JSON.readTree(body).path("code").asString();
    }
}
