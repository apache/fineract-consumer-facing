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

package org.apache.fineract.consumer.infrastructure.kyc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import feign.FeignException;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientStandingCheckerTest {

    private static final Long CLIENT_ID = 42L;
    private static final long ACTIVE_STATUS_ID = 300L;
    private static final long CLOSED_STATUS_ID = 600L;

    @Mock
    private ClientApi clientApi;

    @InjectMocks
    private ClientStandingChecker checker;

    @Test
    void activeClientIsInStanding() {
        when(clientApi.retrieveOneClient(CLIENT_ID, null)).thenReturn(clientWithStatus(ACTIVE_STATUS_ID));

        assertThat(checker.isActive(CLIENT_ID)).isTrue();
    }

    @Test
    void nonActiveClientIsNotInStanding() {
        when(clientApi.retrieveOneClient(CLIENT_ID, null)).thenReturn(clientWithStatus(CLOSED_STATUS_ID));

        assertThat(checker.isActive(CLIENT_ID)).isFalse();
    }

    @Test
    void missingStatusIsNotInStanding() {
        when(clientApi.retrieveOneClient(CLIENT_ID, null)).thenReturn(new GetClientsClientIdResponse());

        assertThat(checker.isActive(CLIENT_ID)).isFalse();
    }

    @Test
    void unknownClientIsNotInStanding() {
        when(clientApi.retrieveOneClient(CLIENT_ID, null)).thenThrow(mock(FeignException.NotFound.class));

        assertThat(checker.isActive(CLIENT_ID)).isFalse();
    }

    @Test
    void transportFailurePropagatesSoTheMintFailsClosed() {
        when(clientApi.retrieveOneClient(CLIENT_ID, null))
                .thenThrow(mock(FeignException.ServiceUnavailable.class));

        assertThatThrownBy(() -> checker.isActive(CLIENT_ID)).isInstanceOf(FeignException.class);
    }

    private static GetClientsClientIdResponse clientWithStatus(long statusId) {
        return new GetClientsClientIdResponse().status(new GetClientsClientIdStatus().id(statusId));
    }
}
