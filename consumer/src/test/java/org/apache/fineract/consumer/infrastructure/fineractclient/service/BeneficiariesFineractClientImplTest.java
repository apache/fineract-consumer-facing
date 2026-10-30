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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SearchApiApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSearchResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BeneficiariesFineractClientImplTest {

    private static final Long ACCOUNT_ID = 66L;
    private static final Long CLIENT_ID = 99L;
    private static final String QUERY = "000000123";
    private static final String RESOURCE = "savingsaccounts";

    @Mock
    private SavingsAccountApi savingsAccountApi;

    @Mock
    private SearchApiApi searchApiApi;

    @Mock
    private ClientApi clientApi;

    @InjectMocks
    private BeneficiariesFineractClientImpl client;

    @Test
    void searchDataDelegatesToGeneratedApi() {
        List<GetSearchResponse> expected = List.of(new GetSearchResponse().entityId(ACCOUNT_ID));
        when(searchApiApi.searchData(QUERY, RESOURCE, true)).thenReturn(expected);

        List<GetSearchResponse> result = client.searchData(QUERY, RESOURCE, true);

        assertThat(result).isSameAs(expected);
        verify(searchApiApi).searchData(QUERY, RESOURCE, true);
    }

    @Test
    void retrieveSavingsAccountDelegatesToGeneratedApi() {
        SavingsAccountData expected = new SavingsAccountData();
        when(savingsAccountApi.retrieveSavingsAccount(ACCOUNT_ID, null, null, null)).thenReturn(expected);

        SavingsAccountData result = client.retrieveSavingsAccount(ACCOUNT_ID, null, null, null);

        assertThat(result).isSameAs(expected);
        verify(savingsAccountApi).retrieveSavingsAccount(ACCOUNT_ID, null, null, null);
    }

    @Test
    void retrieveOneClientDelegatesToGeneratedApi() {
        GetClientsClientIdResponse expected = new GetClientsClientIdResponse();
        when(clientApi.retrieveOneClient(CLIENT_ID, false)).thenReturn(expected);

        GetClientsClientIdResponse result = client.retrieveOneClient(CLIENT_ID, false);

        assertThat(result).isSameAs(expected);
        verify(clientApi).retrieveOneClient(CLIENT_ID, false);
    }
}