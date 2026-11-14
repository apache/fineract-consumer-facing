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

import feign.FeignException;
import feign.Retryer;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.client.ApiClient;
import org.apache.fineract.consumer.infrastructure.web.data.ConsumerHeaders;
import org.springframework.http.HttpHeaders;

public final class ConsumerApiClientFactory {

    public static final String BFF_BASE_URL = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    /**
     * The second BFF that {@code compose.second-bff.yaml} adds, with scheduled transfers turned off. It shares the
     * first's database and signing key, so the first's access tokens work on it. Only {@code @second-bff} scenarios
     * use it.
     */
    public static final String SECOND_BFF_BASE_URL =
            System.getenv().getOrDefault("SECOND_BFF_BASE_URL", "http://localhost:8081");

    private ConsumerApiClientFactory() {
    }

    public static <T extends ApiClient.Api> T authenticated(Class<T> apiType, String accessToken, String deviceFingerprint) {
        return authenticated(apiType, accessToken, deviceFingerprint, BFF_BASE_URL);
    }

    public static <T extends ApiClient.Api> T authenticated(Class<T> apiType, String accessToken,
            String deviceFingerprint, String baseUrl) {
        ApiClient apiClient = newApiClient(baseUrl);
        apiClient.getFeignBuilder().requestInterceptor(template -> {
            template.header(HttpHeaders.COOKIE,
                    AuthenticationConstants.ACCESS_TOKEN_COOKIE_NAME + "=" + accessToken);
            if (!template.headers().containsKey(ConsumerHeaders.DEVICE_FINGERPRINT)) {
                template.header(ConsumerHeaders.DEVICE_FINGERPRINT, deviceFingerprint);
            }
        });
        return apiClient.buildClient(apiType);
    }

    public static <T extends ApiClient.Api> T unauthenticated(Class<T> apiType) {
        return newApiClient(BFF_BASE_URL).buildClient(apiType);
    }

    private static ApiClient newApiClient(String baseUrl) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(baseUrl);
        apiClient.getFeignBuilder()
                .errorDecoder((methodKey, response) -> FeignException.errorStatus(methodKey, response))
                .retryer(Retryer.NEVER_RETRY);
        return apiClient;
    }
}
