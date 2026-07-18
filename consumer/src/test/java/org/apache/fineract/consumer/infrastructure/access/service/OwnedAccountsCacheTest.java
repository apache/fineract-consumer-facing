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

package org.apache.fineract.consumer.infrastructure.access.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Set;
import org.apache.fineract.consumer.infrastructure.access.data.OwnedAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class OwnedAccountsCacheTest {

    private static final Long CLIENT_ID = 42L;
    private static final String EXPECTED_KEY = "abac:ownership:" + CLIENT_ID;
    private static final Duration L2_TTL = Duration.ofSeconds(300);
    private static final Long SAVINGS_ID = 7L;
    private static final Long LOAN_ID = 77L;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ClientApi clientApi;

    private OwnedAccountsCache cache;

    @BeforeEach
    void setUp() {
        cache = new OwnedAccountsCache(redisTemplate, objectMapper, clientApi);
    }

    @Test
    void l1HitSkipsValkeyAndFineract() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenReturn(fineractAccounts());

        cache.ownedAccounts(CLIENT_ID);
        OwnedAccounts second = cache.ownedAccounts(CLIENT_ID);

        assertThat(second).isEqualTo(ownedAccounts());
        verify(valueOperations, times(1)).get(EXPECTED_KEY);
        verify(clientApi, times(1)).retrieveAllClientAccounts(CLIENT_ID);
    }

    @Test
    void l2HitBackfillsL1AndSkipsFineract() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(objectMapper.writeValueAsString(ownedAccounts()));

        OwnedAccounts first = cache.ownedAccounts(CLIENT_ID);
        OwnedAccounts second = cache.ownedAccounts(CLIENT_ID);

        assertThat(first).isEqualTo(ownedAccounts());
        assertThat(second).isEqualTo(ownedAccounts());
        verify(valueOperations, times(1)).get(EXPECTED_KEY);
        verifyNoInteractions(clientApi);
    }

    @Test
    void fullMissFetchesFromFineractAndWritesBothTiers() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenReturn(fineractAccounts());

        OwnedAccounts result = cache.ownedAccounts(CLIENT_ID);

        assertThat(result).isEqualTo(ownedAccounts());
        verify(valueOperations).set(EXPECTED_KEY, objectMapper.writeValueAsString(ownedAccounts()), L2_TTL);
    }

    @Test
    void valkeyReadErrorFallsThroughToFineract() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenThrow(new RedisConnectionFailureException("store down"));
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenReturn(fineractAccounts());

        OwnedAccounts result = cache.ownedAccounts(CLIENT_ID);

        assertThat(result).isEqualTo(ownedAccounts());
    }

    @Test
    void valkeyWriteErrorStillReturnsResult() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);
        doThrow(new RedisConnectionFailureException("store down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenReturn(fineractAccounts());

        OwnedAccounts result = cache.ownedAccounts(CLIENT_ID);

        assertThat(result).isEqualTo(ownedAccounts());
    }

    @Test
    void jsonParseErrorTreatedAsMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn("not-json");
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenReturn(fineractAccounts());

        OwnedAccounts result = cache.ownedAccounts(CLIENT_ID);

        assertThat(result).isEqualTo(ownedAccounts());
        verify(clientApi).retrieveAllClientAccounts(CLIENT_ID);
    }

    @Test
    void evictClearsBothTiers() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenReturn(fineractAccounts());
        cache.ownedAccounts(CLIENT_ID);

        cache.evict(CLIENT_ID);
        cache.ownedAccounts(CLIENT_ID);

        verify(redisTemplate).delete(EXPECTED_KEY);
        verify(valueOperations, times(2)).get(EXPECTED_KEY);
        verify(clientApi, times(2)).retrieveAllClientAccounts(CLIENT_ID);
    }

    @Test
    void evictSwallowsValkeyError() {
        when(redisTemplate.delete(EXPECTED_KEY)).thenThrow(new RedisConnectionFailureException("store down"));

        assertThatCode(() -> cache.evict(CLIENT_ID)).doesNotThrowAnyException();
    }

    @Test
    void nullFineractCollectionsYieldEmptySets() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(EXPECTED_KEY)).thenReturn(null);
        when(clientApi.retrieveAllClientAccounts(CLIENT_ID)).thenReturn(new GetClientsClientIdAccountsResponse());

        OwnedAccounts result = cache.ownedAccounts(CLIENT_ID);

        assertThat(result.getSavingsIds()).isEmpty();
        assertThat(result.getLoanIds()).isEmpty();
    }

    private static GetClientsClientIdAccountsResponse fineractAccounts() {
        return new GetClientsClientIdAccountsResponse()
                .savingsAccounts(Set.of(new GetClientsSavingsAccounts().id(SAVINGS_ID)))
                .loanAccounts(Set.of(new GetClientsLoanAccounts().id(LOAN_ID)));
    }

    private static OwnedAccounts ownedAccounts() {
        return OwnedAccounts.builder()
                .savingsIds(Set.of(SAVINGS_ID))
                .loanIds(Set.of(LOAN_ID))
                .build();
    }
}
