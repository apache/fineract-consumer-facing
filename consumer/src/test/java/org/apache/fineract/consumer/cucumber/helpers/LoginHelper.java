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

package org.apache.fineract.consumer.cucumber.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Response;
import feign.Util;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.cucumber.clients.AuthenticationClient;
import org.apache.fineract.consumer.cucumber.clients.MailpitClient;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

public class LoginHelper {

    private static final String ACCESS_COOKIE_PREFIX = AuthenticationConstants.ACCESS_TOKEN_COOKIE_NAME + "=";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final MailpitClient mailpit = new MailpitClient();
    private final AuthenticationClient authClient = new AuthenticationClient();

    public String login(String email, String password, String deviceFingerprint) {
        mailpit.deleteMessages(email);
        Response loginResponse = authClient.login(email, password, deviceFingerprint);
        String challengeToken = readBody(loginResponse).path("challengeToken").asString();
        String otp = mailpit.waitForOtp(email);
        try (Response twoFactorResponse = authClient.verifyTwoFactor(challengeToken, otp, deviceFingerprint)) {
            String accessToken = extractAccessCookie(twoFactorResponse);
            assertThat(accessToken).isNotBlank();
            return accessToken;
        }
    }

    private static String extractAccessCookie(Response response) {
        return response.headers().entrySet().stream()
                .filter(entry -> HttpHeaders.SET_COOKIE.equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .filter(value -> value.startsWith(ACCESS_COOKIE_PREFIX))
                .map(LoginHelper::cookieValue)
                .findFirst()
                .orElse(null);
    }

    private static String cookieValue(String setCookieValue) {
        int semicolon = setCookieValue.indexOf(';');
        int end = semicolon >= 0 ? semicolon : setCookieValue.length();
        return setCookieValue.substring(ACCESS_COOKIE_PREFIX.length(), end);
    }

    private static JsonNode readBody(Response response) {
        try (response) {
            if (response.body() == null) {
                return JSON.missingNode();
            }
            String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
            if (body == null || body.isBlank()) {
                return JSON.missingNode();
            }
            return JSON.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read authentication response body", e);
        }
    }
}
