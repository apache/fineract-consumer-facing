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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.DefaultApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdChargesResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetObligeeData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserFineractClientImplTest {

    private static final Long CLIENT_ID = 11L;

    @Mock
    private ClientApi clientApi;

    @Mock
    private ClientChargesApi clientChargesApi;

    @Mock
    private DefaultApi defaultApi;

    @InjectMocks
    private UserFineractClientImpl client;

    @Test
    void retrieveOneClientDelegatesToGeneratedApi() {
        GetClientsClientIdResponse expected = new GetClientsClientIdResponse();
        when(clientApi.retrieveOneClient(CLIENT_ID, null)).thenReturn(expected);

        GetClientsClientIdResponse result = client.retrieveOneClient(CLIENT_ID, null);

        assertThat(result).isSameAs(expected);
        verify(clientApi).retrieveOneClient(CLIENT_ID, null);
    }

    @Test
    void retrieveAllClientChargesDelegatesToGeneratedApi() {
        GetClientsClientIdChargesResponse expected = new GetClientsClientIdChargesResponse();
        when(clientChargesApi.retrieveAllClientCharges(CLIENT_ID, "active", null, 5, 10)).thenReturn(expected);

        GetClientsClientIdChargesResponse result =
                client.retrieveAllClientCharges(CLIENT_ID, "active", null, 5, 10);

        assertThat(result).isSameAs(expected);
        verify(clientChargesApi).retrieveAllClientCharges(CLIENT_ID, "active", null, 5, 10);
    }

    @Test
    void retrieveClientObligeeDetailsDelegatesToGeneratedApi() {
        List<GetObligeeData> expected = List.of(new GetObligeeData());
        when(clientApi.retrieveClientObligeeDetails(CLIENT_ID)).thenReturn(expected);

        List<GetObligeeData> result = client.retrieveClientObligeeDetails(CLIENT_ID);

        assertThat(result).isSameAs(expected);
        verify(clientApi).retrieveClientObligeeDetails(CLIENT_ID);
    }

    @Test
    void retrieveImageDelegatesToGeneratedApi() {
        String expected = "data:image/png;base64,AAAA";
        when(defaultApi.retrieveImage("clients", CLIENT_ID, 256, 256, null, "text/plain"))
                .thenReturn(expected);

        String result = client.retrieveImage("clients", CLIENT_ID, 256, 256, null, "text/plain");

        assertThat(result).isEqualTo(expected);
        verify(defaultApi).retrieveImage("clients", CLIENT_ID, 256, 256, null, "text/plain");
    }
}