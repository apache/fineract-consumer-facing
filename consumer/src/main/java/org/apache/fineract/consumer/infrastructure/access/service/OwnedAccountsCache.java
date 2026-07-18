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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.consumer.infrastructure.access.data.OwnedAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OwnedAccountsCache {

    private static final String KEY_PREFIX = "abac:ownership:";
    private static final Duration L1_TTL = Duration.ofSeconds(60);
    private static final Duration L2_TTL = Duration.ofSeconds(300);

    private final Cache<Long, OwnedAccounts> l1Cache = Caffeine.newBuilder()
            .expireAfterWrite(L1_TTL)
            .build();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ClientApi clientApi;

    public OwnedAccounts ownedAccounts(Long clientId) {
        OwnedAccounts l1Hit = l1Cache.getIfPresent(clientId);
        if (l1Hit != null) {
            return l1Hit;
        }
        OwnedAccounts l2Hit = readFromValkey(clientId);
        if (l2Hit != null) {
            l1Cache.put(clientId, l2Hit);
            return l2Hit;
        }
        OwnedAccounts fresh = fetchFromFineract(clientId);
        writeToValkey(clientId, fresh);
        l1Cache.put(clientId, fresh);
        return fresh;
    }

    public void evict(Long clientId) {
        l1Cache.invalidate(clientId);
        try {
            redisTemplate.delete(key(clientId));
        } catch (DataAccessException e) {
            log.warn("ownership cache eviction failed for clientId {}; stale entry expires with key TTL", clientId, e);
        }
    }

    private OwnedAccounts readFromValkey(Long clientId) {
        try {
            String json = redisTemplate.opsForValue().get(key(clientId));
            return json == null ? null : objectMapper.readValue(json, OwnedAccounts.class);
        } catch (DataAccessException | JacksonException e) {
            log.warn("ownership cache read failed for clientId {}; falling through to Fineract", clientId, e);
            return null;
        }
    }

    private void writeToValkey(Long clientId, OwnedAccounts ownedAccounts) {
        try {
            redisTemplate.opsForValue().set(key(clientId), objectMapper.writeValueAsString(ownedAccounts), L2_TTL);
        } catch (DataAccessException | JacksonException e) {
            log.warn("ownership cache write failed for clientId {}; continuing without shared cache entry", clientId, e);
        }
    }

    private OwnedAccounts fetchFromFineract(Long clientId) {
        GetClientsClientIdAccountsResponse accounts = clientApi.retrieveAllClientAccounts(clientId);
        return OwnedAccounts.builder()
                .savingsIds(extractSavingsIds(accounts))
                .loanIds(extractLoanIds(accounts))
                .build();
    }

    private static Set<Long> extractSavingsIds(GetClientsClientIdAccountsResponse accounts) {
        if (accounts == null || accounts.getSavingsAccounts() == null) {
            return Set.of();
        }
        return accounts.getSavingsAccounts().stream()
                .map(GetClientsSavingsAccounts::getId)
                .collect(Collectors.toSet());
    }

    private static Set<Long> extractLoanIds(GetClientsClientIdAccountsResponse accounts) {
        if (accounts == null || accounts.getLoanAccounts() == null) {
            return Set.of();
        }
        return accounts.getLoanAccounts().stream()
                .map(GetClientsLoanAccounts::getId)
                .collect(Collectors.toSet());
    }

    private static String key(Long clientId) {
        return KEY_PREFIX + clientId;
    }
}
