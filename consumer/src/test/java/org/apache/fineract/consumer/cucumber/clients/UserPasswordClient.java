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
import org.springframework.http.MediaType;

public final class UserPasswordClient {

    private static final String BFF_BASE_URL = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    private static final String ACCESS_COOKIE_PREFIX = AuthenticationConstants.ACCESS_TOKEN_COOKIE_NAME + "=";
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

    public Response confirmPasswordChange(String accessToken, String deviceFingerprint,
            String stepUpToken, String otp, String newPassword) {
        return throwIfError(API.confirmPasswordChange(
                ACCESS_COOKIE_PREFIX + accessToken,
                deviceFingerprint,
                Map.of("stepUpToken", stepUpToken, "otp", otp, "newPassword", newPassword)));
    }

    private static Response throwIfError(Response response) {
        if (response.status() >= 400) {
            throw FeignException.errorStatus("userPassword", response);
        }
        return response;
    }

    private interface Api {

        @RequestLine("POST /api/v1/user/password/change/confirm")
        @Headers({ "Content-Type: " + MediaType.APPLICATION_JSON_VALUE, "Cookie: {cookie}",
                ConsumerHeaders.DEVICE_FINGERPRINT + ": {fingerprint}" })
        Response confirmPasswordChange(@Param("cookie") String cookie, @Param("fingerprint") String fingerprint,
                Map<String, Object> body);
    }
}
