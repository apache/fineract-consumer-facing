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

import feign.Feign;
import feign.RequestInterceptor;
import feign.auth.BasicAuthRequestInterceptor;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.okhttp.OkHttpClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.fineract.consumer.cucumber.clients.FineractClientSeedClient;
import org.apache.fineract.consumer.cucumber.clients.FineractCodeLookupClient;
import org.apache.fineract.consumer.cucumber.clients.FineractIdentifierSeedClient;
import org.apache.fineract.consumer.infrastructure.fineractclient.FineractHeaders;

public class FineractSeeder {

    private static final String BASE_URL = System.getenv().getOrDefault(
            "FINERACT_BASE_URL", "http://localhost:8888/fineract-provider/api/v1");
    private static final String USERNAME = System.getenv().getOrDefault("FINERACT_USERNAME", "mifos");
    private static final String PASSWORD = System.getenv().getOrDefault("FINERACT_PASSWORD", "password");
    private static final String TENANT = System.getenv().getOrDefault("FINERACT_TENANT", "default");
    private static final long HEAD_OFFICE_ID = 1L;
    private static final String CUSTOMER_IDENTIFIER_CODE = "Customer Identifier";
    private static final String PASSPORT_DOCUMENT_TYPE = "Passport";

    private static final FineractClientSeedClient CLIENTS = buildFeignClient(FineractClientSeedClient.class);
    private static final FineractIdentifierSeedClient IDENTIFIERS = buildFeignClient(FineractIdentifierSeedClient.class);
    private static final FineractCodeLookupClient CODES = buildFeignClient(FineractCodeLookupClient.class);

    private static volatile Long cachedPassportCodeValueId;

    public record SeededClient(long fineractClientId, String documentTypeName, String documentKey) {}

    public SeededClient seedClientWithPassport() {
        long clientId = createClient();
        String documentKey = "PASS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        attachPassportIdentifier(clientId, documentKey);
        return new SeededClient(clientId, PASSPORT_DOCUMENT_TYPE, documentKey);
    }

    private long createClient() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> body = Map.of(
                "officeId", HEAD_OFFICE_ID,
                "firstname", "Test",
                "lastname", "User-" + suffix,
                "active", false,
                "legalFormId", 1,
                "locale", "en",
                "dateFormat", "dd MMMM yyyy");
        Map<String, Object> response = CLIENTS.createClient(body);
        return ((Number) response.get("clientId")).longValue();
    }

    private void attachPassportIdentifier(long clientId, String documentKey) {
        long passportId = resolvePassportCodeValueId();
        Map<String, Object> body = Map.of(
                "documentTypeId", passportId,
                "documentKey", documentKey,
                "status", "ACTIVE");
        IDENTIFIERS.createIdentifier(clientId, body);
    }

    private long resolvePassportCodeValueId() {
        Long cached = cachedPassportCodeValueId;
        if (cached != null) {
            return cached;
        }
        synchronized (FineractSeeder.class) {
            if (cachedPassportCodeValueId != null) {
                return cachedPassportCodeValueId;
            }
            long codeId = findIdByName(CODES.listCodes(), CUSTOMER_IDENTIFIER_CODE);
            long passportId = findIdByName(CODES.listCodeValues(codeId), PASSPORT_DOCUMENT_TYPE);
            cachedPassportCodeValueId = passportId;
            return passportId;
        }
    }

    private long findIdByName(List<Map<String, Object>> entries, String name) {
        return entries.stream()
                .filter(entry -> name.equals(entry.get("name")))
                .map(entry -> ((Number) entry.get("id")).longValue())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fineract entry not found: " + name));
    }

    private static <T> T buildFeignClient(Class<T> apiType) {
        return Feign.builder()
                .client(new OkHttpClient())
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .requestInterceptor(new BasicAuthRequestInterceptor(USERNAME, PASSWORD))
                .requestInterceptor(tenantInterceptor())
                .target(apiType, BASE_URL);
    }

    private static RequestInterceptor tenantInterceptor() {
        return template -> template.header(FineractHeaders.TENANT_ID, TENANT);
    }
}
