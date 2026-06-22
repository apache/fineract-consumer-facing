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

package org.apache.fineract.consumer.savings.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.util.List;
import java.util.Set;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccountsCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccountsStatus;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountListItemQueryData;
import org.apache.fineract.consumer.savings.query.exception.SavingsUpstreamUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsQueryServiceImplTest {

    private static final Long CLIENT_ID = 11L;

    @Mock
    private ClientApi clientApi;

    @InjectMocks
    private SavingsQueryServiceImpl service;

    @Test
    void listAccountsMapsIndexFields() {
        GetClientsSavingsAccounts account = new GetClientsSavingsAccounts()
                .id(42L)
                .accountNo("000000042")
                .productName("Regular Savings")
                .status(new GetClientsSavingsAccountsStatus().value("Active"))
                .currency(new GetClientsSavingsAccountsCurrency().code("USD"));
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse().savingsAccounts(Set.of(account)));

        List<SavingsAccountListItemQueryData> result = service.listAccounts(CLIENT_ID);

        assertThat(result).hasSize(1);
        SavingsAccountListItemQueryData item = result.get(0);
        assertThat(item.getId()).isEqualTo(42L);
        assertThat(item.getAccountNo()).isEqualTo("000000042");
        assertThat(item.getProductName()).isEqualTo("Regular Savings");
        assertThat(item.getStatus()).isEqualTo("Active");
        assertThat(item.getCurrency()).isEqualTo("USD");
    }

    @Test
    void listAccountsEmptyWhenNoSavingsAccounts() {
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID))
                .thenReturn(new GetClientsClientIdAccountsResponse());

        assertThat(service.listAccounts(CLIENT_ID)).isEmpty();
    }

    @Test
    void listAccountsTranslatesUpstreamFailure() {
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> service.listAccounts(CLIENT_ID))
                .isInstanceOf(SavingsUpstreamUnavailableException.class);
    }
}
