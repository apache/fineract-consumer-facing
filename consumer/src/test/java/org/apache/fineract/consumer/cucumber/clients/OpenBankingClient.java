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

package org.apache.fineract.consumer.cucumber.clients;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

public final class OpenBankingClient {

    private static final String BFF_BASE_URL = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");

    public static final String TPP_CLIENT_ID = System.getenv().getOrDefault("OB_TPP_CLIENT_ID", "demo-tpp");
    public static final String TPP_CLIENT_SECRET = System.getenv().getOrDefault("OB_TPP_CLIENT_SECRET", "demo-tpp-secret");
    public static final String REDIRECT_URI =
            System.getenv().getOrDefault("OB_TPP_CLIENT_REDIRECT_URI", "http://localhost:9999/tpp/callback");
    public static final String FRONTEND_BASE_URL =
            System.getenv().getOrDefault("OB_FRONTEND_BASE_URL", "http://localhost:4443");

    public static final String XSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String CSRF_FORM_FIELD = "_csrf";

    private static final String ACCOUNT_ACCESS_CONSENTS_PATH = "/api/v1/openbanking/account-access-consents";
    private static final String ACCOUNTS_PATH = "/api/v1/openbanking/accounts";
    private static final String USER_CONSENTS_PATH = "/api/v1/openbanking/consents";

    private static final String ACCESS_COOKIE_PREFIX = AuthenticationConstants.ACCESS_TOKEN_COOKIE_NAME + "=";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String BASIC_PREFIX = "Basic ";
    private static final String COOKIE_SEPARATOR = "; ";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();

    public HttpResponse<String> requestToken(Map<String, String> form) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BFF_BASE_URL + OAuth2Constants.TOKEN_ENDPOINT))
                .timeout(TIMEOUT)
                .header(HttpHeaders.AUTHORIZATION, BASIC_PREFIX + Base64.getEncoder()
                        .encodeToString((TPP_CLIENT_ID + ":" + TPP_CLIENT_SECRET).getBytes(StandardCharsets.UTF_8)))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(form.entrySet().stream()
                        .map(entry -> entry(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList()))))
                .build();
        return send(request);
    }

    public HttpResponse<String> createConsent(String bearerToken, String permissionsJsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BFF_BASE_URL + ACCOUNT_ACCESS_CONSENTS_PATH))
                .timeout(TIMEOUT)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + bearerToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(permissionsJsonBody))
                .build();
        return send(request);
    }

    public HttpResponse<String> getConsent(String bearerToken, String consentId) {
        return get(ACCOUNT_ACCESS_CONSENTS_PATH + "/" + consentId,
                HttpHeaders.AUTHORIZATION, BEARER_PREFIX + bearerToken);
    }

    public HttpResponse<String> authorize(String accessTokenCookieValue, Map<String, String> queryParams) {
        String query = queryParams.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BFF_BASE_URL + OAuth2Constants.AUTHORIZATION_ENDPOINT + "?" + query))
                .timeout(TIMEOUT)
                .GET();
        if (accessTokenCookieValue != null) {
            builder.header(HttpHeaders.COOKIE, ACCESS_COOKIE_PREFIX + accessTokenCookieValue);
        }
        return send(builder.build());
    }

    public HttpResponse<String> submitConsentDecision(String accessTokenCookieValue, String xsrfToken,
            List<Map.Entry<String, String>> formFields) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BFF_BASE_URL + OAuth2Constants.AUTHORIZATION_ENDPOINT))
                .timeout(TIMEOUT)
                .header(HttpHeaders.COOKIE, ACCESS_COOKIE_PREFIX + accessTokenCookieValue
                        + COOKIE_SEPARATOR + XSRF_COOKIE_NAME + "=" + xsrfToken)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(formFields)))
                .build();
        return send(request);
    }

    public HttpResponse<String> listAccounts(String bearerToken) {
        return get(ACCOUNTS_PATH, HttpHeaders.AUTHORIZATION, BEARER_PREFIX + bearerToken);
    }

    public HttpResponse<String> getBalances(String bearerToken, String accountId) {
        return get(ACCOUNTS_PATH + "/" + accountId + "/balances",
                HttpHeaders.AUTHORIZATION, BEARER_PREFIX + bearerToken);
    }

    public HttpResponse<String> listUserConsents(String accessTokenCookieValue, String deviceFingerprint) {
        return get(USER_CONSENTS_PATH,
                HttpHeaders.COOKIE, ACCESS_COOKIE_PREFIX + accessTokenCookieValue,
                ConsumerHeaders.DEVICE_FINGERPRINT, deviceFingerprint);
    }

    public HttpResponse<String> revokeUserConsent(String accessTokenCookieValue, String deviceFingerprint,
            String consentId) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BFF_BASE_URL + USER_CONSENTS_PATH + "/" + consentId + "/revoke"))
                .timeout(TIMEOUT)
                .header(HttpHeaders.COOKIE, ACCESS_COOKIE_PREFIX + accessTokenCookieValue)
                .header(ConsumerHeaders.DEVICE_FINGERPRINT, deviceFingerprint)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return send(request);
    }

    public static Map.Entry<String, String> entry(String name, String value) {
        return new AbstractMap.SimpleImmutableEntry<>(name, value);
    }

    private HttpResponse<String> get(String path, String... headerPairs) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BFF_BASE_URL + path))
                .timeout(TIMEOUT)
                .GET();
        for (int i = 0; i < headerPairs.length; i += 2) {
            builder.header(headerPairs[i], headerPairs[i + 1]);
        }
        return send(builder.build());
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("open banking request failed: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during open banking request: " + request.uri(), e);
        }
    }

    private static String formEncode(List<Map.Entry<String, String>> fields) {
        return fields.stream()
                .map(field -> urlEncode(field.getKey()) + "=" + urlEncode(field.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
