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
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.fineract.consumer.audit.command.data.AuditEventSource;
import org.apache.fineract.consumer.client.api.SavingsQueryControllerApi;
import org.apache.fineract.consumer.cucumber.clients.OpenBankingClient;
import org.apache.fineract.consumer.cucumber.helpers.AuditEventPoller;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.audit.data.AuditEventType;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.apache.fineract.consumer.openbanking.command.data.ConsentStatus;
import org.apache.fineract.consumer.openbanking.command.data.OpenBankingPermission;
import org.apache.fineract.consumer.openbanking.command.exception.OpenBankingConsentNotRevocableException;
import org.apache.fineract.consumer.openbanking.query.domain.OpenBankingAccountId;
import org.apache.fineract.consumer.openbanking.query.exception.OpenBankingConsentInvalidException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class OpenBankingSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-openbanking-device";
    private static final String PKCE_CHALLENGE_METHOD_S256 = "S256";
    private static final String UNKNOWN_PERMISSION = "ReadNothing";
    private static final String STATUS_FIELD = "status";
    private static final String PERMISSION_SEPARATOR = ",";
    private static final String SCOPE_SEPARATOR = " ";
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int FOUND = 302;
    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int CONFLICT = 409;
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String COUNT_CONSENT_SERVER_EVENT_SQL =
            "SELECT count(*) FROM audit_events WHERE event_type = ? AND source = ? "
                    + "AND details->>'consentId' = ?";

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final LoginHelper loginHelper = new LoginHelper();
    private final OpenBankingClient openBankingClient = new OpenBankingClient();

    private RegistrationHelper.BoundUserWithAccounts user;
    private String accessToken;
    private String ccToken;
    private String consentId;
    private String tppState;
    private String codeVerifier;
    private Map<String, String> consentPageParams;
    private String xsrfToken;
    private String authorizationCode;
    private String openBankingAccessToken;
    private String openBankingRefreshToken;
    private String previousRefreshToken;
    private HttpResponse<String> lastResponse;
    private FeignException lastFeignError;


    @Given("a logged-in open banking customer with accounts")
    public void loggedInOpenBankingCustomer() {
        if (user != null) {
            return;
        }
        user = registrationHelper.registerBoundUserWithAccounts();
        accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
    }

    @Given("the TPP holds a client-credentials token")
    public void tppHoldsClientCredentialsToken() {
        lastResponse = openBankingClient.requestToken(Map.of(
                OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.CLIENT_CREDENTIALS.getValue(),
                OAuth2ParameterNames.SCOPE, AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS));
        assertThat(lastResponse.statusCode()).isEqualTo(OK);
        ccToken = readBody(lastResponse).path(OAuth2ParameterNames.ACCESS_TOKEN).asString();
        assertThat(ccToken).isNotBlank();
    }

    @Given("an authorised open banking consent with an access token")
    public void authorisedConsentWithAccessToken() {
        grantConsent(OpenBankingPermission.READ_ACCOUNTS_BASIC.getCode()
                + PERMISSION_SEPARATOR + OpenBankingPermission.READ_BALANCES.getCode());
        exchangeCodeWithCorrectVerifier();
        tppReceivesAccessToken();
    }

    @Given("an authorised open banking consent limited to permission {string}")
    public void authorisedConsentLimitedToPermission(String permission) {
        grantConsent(permission);
        exchangeCodeWithCorrectVerifier();
        tppReceivesAccessToken();
    }

    @Given("a granted open banking consent with a pending authorization code")
    public void grantedConsentWithPendingCode() {
        grantConsent(OpenBankingPermission.READ_ACCOUNTS_BASIC.getCode()
                + PERMISSION_SEPARATOR + OpenBankingPermission.READ_BALANCES.getCode());
    }


    @When("the TPP creates an account access consent with permissions {string}")
    public void tppCreatesConsent(String permissions) {
        List<String> codes = Arrays.stream(permissions.split(PERMISSION_SEPARATOR))
                .map(code -> OpenBankingPermission.fromCode(code).getCode())
                .collect(Collectors.toList());
        lastResponse = openBankingClient.createConsent(ccToken, permissionsBody(codes));
        JsonNode body = readBody(lastResponse);
        if (lastResponse.statusCode() == CREATED) {
            consentId = body.path("consentId").asString();
            assertThat(consentId).isNotBlank();
        }
    }

    @When("the TPP creates an account access consent with an unknown permission")
    public void tppCreatesConsentWithUnknownPermission() {
        lastResponse = openBankingClient.createConsent(ccToken, permissionsBody(List.of(UNKNOWN_PERMISSION)));
    }

    @Then("the consent is created as {string}")
    public void consentIsCreatedAs(String expectedStatus) {
        assertThat(lastResponse.statusCode()).isEqualTo(CREATED);
        assertThat(readBody(lastResponse).path(STATUS_FIELD).asString())
                .isEqualTo(ConsentStatus.valueOf(expectedStatus).name());
    }

    @Then("the consent creation is rejected as a bad request")
    public void consentCreationRejectedAsBadRequest() {
        assertThat(lastResponse.statusCode()).isEqualTo(BAD_REQUEST);
    }


    @When("the user opens the authorize link without a login session")
    public void openAuthorizeLinkWithoutSession() {
        lastResponse = openBankingClient.authorize(null, authorizeParams(consentId));
    }

    @Then("the browser is redirected to the login page with a return url")
    public void redirectedToLoginPage() {
        assertThat(lastResponse.statusCode()).isEqualTo(FOUND);
        String location = locationHeader(lastResponse);
        assertThat(location).startsWith(
                OpenBankingClient.FRONTEND_BASE_URL + OAuth2Constants.LOGIN_PAGE_PATH + "?");
        assertThat(queryParams(location)).containsKey(OAuth2Constants.RETURN_URL_PARAM);
    }

    @When("the user opens the authorize link with their login session")
    public void openAuthorizeLinkWithSession() {
        lastResponse = openBankingClient.authorize(accessToken, authorizeParams(consentId));
    }

    @When("the user opens an authorize link with an unknown consent id")
    public void openAuthorizeLinkWithUnknownConsentId() {
        lastResponse = openBankingClient.authorize(accessToken, authorizeParams(UUID.randomUUID().toString()));
    }

    @When("the user opens an authorize link with the already authorised consent id")
    public void openAuthorizeLinkWithAuthorisedConsentId() {
        lastResponse = openBankingClient.authorize(accessToken, authorizeParams(consentId));
    }

    @Then("the browser is redirected to the consent page")
    public void redirectedToConsentPage() {
        assertThat(lastResponse.statusCode()).isEqualTo(FOUND);
        String location = locationHeader(lastResponse);
        assertThat(location).startsWith(
                OpenBankingClient.FRONTEND_BASE_URL + OAuth2Constants.CONSENT_PAGE_PATH + "?");
        consentPageParams = queryParams(location);
        assertThat(consentPageParams.get(OAuth2ParameterNames.CLIENT_ID))
                .isEqualTo(OpenBankingClient.TPP_CLIENT_ID);
        assertThat(consentPageParams).containsKeys(OAuth2ParameterNames.SCOPE, OAuth2ParameterNames.STATE);
        xsrfToken = xsrfCookie(lastResponse);
        assertThat(xsrfToken).isNotBlank();
    }

    @When("the user approves the consent on the consent page")
    public void approveConsentOnConsentPage() {
        lastResponse = openBankingClient.submitConsentDecision(accessToken, xsrfToken, decisionFields(true));
    }

    @When("the user denies the consent on the consent page")
    public void denyConsentOnConsentPage() {
        lastResponse = openBankingClient.submitConsentDecision(accessToken, xsrfToken, decisionFields(false));
    }

    @Then("the TPP callback receives an authorization code")
    public void callbackReceivesAuthorizationCode() {
        Map<String, String> callbackParams = callbackParams();
        authorizationCode = callbackParams.get(OAuth2ParameterNames.CODE);
        assertThat(authorizationCode).isNotBlank();
        assertThat(callbackParams.get(OAuth2ParameterNames.STATE)).isEqualTo(tppState);
    }

    @Then("the TPP callback receives an access denied error")
    public void callbackReceivesAccessDenied() {
        Map<String, String> callbackParams = callbackParams();
        assertThat(callbackParams.get(OAuth2ParameterNames.ERROR)).isEqualTo(OAuth2ErrorCodes.ACCESS_DENIED);
        assertThat(callbackParams).doesNotContainKey(OAuth2ParameterNames.CODE);
    }

    @Then("the TPP callback receives an invalid request error and no code")
    public void callbackReceivesInvalidRequest() {
        Map<String, String> callbackParams = callbackParams();
        assertThat(callbackParams.get(OAuth2ParameterNames.ERROR)).isEqualTo(OAuth2ErrorCodes.INVALID_REQUEST);
        assertThat(callbackParams).doesNotContainKey(OAuth2ParameterNames.CODE);
    }


    @When("the TPP exchanges the code using the correct PKCE verifier")
    public void exchangeCodeWithCorrectVerifier() {
        lastResponse = exchangeCode(codeVerifier);
    }

    @When("the TPP exchanges the code using a wrong PKCE verifier")
    public void exchangeCodeWithWrongVerifier() {
        lastResponse = exchangeCode(randomUrlSafeToken());
    }

    @Then("the TPP receives an open banking access token")
    public void tppReceivesAccessToken() {
        assertThat(lastResponse.statusCode()).isEqualTo(OK);
        JsonNode body = readBody(lastResponse);
        openBankingAccessToken = body.path(OAuth2ParameterNames.ACCESS_TOKEN).asString();
        openBankingRefreshToken = body.path(OAuth2ParameterNames.REFRESH_TOKEN).asString();
        assertThat(openBankingAccessToken).isNotBlank();
        assertThat(openBankingRefreshToken).isNotBlank();
    }

    @When("the TPP refreshes the open banking token")
    public void tppRefreshesToken() {
        previousRefreshToken = openBankingRefreshToken;
        lastResponse = openBankingClient.requestToken(Map.of(
                OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.REFRESH_TOKEN.getValue(),
                OAuth2ParameterNames.REFRESH_TOKEN, openBankingRefreshToken));
    }

    @Then("the TPP receives a new open banking access token and a rotated refresh token")
    public void tppReceivesRotatedTokens() {
        assertThat(lastResponse.statusCode()).isEqualTo(OK);
        JsonNode body = readBody(lastResponse);
        String newAccessToken = body.path(OAuth2ParameterNames.ACCESS_TOKEN).asString();
        String newRefreshToken = body.path(OAuth2ParameterNames.REFRESH_TOKEN).asString();
        assertThat(newAccessToken).isNotBlank().isNotEqualTo(openBankingAccessToken);
        assertThat(newRefreshToken).isNotBlank().isNotEqualTo(previousRefreshToken);
        openBankingAccessToken = newAccessToken;
        openBankingRefreshToken = newRefreshToken;
    }

    @When("the TPP replays the previous refresh token")
    public void tppReplaysPreviousRefreshToken() {
        lastResponse = openBankingClient.requestToken(Map.of(
                OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.REFRESH_TOKEN.getValue(),
                OAuth2ParameterNames.REFRESH_TOKEN, previousRefreshToken));
    }

    @Then("the token exchange is rejected with {string}")
    public void tokenExchangeRejectedWith(String expectedError) {
        assertThat(lastResponse.statusCode()).isEqualTo(BAD_REQUEST);
        assertThat(readBody(lastResponse).path(OAuth2ParameterNames.ERROR).asString()).isEqualTo(expectedError);
    }


    @When("the TPP lists the open banking accounts")
    public void tppListsAccounts() {
        lastResponse = openBankingClient.listAccounts(openBankingAccessToken);
    }

    @When("the client-credentials token is used on the open banking accounts endpoint")
    public void ccTokenOnAccountsEndpoint() {
        lastResponse = openBankingClient.listAccounts(ccToken);
    }

    @When("the login token is used as a bearer token on the open banking accounts endpoint")
    public void loginTokenOnAccountsEndpoint() {
        lastResponse = openBankingClient.listAccounts(accessToken);
    }

    @Then("the account list contains the user's savings and loan accounts")
    public void accountListContainsUserAccounts() {
        assertThat(lastResponse.statusCode()).isEqualTo(OK);
        List<String> accountIds = new ArrayList<>();
        readBody(lastResponse).forEach(account -> accountIds.add(account.path("accountId").asString()));
        assertThat(accountIds).contains(
                OpenBankingAccountId.compose(OpenBankingAccountId.Type.SAVINGS, user.savingsAccountId()),
                OpenBankingAccountId.compose(OpenBankingAccountId.Type.LOAN, user.loanAccountId()));
    }

    @When("the TPP reads the balances of the user's savings account")
    public void tppReadsSavingsBalances() {
        lastResponse = openBankingClient.getBalances(openBankingAccessToken, savingsAccountId());
    }

    @Then("a balance entry is returned for the savings account")
    public void balanceEntryReturned() {
        assertThat(lastResponse.statusCode()).isEqualTo(OK);
        JsonNode balances = readBody(lastResponse);
        assertThat(balances.size()).isGreaterThanOrEqualTo(1);
        balances.forEach(balance ->
                assertThat(balance.path("accountId").asString()).isEqualTo(savingsAccountId()));
    }

    @Then("the open banking read is forbidden by the consent check")
    public void openBankingReadForbiddenByConsentCheck() {
        assertThat(lastResponse.statusCode()).isEqualTo(FORBIDDEN);
        assertThat(readBody(lastResponse).path("code").asString()).isEqualTo(OpenBankingConsentInvalidException.CODE);
    }

    @Then("the open banking request is rejected as unauthenticated")
    public void openBankingRequestRejectedAsUnauthenticated() {
        assertThat(lastResponse.statusCode()).isEqualTo(UNAUTHORIZED);
    }


    @When("the open banking access token is used on the savings endpoint")
    public void openBankingTokenOnSavingsEndpoint() {
        SavingsQueryControllerApi savingsApi = ConsumerApiClientFactory
                .authenticated(SavingsQueryControllerApi.class, openBankingAccessToken, DEVICE_FINGERPRINT);
        try {
            savingsApi.listSavingsAccounts();
            fail("expected the savings request to be rejected");
        } catch (FeignException e) {
            lastFeignError = e;
        }
    }

    @Then("the consumer request is rejected as unauthenticated")
    public void consumerRequestRejectedAsUnauthenticated() {
        assertThat(lastFeignError.status()).isEqualTo(UNAUTHORIZED);
    }


    @Then("the TPP sees the consent status as {string}")
    public void tppSeesConsentStatusAs(String expectedStatus) {
        HttpResponse<String> response = openBankingClient.getConsent(ccToken, consentId);
        assertThat(response.statusCode()).isEqualTo(OK);
        assertThat(readBody(response).path(STATUS_FIELD).asString())
                .isEqualTo(ConsentStatus.valueOf(expectedStatus).name());
    }

    @When("the user lists their connected app consents")
    public void userListsConsents() {
        lastResponse = openBankingClient.listUserConsents(accessToken, DEVICE_FINGERPRINT);
    }

    @Then("the consent list shows the consent as {string}")
    public void consentListShowsConsentAs(String expectedStatus) {
        assertThat(lastResponse.statusCode()).isEqualTo(OK);
        List<JsonNode> matches = new ArrayList<>();
        readBody(lastResponse).forEach(consent -> {
            if (consentId.equals(consent.path("consentId").asString())) {
                matches.add(consent);
            }
        });
        assertThat(matches).as("consent %s in the user's consent list", consentId).hasSize(1);
        assertThat(matches.get(0).path(STATUS_FIELD).asString())
                .isEqualTo(ConsentStatus.valueOf(expectedStatus).name());
    }

    @When("the user revokes the consent")
    public void userRevokesConsent() {
        lastResponse = openBankingClient.revokeUserConsent(accessToken, DEVICE_FINGERPRINT, consentId);
        assertThat(lastResponse.statusCode()).isEqualTo(OK);
    }

    @When("the user revokes the consent again")
    public void userRevokesConsentAgain() {
        lastResponse = openBankingClient.revokeUserConsent(accessToken, DEVICE_FINGERPRINT, consentId);
    }

    @Then("the revoke is rejected as not revocable")
    public void revokeRejectedAsNotRevocable() {
        assertThat(lastResponse.statusCode()).isEqualTo(CONFLICT);
        assertThat(readBody(lastResponse).path("code").asString())
                .isEqualTo(OpenBankingConsentNotRevocableException.CODE);
    }


    @Then("a server-side {string} audit event is recorded for the consent")
    public void serverConsentAuditEventRecorded(String eventType) {
        AuditEventType type = AuditEventType.valueOf(eventType);
        long rows = AuditEventPoller.pollForRows(COUNT_CONSENT_SERVER_EVENT_SQL,
                type.name(), AuditEventSource.SERVER.name(), consentId);
        assertThat(rows)
                .as("SERVER %s audit row for consent %s", type, consentId)
                .isGreaterThanOrEqualTo(1);
    }


    private void grantConsent(String permissions) {
        loggedInOpenBankingCustomer();
        tppHoldsClientCredentialsToken();
        tppCreatesConsent(permissions);
        consentIsCreatedAs(ConsentStatus.AWAITING_AUTHORISATION.name());
        openAuthorizeLinkWithSession();
        redirectedToConsentPage();
        approveConsentOnConsentPage();
        callbackReceivesAuthorizationCode();
    }


    private Map<String, String> authorizeParams(String requestedConsentId) {
        tppState = randomUrlSafeToken();
        codeVerifier = randomUrlSafeToken();
        Map<String, String> params = new LinkedHashMap<>();
        params.put(OAuth2ParameterNames.RESPONSE_TYPE, OAuth2AuthorizationResponseType.CODE.getValue());
        params.put(OAuth2ParameterNames.CLIENT_ID, OpenBankingClient.TPP_CLIENT_ID);
        params.put(OAuth2ParameterNames.REDIRECT_URI, OpenBankingClient.REDIRECT_URI);
        params.put(OAuth2ParameterNames.SCOPE, AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ);
        params.put(OAuth2ParameterNames.STATE, tppState);
        params.put(PkceParameterNames.CODE_CHALLENGE, codeChallenge(codeVerifier));
        params.put(PkceParameterNames.CODE_CHALLENGE_METHOD, PKCE_CHALLENGE_METHOD_S256);
        params.put(OAuth2Constants.CONSENT_ID, requestedConsentId);
        return params;
    }

    private List<Map.Entry<String, String>> decisionFields(boolean approve) {
        List<Map.Entry<String, String>> fields = new ArrayList<>();
        fields.add(OpenBankingClient.entry(OAuth2ParameterNames.CLIENT_ID,
                consentPageParams.get(OAuth2ParameterNames.CLIENT_ID)));
        fields.add(OpenBankingClient.entry(OAuth2ParameterNames.STATE,
                consentPageParams.get(OAuth2ParameterNames.STATE)));
        fields.add(OpenBankingClient.entry(OpenBankingClient.CSRF_FORM_FIELD, xsrfToken));
        if (approve) {
            for (String scope : consentPageParams.get(OAuth2ParameterNames.SCOPE).split(SCOPE_SEPARATOR)) {
                fields.add(OpenBankingClient.entry(OAuth2ParameterNames.SCOPE, scope));
            }
        }
        return fields;
    }

    private HttpResponse<String> exchangeCode(String verifier) {
        return openBankingClient.requestToken(Map.of(
                OAuth2ParameterNames.GRANT_TYPE, AuthorizationGrantType.AUTHORIZATION_CODE.getValue(),
                OAuth2ParameterNames.CODE, authorizationCode,
                OAuth2ParameterNames.REDIRECT_URI, OpenBankingClient.REDIRECT_URI,
                PkceParameterNames.CODE_VERIFIER, verifier));
    }

    private Map<String, String> callbackParams() {
        assertThat(lastResponse.statusCode()).isEqualTo(FOUND);
        String location = locationHeader(lastResponse);
        assertThat(location).startsWith(OpenBankingClient.REDIRECT_URI + "?");
        return queryParams(location);
    }

    private String savingsAccountId() {
        return OpenBankingAccountId.compose(OpenBankingAccountId.Type.SAVINGS, user.savingsAccountId());
    }

    private static String locationHeader(HttpResponse<String> response) {
        return response.headers().firstValue(HttpHeaders.LOCATION)
                .orElseThrow(() -> new AssertionError("expected a Location header on the redirect"));
    }

    private static Map<String, String> queryParams(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        String query = URI.create(url).getRawQuery();
        if (query == null) {
            return params;
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String name = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            params.put(URLDecoder.decode(name, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return params;
    }

    private static String xsrfCookie(HttpResponse<String> response) {
        String prefix = OpenBankingClient.XSRF_COOKIE_NAME + "=";
        return response.headers().allValues(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith(prefix))
                .map(value -> {
                    int semicolon = value.indexOf(';');
                    int end = semicolon >= 0 ? semicolon : value.length();
                    return URLDecoder.decode(value.substring(prefix.length(), end), StandardCharsets.UTF_8);
                })
                .findFirst()
                .orElse(null);
    }

    private static String permissionsBody(List<String> permissionCodes) {
        return JSON.writeValueAsString(Map.of("permissions", permissionCodes));
    }

    private static JsonNode readBody(HttpResponse<String> response) {
        String body = response.body();
        if (body == null || body.isBlank()) {
            return JSON.missingNode();
        }
        return JSON.readTree(body);
    }

    private static String randomUrlSafeToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String codeChallenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

}
