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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.infrastructure.fineractclient.configs;

import static org.assertj.core.api.Assertions.assertThat;

import feign.RequestTemplate;
import feign.codec.Encoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FineractClientConfigTest {

    private static final String POPULATED_VALUE = "populated";
    private static final LocalDate SAMPLE_DATE = LocalDate.of(2026, 8, 9);

    private final JsonMapper springManagedMapper = JsonMapper.builder().build();
    private final Encoder encoder = new FineractClientConfig().fineractFeignEncoder(springManagedMapper);

    @Getter
    @RequiredArgsConstructor
    static final class SampleRequest {

        private final String populatedField;
        private final String nullField;
        private final List<String> emptyList;
        private final LocalDate submittedOnDate;
    }

    private JsonNode encodeToJson(SampleRequest request) {
        RequestTemplate template = new RequestTemplate();
        encoder.encode(request, SampleRequest.class, template);
        return springManagedMapper.readTree(new String(template.body(), StandardCharsets.UTF_8));
    }

    private static SampleRequest sampleRequest() {
        return new SampleRequest(POPULATED_VALUE, null, List.of(), SAMPLE_DATE);
    }

    @Test
    void omitsNullFieldsFromEncodedBody() {
        JsonNode body = encodeToJson(sampleRequest());

        assertThat(body.has("nullField")).isFalse();
    }

    @Test
    void omitsEmptyListsFromEncodedBody() {
        JsonNode body = encodeToJson(sampleRequest());

        assertThat(body.has("emptyList")).isFalse();
    }

    @Test
    void keepsPopulatedFieldsInEncodedBody() {
        JsonNode body = encodeToJson(sampleRequest());

        assertThat(body.get("populatedField").asString()).isEqualTo(POPULATED_VALUE);
    }

    @Test
    void serializesLocalDateSameAsSpringManagedMapper() {
        JsonNode encoded = encodeToJson(sampleRequest());
        JsonNode springManaged = springManagedMapper.readTree(springManagedMapper.writeValueAsString(sampleRequest()));

        assertThat(encoded.get("submittedOnDate")).isEqualTo(springManaged.get("submittedOnDate"));
    }
}
