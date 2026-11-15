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

package org.apache.fineract.consumer.infrastructure.fineractclient.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.LocalDate;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.AccountTransfersApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetAccountTransfersTemplateResponse;
import org.springframework.stereotype.Service;

/**
 * Reads the date from the account transfer template. Fineract fills its {@code transferDate} with the same date its
 * jobs use, whether or not business dates are enabled. {@code GET /businessdate/BUSINESS_DATE} only returns the
 * stored row: it is 404 on a tenant that never set one, and stale once business dates are disabled again.
 */
@Service
public class BusinessDateFineractClientImpl implements BusinessDateFineractClient {

    // Short, because the date moves at midnight and whenever the business date is changed.
    static final Duration TTL = Duration.ofSeconds(30);
    private static final String KEY = "today";

    private final Cache<String, LocalDate> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .build();

    private final AccountTransfersApi accountTransfersApi;

    public BusinessDateFineractClientImpl(AccountTransfersApi accountTransfersApi) {
        this.accountTransfersApi = accountTransfersApi;
    }

    @Override
    public LocalDate today() {
        return cache.get(KEY, key -> fetch());
    }

    private LocalDate fetch() {
        GetAccountTransfersTemplateResponse template =
                accountTransfersApi.retrieveTemplateAccountTransfer(null, null, null, null, null, null, null, null);
        return template == null ? null : template.getTransferDate();
    }
}
