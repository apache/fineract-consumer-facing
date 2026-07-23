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

package org.apache.fineract.consumer.summary.query.service;

import feign.FeignException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsLoanAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.query.Query;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.summary.query.data.AccountsSummaryQueryData;
import org.apache.fineract.consumer.summary.query.data.LoanSummaryItemQueryData;
import org.apache.fineract.consumer.summary.query.data.SavingsSummaryItemQueryData;
import org.apache.fineract.consumer.summary.query.exception.SummaryUpstreamUnavailableException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SummaryQueryServiceImpl implements SummaryQueryService {

    private final ClientApi clientApi;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final UserClientResolver userClientResolver;

    @Override
    @Query
    public AccountsSummaryQueryData getAccountsSummary(Jwt jwt) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SUMMARY_VIEW);
        Long clientId = userClientResolver.resolveClientId(jwt);
        GetClientsClientIdAccountsResponse accounts = fetch(clientId);
        return AccountsSummaryQueryData.builder()
                .savings(toSavingsItems(accounts))
                .loans(toLoanItems(accounts))
                .build();
    }

    private GetClientsClientIdAccountsResponse fetch(Long clientId) {
        try {
            return clientApi.retrieveAllClientAccounts(clientId);
        } catch (FeignException e) {
            throw new SummaryUpstreamUnavailableException(e);
        }
    }

    private List<SavingsSummaryItemQueryData> toSavingsItems(GetClientsClientIdAccountsResponse accounts) {
        if (accounts == null || accounts.getSavingsAccounts() == null) {
            return List.of();
        }
        return sortedById(accounts.getSavingsAccounts(), GetClientsSavingsAccounts::getId)
                .map(this::toSavingsItem)
                .toList();
    }

    private List<LoanSummaryItemQueryData> toLoanItems(GetClientsClientIdAccountsResponse accounts) {
        if (accounts == null || accounts.getLoanAccounts() == null) {
            return List.of();
        }
        return sortedById(accounts.getLoanAccounts(), GetClientsLoanAccounts::getId)
                .map(this::toLoanItem)
                .toList();
    }

    private SavingsSummaryItemQueryData toSavingsItem(GetClientsSavingsAccounts account) {
        return SavingsSummaryItemQueryData.builder()
                .id(account.getId())
                .accountNo(account.getAccountNo())
                .productName(account.getProductName())
                .status(account.getStatus() == null ? null : account.getStatus().getValue())
                .currency(account.getCurrency() == null ? null : account.getCurrency().getCode())
                .accountBalance(account.getAccountBalance())
                .availableBalance(account.getAvailableBalance())
                .build();
    }

    private LoanSummaryItemQueryData toLoanItem(GetClientsLoanAccounts account) {
        return LoanSummaryItemQueryData.builder()
                .id(account.getId())
                .accountNo(account.getAccountNo())
                .productName(account.getProductName())
                .status(account.getStatus() == null ? null : account.getStatus().getCode())
                .currency(account.getCurrency() == null ? null : account.getCurrency().getCode())
                .loanBalance(account.getLoanBalance())
                .amountPaid(account.getAmountPaid())
                .build();
    }

    private static <T> Stream<T> sortedById(Collection<T> accounts, Function<T, Long> idExtractor) {
        return accounts.stream()
                .sorted(Comparator.comparing(idExtractor, Comparator.nullsLast(Comparator.naturalOrder())));
    }
}
