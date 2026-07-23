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
import feign.Response;
import feign.Util;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.client.api.UserCommandControllerApi;
import org.apache.fineract.consumer.client.api.UserQueryControllerApi;
import org.apache.fineract.consumer.client.model.ForgotPasswordCommandRequest;
import org.apache.fineract.consumer.client.model.InitiatePasswordChangeCommandRequest;
import org.apache.fineract.consumer.client.model.ResetPasswordCommandRequest;
import org.apache.fineract.consumer.client.model.UserPasswordChangeChallengeCommandData;
import org.apache.fineract.consumer.cucumber.clients.AuthenticationClient;
import org.apache.fineract.consumer.cucumber.clients.MailpitClient;
import org.apache.fineract.consumer.cucumber.clients.UserPasswordClient;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.user.command.exception.UserPasswordInvalidException;
import org.apache.fineract.consumer.user.command.exception.UserPasswordResetInvalidException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class PasswordSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-password-device";
    private static final String NEW_PASSWORD = "Cucumber-changed-pw1";
    private static final String WRONG_PASSWORD = "Wrong-password-123";
    private static final String WRONG_OTP = "WRONG1";
    private static final String UNKNOWN_EMAIL = "unknown-user@cucumber.test";
    private static final String SET_COOKIE_HEADER = "set-cookie";
    private static final String ACCESS_COOKIE_PREFIX = AuthenticationConstants.ACCESS_TOKEN_COOKIE_NAME + "=";
    private static final String REFRESH_COOKIE_PREFIX = AuthenticationConstants.REFRESH_TOKEN_COOKIE_NAME + "=";
    private static final int OK = 200;
    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final MailpitClient mailpit = new MailpitClient();
    private final AuthenticationClient authClient = new AuthenticationClient();
    private final UserPasswordClient userPasswordClient = new UserPasswordClient();

    private RegistrationHelper.BoundUser user;
    private String accessToken;
    private String refreshCookie;
    private String newAccessToken;
    private String newRefreshCookie;
    private UserCommandControllerApi commandApi;
    private UserPasswordChangeChallengeCommandData challenge;
    private String otp;
    private FeignException lastError;
    private boolean resetRequestAccepted;

    @Given("a logged-in customer wanting to change their password")
    public void loggedInPasswordCustomer() {
        user = registrationHelper.registerBoundUser();
        Session session = login(user.email(), user.password());
        accessToken = session.accessToken();
        refreshCookie = session.refreshCookie();
        commandApi = ConsumerApiClientFactory.authenticated(
                UserCommandControllerApi.class, accessToken, DEVICE_FINGERPRINT);
    }

    @When("I initiate a password change with my current password")
    public void initiateWithCurrentPassword() {
        mailpit.deleteMessages(user.email());
        challenge = commandApi.initiatePasswordChange(DEVICE_FINGERPRINT,
                new InitiatePasswordChangeCommandRequest().currentPassword(user.password()));
        assertThat(challenge.getStepUpToken()).isNotBlank();
        assertThat(challenge.getSentTo()).isNotEqualTo(user.email()).contains("***");
    }

    @When("I retrieve the password change OTP from Mailpit")
    @When("I retrieve the password reset OTP from Mailpit")
    public void retrievePasswordChangeOtp() {
        otp = mailpit.waitForOtp(user.email());
        assertThat(otp).isNotBlank();
    }

    @When("I confirm the password change with the OTP and a new password")
    public void confirmPasswordChange() {
        awaitNextJwtSecond();
        try (Response response = userPasswordClient.confirmPasswordChange(
                accessToken, DEVICE_FINGERPRINT, challenge.getStepUpToken(), otp, NEW_PASSWORD)) {
            assertThat(response.status()).isEqualTo(OK);
            newAccessToken = extractCookie(response.headers(), ACCESS_COOKIE_PREFIX);
            newRefreshCookie = extractCookie(response.headers(), REFRESH_COOKIE_PREFIX);
        }
    }

    @Then("I receive fresh session cookies and stay logged in")
    public void freshSessionCookiesKeepMeLoggedIn() {
        assertThat(newAccessToken).isNotBlank().isNotEqualTo(accessToken);
        assertThat(newRefreshCookie).isNotBlank().isNotEqualTo(refreshCookie);
        UserQueryControllerApi freshSessionApi = ConsumerApiClientFactory.authenticated(
                UserQueryControllerApi.class, newAccessToken, DEVICE_FINGERPRINT);
        assertThat(freshSessionApi.getUserProfile()).isNotNull();
    }

    @Then("my old access token is rejected")
    public void oldAccessTokenRejected() {
        UserQueryControllerApi oldSessionApi = ConsumerApiClientFactory.authenticated(
                UserQueryControllerApi.class, accessToken, DEVICE_FINGERPRINT);
        try {
            oldSessionApi.getUserProfile();
            throw new AssertionError("Expected the old access token to be rejected, but it was accepted");
        } catch (FeignException e) {
            assertThat(e.status()).isEqualTo(UNAUTHORIZED);
        }
    }

    @Then("my old refresh cookie is rejected")
    public void oldRefreshCookieRejected() {
        try {
            authClient.refresh(refreshCookie, DEVICE_FINGERPRINT);
            throw new AssertionError("Expected the old refresh cookie to be rejected, but it was accepted");
        } catch (FeignException e) {
            assertThat(e.status()).isEqualTo(UNAUTHORIZED);
        }
    }

    @Then("I can log in with my new password")
    public void loginWithNewPassword() {
        Session session = login(user.email(), NEW_PASSWORD);
        assertThat(session.accessToken()).isNotBlank();
        assertThat(session.refreshCookie()).isNotBlank();
    }

    @When("I initiate a password change with a wrong current password")
    public void initiateWithWrongCurrentPassword() {
        try {
            commandApi.initiatePasswordChange(DEVICE_FINGERPRINT,
                    new InitiatePasswordChangeCommandRequest().currentPassword(WRONG_PASSWORD));
            throw new AssertionError("Expected the password change to be rejected, but it was accepted");
        } catch (FeignException e) {
            lastError = e;
        }
    }

    @Then("the password change is rejected as invalid credentials")
    public void passwordChangeRejected() {
        assertThat(lastError.status()).isEqualTo(FORBIDDEN);
        assertThat(readCode(lastError.contentUTF8())).isEqualTo(UserPasswordInvalidException.CODE);
    }

    @When("I request a password reset for my email")
    public void requestPasswordReset() {
        mailpit.deleteMessages(user.email());
        unauthenticatedApi().forgotPassword(DEVICE_FINGERPRINT,
                new ForgotPasswordCommandRequest().email(user.email()));
    }

    @When("I reset my password with the OTP and a new password")
    public void resetPasswordWithOtp() {
        unauthenticatedApi().resetPassword(DEVICE_FINGERPRINT, new ResetPasswordCommandRequest()
                .email(user.email())
                .otp(otp)
                .newPassword(NEW_PASSWORD));
    }

    @Then("I can log in with my new password while still sending the revoked access cookie")
    public void loginWithNewPasswordCarryingRevokedAccessCookie() {
        mailpit.deleteMessages(user.email());
        JsonNode loginBody = readBody(
                authClient.loginWithAccessCookie(user.email(), NEW_PASSWORD, DEVICE_FINGERPRINT, accessToken));
        assertThat(loginBody.path("challengeToken").asString()).isNotBlank();
    }

    @When("I request a password reset for an unknown email")
    public void requestPasswordResetForUnknownEmail() {
        unauthenticatedApi().forgotPassword(DEVICE_FINGERPRINT,
                new ForgotPasswordCommandRequest().email(UNKNOWN_EMAIL));
        resetRequestAccepted = true;
    }

    @Then("the reset request is accepted")
    public void resetRequestAccepted() {
        assertThat(resetRequestAccepted).isTrue();
    }

    @When("I reset my password with a wrong OTP")
    public void resetPasswordWithWrongOtp() {
        try {
            unauthenticatedApi().resetPassword(DEVICE_FINGERPRINT, new ResetPasswordCommandRequest()
                    .email(user.email())
                    .otp(WRONG_OTP)
                    .newPassword(NEW_PASSWORD));
            throw new AssertionError("Expected the password reset to be rejected, but it was accepted");
        } catch (FeignException e) {
            lastError = e;
        }
    }

    @Then("the password reset is rejected as invalid")
    public void passwordResetRejected() {
        assertThat(lastError.status()).isEqualTo(BAD_REQUEST);
        assertThat(readCode(lastError.contentUTF8())).isEqualTo(UserPasswordResetInvalidException.CODE);
    }

    private static void awaitNextJwtSecond() {
        long millisIntoSecond = Instant.now().toEpochMilli() % 1000;
        try {
            Thread.sleep(1000 - millisIntoSecond + 50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for the next JWT second", e);
        }
    }

    private static UserCommandControllerApi unauthenticatedApi() {
        return ConsumerApiClientFactory.unauthenticated(UserCommandControllerApi.class);
    }

    private record Session(String accessToken, String refreshCookie) {}

    private Session login(String email, String password) {
        mailpit.deleteMessages(email);
        JsonNode loginBody = readBody(authClient.login(email, password, DEVICE_FINGERPRINT));
        String challengeToken = loginBody.path("challengeToken").asString();
        String loginOtp = mailpit.waitForOtp(email);
        try (Response response = authClient.verifyTwoFactor(challengeToken, loginOtp, DEVICE_FINGERPRINT)) {
            return new Session(
                    extractCookie(response.headers(), ACCESS_COOKIE_PREFIX),
                    extractCookie(response.headers(), REFRESH_COOKIE_PREFIX));
        }
    }

    private static JsonNode readBody(Response response) {
        try (response) {
            String body = response.body() == null
                    ? null
                    : Util.toString(response.body().asReader(StandardCharsets.UTF_8));
            return body == null || body.isBlank() ? JSON.missingNode() : JSON.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read authentication response body", e);
        }
    }

    private static String extractCookie(Map<String, Collection<String>> headers, String prefix) {
        return headers.entrySet().stream()
                .filter(entry -> SET_COOKIE_HEADER.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .filter(value -> value.startsWith(prefix))
                .map(value -> value.substring(prefix.length(), cookieValueEnd(value)))
                .findFirst()
                .orElse(null);
    }

    private static int cookieValueEnd(String setCookieValue) {
        int semicolon = setCookieValue.indexOf(';');
        return semicolon >= 0 ? semicolon : setCookieValue.length();
    }

    private static String readCode(String body) {
        try {
            return JSON.readTree(body).path("code").asString();
        } catch (Exception e) {
            throw new IllegalStateException("could not parse error response body: " + body, e);
        }
    }
}
