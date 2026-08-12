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

package org.apache.fineract.consumer.infrastructure.fineractclient.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FineractCallerTest {

    private static final RuntimeException NOT_FOUND = new RuntimeException("not found");
    private static final RuntimeException BAD_REQUEST = new RuntimeException("bad request");
    private static final RuntimeException IN_PROGRESS = new RuntimeException("in progress");
    private static final RuntimeException UPSTREAM = new RuntimeException("upstream");

    private static FeignException feignException(int status) {
        Request request = Request.create(Request.HttpMethod.POST, "/test", Map.of(), null,
                StandardCharsets.UTF_8, null);
        Response response = Response.builder().status(status).request(request).headers(Map.of()).build();
        return FeignException.errorStatus("test", response);
    }

    private static <T> T callWithInProgress(FeignException upstreamFailure) {
        return FineractCaller.call(() -> {
            throw upstreamFailure;
        }, e -> NOT_FOUND, e -> BAD_REQUEST, e -> IN_PROGRESS, e -> UPSTREAM);
    }

    @Test
    void successReturnsUpstreamValue() {
        String result = FineractCaller.call(() -> "ok", e -> NOT_FOUND, e -> BAD_REQUEST, e -> IN_PROGRESS,
                e -> UPSTREAM);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void tooEarlyRoutesToOnRequestInProgress() {
        assertThatThrownBy(() -> callWithInProgress(feignException(425))).isSameAs(IN_PROGRESS);
    }

    @Test
    void notFoundRoutesToOnNotFound() {
        assertThatThrownBy(() -> callWithInProgress(feignException(404))).isSameAs(NOT_FOUND);
    }

    @Test
    void badRequestRoutesToOnBadRequest() {
        assertThatThrownBy(() -> callWithInProgress(feignException(400))).isSameAs(BAD_REQUEST);
    }

    @Test
    void otherStatusRoutesToOnUpstreamError() {
        assertThatThrownBy(() -> callWithInProgress(feignException(503))).isSameAs(UPSTREAM);
    }

    @Test
    void legacyOverloadRoutesTooEarlyToOnUpstreamError() {
        assertThatThrownBy(() -> FineractCaller.call(() -> {
            throw feignException(425);
        }, e -> NOT_FOUND, e -> BAD_REQUEST, e -> UPSTREAM)).isSameAs(UPSTREAM);
    }
}
