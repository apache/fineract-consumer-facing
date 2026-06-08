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
import java.util.UUID;
import org.apache.fineract.consumer.client.ApiClient;
import org.apache.fineract.consumer.client.api.RegistrationCommandControllerApi;
import org.apache.fineract.consumer.client.model.SendOtpCommandRequest;
import org.apache.fineract.consumer.client.model.SubmitRegistrationCommandData;
import org.apache.fineract.consumer.client.model.SubmitRegistrationCommandRequest;
import org.apache.fineract.consumer.client.model.VerifyOtpCommandData;
import org.apache.fineract.consumer.client.model.VerifyOtpCommandRequest;
import org.apache.fineract.consumer.cucumber.helpers.FineractSeeder;
import org.apache.fineract.consumer.cucumber.helpers.MailpitProbe;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.registration.command.exception.IdentityNotVerifiedException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class RegistrationSteps {

    private static final String BFF_BASE_URL = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    private static final String DEVICE_FINGERPRINT = "cucumber-test-device";
    private static final String WRONG_OTP = "WRONG1";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final FineractSeeder fineractSeed = new FineractSeeder();
    private final MailpitProbe mailpit = new MailpitProbe();
    private final RegistrationCommandControllerApi bff = buildBffClient();

    private FineractSeeder.SeededClient seededClient;
    private String email;
    private UUID registrationId;
    private SubmitRegistrationCommandData lastSubmit;
    private VerifyOtpCommandData lastVerify;
    private FeignException lastError;
    private String otpToken;

    @Given("a fresh Fineract client exists with a Passport identifier")
    public void freshFineractClient() {
        seededClient = fineractSeed.seedClientWithPassport();
        email = "user-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    @When("I submit registration with the matching Passport")
    public void submitMatching() {
        submit(seededClient.documentKey());
    }

    @When("I submit registration with a non-matching Passport value")
    public void submitMismatch() {
        submit("WRONG-VALUE-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
    }

    @Then("registration is accepted in PENDING_OTP state")
    public void acceptedPendingOtp() {
        assertThat(lastError).as("expected success, got error").isNull();
        assertThat(lastSubmit).isNotNull();
        assertThat(lastSubmit.getRegistrationId()).isNotNull();
        assertThat(lastSubmit.getStatus()).isEqualTo(SubmitRegistrationCommandData.StatusEnum.PENDING_OTP);
        registrationId = lastSubmit.getRegistrationId();
    }

    @Then("registration is rejected")
    public void registrationRejected() {
        assertThat(lastError).as("expected rejection, got success").isNotNull();
        assertThat(lastError.status()).isEqualTo(403);
        assertThat(readCode(lastError.contentUTF8())).isEqualTo(IdentityNotVerifiedException.CODE);
    }

    @Then("the rejection does not reveal which field failed")
    public void rejectionDoesNotRevealField() {
        String body = lastError.contentUTF8();
        assertThat(body)
                .doesNotContain("documentKey")
                .doesNotContain("documentTypeName")
                .doesNotContain("fineractClientId");
    }

    @When("I request an email OTP")
    public void requestEmailOtp() {
        SendOtpCommandRequest request = new SendOtpCommandRequest()
                .registrationId(registrationId)
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME);
        try {
            bff.sendOtp(request);
            lastError = null;
        } catch (FeignException e) {
            lastError = e;
        }
    }

    @Then("an OTP is delivered to my email")
    public void otpDelivered() {
        assertThat(lastError).as("expected OTP send success, got error").isNull();
    }

    @When("I retrieve the OTP from Mailpit")
    public void retrieveOtpFromMailpit() {
        otpToken = mailpit.waitForOtp(email);
        assertThat(otpToken).isNotBlank();
    }

    @When("I verify the OTP")
    public void verifyCorrectOtp() {
        verifyWithToken(otpToken);
    }

    @When("I verify a wrong OTP")
    public void verifyWrongOtp() {
        verifyWithToken(WRONG_OTP);
    }

    @When("I verify the same OTP a second time")
    public void verifyOtpAgain() {
        verifyWithToken(otpToken);
    }

    @When("I complete an OTP verification successfully")
    public void completeOtpVerification() {
        freshFineractClient();
        submitMatching();
        acceptedPendingOtp();
        requestEmailOtp();
        otpDelivered();
        retrieveOtpFromMailpit();
        verifyCorrectOtp();
        advancedToPending2fa();
    }

    @Then("my registration advances to PENDING_2FA")
    public void advancedToPending2fa() {
        assertThat(lastError).as("expected verify success, got error").isNull();
        assertThat(lastVerify).isNotNull();
        assertThat(lastVerify.getStatus()).isEqualTo(VerifyOtpCommandData.StatusEnum.PENDING_2_FA);
    }

    @Then("the OTP is rejected as invalid")
    public void otpRejected() {
        assertThat(lastError).as("expected OTP rejection, got success").isNotNull();
        assertThat(lastError.status()).isEqualTo(400);
        assertThat(readCode(lastError.contentUTF8())).isEqualTo(OtpTokenInvalidException.CODE);
    }

    private void submit(String documentKey) {
        SubmitRegistrationCommandRequest request = new SubmitRegistrationCommandRequest()
                .fineractClientId(seededClient.fineractClientId())
                .email(email)
                .documentTypeName(seededClient.documentTypeName())
                .documentKey(documentKey);
        try {
            lastSubmit = bff.submit(DEVICE_FINGERPRINT, request);
            lastError = null;
        } catch (FeignException e) {
            lastError = e;
            lastSubmit = null;
        }
    }

    private void verifyWithToken(String token) {
        VerifyOtpCommandRequest request = new VerifyOtpCommandRequest()
                .registrationId(registrationId)
                .token(token);
        try {
            lastVerify = bff.verifyOtp(request);
            lastError = null;
        } catch (FeignException e) {
            lastError = e;
            lastVerify = null;
        }
    }

    private static String readCode(String body) {
        try {
            return JSON.readTree(body).path("code").asString();
        } catch (Exception e) {
            throw new IllegalStateException("could not parse error response body: " + body, e);
        }
    }

    private static RegistrationCommandControllerApi buildBffClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(BFF_BASE_URL);
        return apiClient.buildClient(RegistrationCommandControllerApi.class);
    }
}
