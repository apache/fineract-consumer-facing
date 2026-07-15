/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
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

package org.apache.fineract.consumer.infrastructure.fineractclient;

import feign.FeignException;
import java.util.function.Function;
import java.util.function.Supplier;

public final class FineractCaller {

    private FineractCaller() {
    }

    public static <T> T call(Supplier<T> upstream,
            Function<FeignException, RuntimeException> onNotFound,
            Function<FeignException, RuntimeException> onBadRequest,
            Function<FeignException, RuntimeException> onUpstreamError) {
        try {
            return upstream.get();
        } catch (FeignException.NotFound e) {
            throw onNotFound.apply(e);
        } catch (FeignException.BadRequest e) {
            throw onBadRequest.apply(e);
        } catch (FeignException e) {
            throw onUpstreamError.apply(e);
        }
    }
}
