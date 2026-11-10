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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.AccountTransfersApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetAccountTransfersTemplateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessDateFineractClientImplTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2030, 1, 15);

    @Mock
    private AccountTransfersApi accountTransfersApi;

    private BusinessDateFineractClientImpl client;

    @BeforeEach
    void setUp() {
        client = new BusinessDateFineractClientImpl(accountTransfersApi);
    }

    @Test
    void todayIsTheTransferTemplatesDateRequestedWithNoFilters() {
        when(accountTransfersApi.retrieveTemplateAccountTransfer(null, null, null, null, null, null, null, null))
                .thenReturn(new GetAccountTransfersTemplateResponse().transferDate(BUSINESS_DATE));

        assertThat(client.today()).isEqualTo(BUSINESS_DATE);
    }

    @Test
    void todayIsCachedBetweenCalls() {
        when(accountTransfersApi.retrieveTemplateAccountTransfer(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new GetAccountTransfersTemplateResponse().transferDate(BUSINESS_DATE));

        client.today();
        client.today();

        verify(accountTransfersApi, times(1))
                .retrieveTemplateAccountTransfer(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aMissingDateIsReturnedAsNullAndNotCached() {
        when(accountTransfersApi.retrieveTemplateAccountTransfer(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new GetAccountTransfersTemplateResponse())
                .thenReturn(new GetAccountTransfersTemplateResponse().transferDate(BUSINESS_DATE));

        assertThat(client.today()).isNull();
        assertThat(client.today()).isEqualTo(BUSINESS_DATE);
    }

    @Test
    void anEmptyResponseIsReturnedAsNull() {
        when(accountTransfersApi.retrieveTemplateAccountTransfer(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        assertThat(client.today()).isNull();
    }
}
