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

import feign.Feign;
import feign.FeignException;
import feign.Headers;
import feign.Param;
import feign.Request;
import feign.RequestLine;
import feign.Response;
import feign.jackson.JacksonEncoder;
import feign.okhttp.OkHttpClient;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.web.ConsumerHeaders;

public final class AuthenticationClient {

    private static final String BFF_BASE_URL = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    private static final String REFRESH_COOKIE_PREFIX = AuthenticationConstants.REFRESH_TOKEN_COOKIE_NAME + "=";
    private static final long CONNECT_TIMEOUT_SECONDS = 5;
    private static final long READ_TIMEOUT_SECONDS = 10;

    private static final Api API = Feign.builder()
            .client(new OkHttpClient())
            .encoder(new JacksonEncoder())
            .options(new Request.Options(
                    CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS,
                    READ_TIMEOUT_SECONDS, TimeUnit.SECONDS,
                    true))
            .target(Api.class, BFF_BASE_URL);

    public Response login(String email, String password, String deviceFingerprint) {
        return throwIfError(API.login(deviceFingerprint, Map.of("email", email, "password", password)));
    }

    public Response verifyTwoFactor(String challengeToken, String otpToken, String deviceFingerprint) {
        return throwIfError(API.verifyTwoFactor(deviceFingerprint,
                Map.of("challengeToken", challengeToken, "token", otpToken)));
    }

    public Response refresh(String refreshCookieValue, String deviceFingerprint) {
        String cookie = refreshCookieValue == null ? null : REFRESH_COOKIE_PREFIX + refreshCookieValue;
        return throwIfError(API.refresh(cookie, deviceFingerprint));
    }

    public Response logout(String bearerToken, String refreshCookieValue) {
        String authorization = bearerToken == null ?
                null : AuthenticationConstants.BEARER_TOKEN_TYPE + " " + bearerToken;
        String cookie = refreshCookieValue == null ? null : REFRESH_COOKIE_PREFIX + refreshCookieValue;
        return throwIfError(API.logout(authorization, cookie));
    }

    private static Response throwIfError(Response response) {
        if (response.status() >= 400) {
            throw FeignException.errorStatus("authentication", response);
        }
        return response;
    }

    private interface Api {

        @RequestLine("POST /api/v1/authentication/login")
        @Headers({ "Content-Type: application/json", ConsumerHeaders.DEVICE_FINGERPRINT + ": {fingerprint}" })
        Response login(@Param("fingerprint") String fingerprint, Map<String, Object> body);

        @RequestLine("POST /api/v1/authentication/2fa")
        @Headers({ "Content-Type: application/json", ConsumerHeaders.DEVICE_FINGERPRINT + ": {fingerprint}" })
        Response verifyTwoFactor(@Param("fingerprint") String fingerprint, Map<String, Object> body);

        @RequestLine("POST /api/v1/authentication/refresh")
        @Headers({ "Cookie: {refreshCookie}", ConsumerHeaders.DEVICE_FINGERPRINT + ": {fingerprint}" })
        Response refresh(@Param("refreshCookie") String refreshCookie, @Param("fingerprint") String fingerprint);

        @RequestLine("POST /api/v1/authentication/logout")
        @Headers({ "Authorization: {authorization}", "Cookie: {refreshCookie}" })
        Response logout(@Param("authorization") String authorization, @Param("refreshCookie") String refreshCookie);
    }
}
