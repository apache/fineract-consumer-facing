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
import feign.Response;
import feign.Util;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.authentication.command.exception.InvalidCredentialsException;
import org.apache.fineract.consumer.authentication.command.exception.RefreshTokenInvalidException;
import org.apache.fineract.consumer.authentication.command.exception.TwoFactorInvalidException;
import org.apache.fineract.consumer.cucumber.clients.AuthenticationClient;
import org.apache.fineract.consumer.cucumber.clients.MailpitClient;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class LoginSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-login-device";
    private static final String OTHER_DEVICE_FINGERPRINT = "cucumber-other-device";
    private static final String WRONG_PASSWORD = "Wrong-password-123";
    private static final String WRONG_OTP = "WRONG1";
    private static final String SET_COOKIE_HEADER = "set-cookie";
    private static final String EXPECTED_LOGIN_REJECTED = "expected login to be rejected";
    private static final String EXPECTED_TWO_FACTOR_REJECTED = "expected two-factor verification to be rejected";
    private static final String EXPECTED_LOGOUT_REJECTED = "expected logout to be rejected";
    private static final String EXPECTED_REFRESH_REJECTED = "expected refresh to be rejected";
    private static final String ACCESS_COOKIE_PREFIX = AuthenticationConstants.ACCESS_TOKEN_COOKIE_NAME + "=";
    private static final String REFRESH_COOKIE_PREFIX = AuthenticationConstants.REFRESH_TOKEN_COOKIE_NAME + "=";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    public record AuthResponse(int status, JsonNode body, String accessCookie, String refreshCookie) {}

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final MailpitClient mailpit = new MailpitClient();
    private final AuthenticationClient authClient = new AuthenticationClient();

    private RegistrationHelper.BoundUser user;
    private AuthResponse lastResponse;
    private String challengeToken;
    private String otpToken;
    private String accessToken;
    private String currentRefreshCookie;
    private String previousRefreshCookie;

    @Given("a registered and bound user exists")
    public void registeredAndBoundUser() {
        user = registrationHelper.registerBoundUser();
    }

    @When("I log in with my correct password")
    public void loginWithCorrectPassword() {
        login(user.email(), user.password());
    }

    @When("I log in with a wrong password")
    public void loginWithWrongPassword() {
        clearLoginInbox();
        try {
            authClient.login(user.email(), WRONG_PASSWORD, DEVICE_FINGERPRINT);
            fail(EXPECTED_LOGIN_REJECTED);
        } catch (FeignException e) {
            lastResponse = toAuthResponse(e);
        }
    }

    @When("I log in with an unknown email")
    public void loginWithUnknownEmail() {
        clearLoginInbox();
        String unknownEmail = "unknown-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
        try {
            authClient.login(unknownEmail, user.password(), DEVICE_FINGERPRINT);
            fail(EXPECTED_LOGIN_REJECTED);
        } catch (FeignException e) {
            lastResponse = toAuthResponse(e);
        }
    }

    @Then("I receive a login challenge sent to my masked email")
    public void receivedLoginChallenge() {
        assertThat(challengeToken).isNotBlank();
        String sentTo = lastResponse.body().path("sentTo").asString();
        assertThat(sentTo)
                .isNotEqualTo(user.email())
                .startsWith(user.email().substring(0, 1))
                .contains("***")
                .endsWith(user.email().substring(user.email().indexOf('@')));
    }

    @When("I retrieve the login OTP from Mailpit")
    public void retrieveLoginOtp() {
        otpToken = mailpit.waitForOtp(user.email());
        assertThat(otpToken).isNotBlank();
    }

    @When("I verify the login OTP")
    public void verifyLoginOtp() {
        verifyTwoFactor(otpToken, DEVICE_FINGERPRINT);
    }

    @When("I verify a wrong login OTP")
    public void verifyWrongLoginOtp() {
        try {
            authClient.verifyTwoFactor(challengeToken, WRONG_OTP, DEVICE_FINGERPRINT);
            fail(EXPECTED_TWO_FACTOR_REJECTED);
        } catch (FeignException e) {
            lastResponse = toAuthResponse(e);
        }
    }

    @When("I verify the same login OTP again")
    public void verifySameLoginOtpAgain() {
        try {
            authClient.verifyTwoFactor(challengeToken, otpToken, DEVICE_FINGERPRINT);
            fail(EXPECTED_TWO_FACTOR_REJECTED);
        } catch (FeignException e) {
            lastResponse = toAuthResponse(e);
        }
    }

    @When("I complete a login successfully")
    public void completeLoginSuccessfully() {
        loginWithCorrectPassword();
        receivedLoginChallenge();
        retrieveLoginOtp();
        verifyLoginOtp();
        receivedSession();
    }

    @Then("I receive a session with an access token and refresh cookie")
    public void receivedSession() {
        assertThat(accessToken).isNotBlank();
        assertThat(currentRefreshCookie).isNotBlank();
        assertThat(lastResponse.body().path("expiresAt").asString()).isNotBlank();
    }

    @Then("a protected endpoint accepts the access token")
    public void protectedEndpointAcceptsAccessToken() {
        authClient.logout(accessToken, currentRefreshCookie, DEVICE_FINGERPRINT);
    }

    @Then("a protected endpoint rejects the challenge token as an access cookie")
    public void protectedEndpointRejectsChallengeToken() {
        try {
            authClient.logout(challengeToken, null, DEVICE_FINGERPRINT);
            fail(EXPECTED_LOGOUT_REJECTED);
        } catch (FeignException e) {
            assertThat(e.status()).isEqualTo(401);
        }
    }

    @Then("the login is rejected with a generic credentials error")
    public void loginRejectedGenerically() {
        assertThat(lastResponse.status()).isEqualTo(401);
        assertThat(lastResponse.body().path("code").asString()).isEqualTo(InvalidCredentialsException.CODE);
        assertThat(lastResponse.body().toString()).doesNotContain(user.email());
    }

    @Then("the two-factor verification is rejected")
    public void twoFactorRejected() {
        assertThat(lastResponse.status()).isEqualTo(401);
        assertThat(lastResponse.body().path("code").asString()).isEqualTo(TwoFactorInvalidException.CODE);
    }

    @When("I refresh my session")
    public void refreshSession() {
        refresh(currentRefreshCookie, DEVICE_FINGERPRINT);
    }

    @When("I refresh using the previous refresh cookie")
    public void refreshWithPreviousCookie() {
        refresh(previousRefreshCookie, DEVICE_FINGERPRINT);
    }

    @When("I refresh using the previous refresh cookie from a different device")
    public void refreshWithPreviousCookieFromDifferentDevice() {
        try {
            authClient.refresh(previousRefreshCookie, OTHER_DEVICE_FINGERPRINT);
            fail(EXPECTED_REFRESH_REJECTED);
        } catch (FeignException e) {
            lastResponse = toAuthResponse(e);
        }
    }

    @When("I refresh using the latest refresh cookie")
    public void refreshWithLatestCookie() {
        try {
            authClient.refresh(currentRefreshCookie, DEVICE_FINGERPRINT);
            fail(EXPECTED_REFRESH_REJECTED);
        } catch (FeignException e) {
            lastResponse = toAuthResponse(e);
        }
    }

    @When("I refresh my session from a different device")
    public void refreshFromDifferentDevice() {
        try {
            authClient.refresh(currentRefreshCookie, OTHER_DEVICE_FINGERPRINT);
            fail(EXPECTED_REFRESH_REJECTED);
        } catch (FeignException e) {
            lastResponse = toAuthResponse(e);
        }
    }

    @Then("the refresh is rejected")
    public void refreshRejected() {
        assertThat(lastResponse.status()).isEqualTo(401);
        assertThat(lastResponse.body().path("code").asString()).isEqualTo(RefreshTokenInvalidException.CODE);
    }

    private void login(String email, String password) {
        clearLoginInbox();
        lastResponse = toAuthResponse(authClient.login(email, password, DEVICE_FINGERPRINT));
        challengeToken = lastResponse.body().path("challengeToken").asString();
    }

    private void verifyTwoFactor(String otp, String deviceFingerprint) {
        lastResponse = toAuthResponse(authClient.verifyTwoFactor(challengeToken, otp, deviceFingerprint));
        accessToken = lastResponse.accessCookie();
        previousRefreshCookie = currentRefreshCookie;
        currentRefreshCookie = lastResponse.refreshCookie();
    }

    private void refresh(String refreshCookie, String deviceFingerprint) {
        lastResponse = toAuthResponse(authClient.refresh(refreshCookie, deviceFingerprint));
        accessToken = lastResponse.accessCookie();
        previousRefreshCookie = currentRefreshCookie;
        currentRefreshCookie = lastResponse.refreshCookie();
    }

    private void clearLoginInbox() {
        mailpit.deleteMessages(user.email());
    }

    private static AuthResponse toAuthResponse(Response response) {
        try (response) {
            String body = response.body() == null
                    ? null
                    : Util.toString(response.body().asReader(StandardCharsets.UTF_8));
            return new AuthResponse(response.status(), parseJson(body),
                    extractCookie(response.headers(), ACCESS_COOKIE_PREFIX),
                    extractCookie(response.headers(), REFRESH_COOKIE_PREFIX));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read authentication response body", e);
        }
    }

    private static AuthResponse toAuthResponse(FeignException e) {
        return new AuthResponse(e.status(), parseJson(e.contentUTF8()),
                extractCookie(e.responseHeaders(), ACCESS_COOKIE_PREFIX),
                extractCookie(e.responseHeaders(), REFRESH_COOKIE_PREFIX));
    }

    private static JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) {
            return JSON.missingNode();
        }
        try {
            return JSON.readTree(body);
        } catch (Exception ex) {
            return JSON.missingNode();
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
}
