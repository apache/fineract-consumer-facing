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

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.fineractclient.FineractCaller;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsProductApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsAccountTransactionsPageItem;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsAccountsSavingsAccountIdChargesResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsProductsProductIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSavingsProductsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountSummaryData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionsSearchResponse;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.query.Query;
import org.apache.fineract.consumer.infrastructure.web.UserClientResolver;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountListItemQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsApplicationTemplateQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsChargeQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsProductOptionQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionSearchQuery;
import org.apache.fineract.consumer.savings.query.exception.SavingsAccountNotFoundException;
import org.apache.fineract.consumer.savings.query.exception.SavingsProductNotFoundException;
import org.apache.fineract.consumer.savings.query.exception.SavingsRequestInvalidException;
import org.apache.fineract.consumer.savings.query.exception.SavingsUpstreamUnavailableException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SavingsQueryServiceImpl implements SavingsQueryService {

    private static final String LOCALE = "en";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private final SavingsAccountApi savingsAccountApi;
    private final SavingsChargesApi savingsChargesApi;
    private final SavingsAccountTransactionsApi savingsAccountTransactionsApi;
    private final SavingsProductApi savingsProductApi;
    private final ClientApi clientApi;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final UserClientResolver userClientResolver;
    private final ObjectMapper objectMapper;

    @Override
    @Query
    public List<SavingsAccountListItemQueryData> listAccounts(Jwt jwt) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_LIST);
        Long clientId = userClientResolver.resolveClientId(jwt);
        GetClientsClientIdAccountsResponse accounts = fetch(() -> clientApi.retrieveAllClientAccounts(clientId));
        if (accounts == null || accounts.getSavingsAccounts() == null) {
            return List.of();
        }
        return accounts.getSavingsAccounts().stream().map(this::toListItem).toList();
    }

    @Override
    @Query
    public SavingsAccountQueryData getAccount(Jwt jwt, Long savingsId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_VIEW, savingsId);
        SavingsAccountData account = fetch(() -> savingsAccountApi.retrieveSavingsAccount(savingsId, null, null, null));
        return toAccountData(account);
    }

    @Override
    @Query
    public List<SavingsChargeQueryData> getCharges(Jwt jwt, Long savingsId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_VIEW, savingsId);
        List<GetSavingsAccountsSavingsAccountIdChargesResponse> charges =
                fetch(() -> savingsChargesApi.retrieveAllSavingsAccountCharges(savingsId, null));
        if (charges == null) {
            return List.of();
        }
        return charges.stream().map(this::toChargeData).toList();
    }

    @Override
    @Query
    public List<SavingsTransactionQueryData> searchTransactions(Jwt jwt, SavingsTransactionSearchQuery query) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_VIEW, query.getSavingsId());
        // TODO: Fix Fineract's GET /savingsaccounts/{id}/transactions/{txnId} endpoint to return an object, not a string, then refactor
        SavingsAccountTransactionsSearchResponse response = fetch(() ->
                savingsAccountTransactionsApi.searchTransactions(
                        query.getSavingsId(),
                        isoDate(query.getFromDate()),
                        isoDate(query.getToDate()),
                        null, null, null, null, null, null, null,
                        query.getOffset(),
                        query.getLimit(),
                        null, null,
                        LOCALE, DATE_FORMAT));
        if (response == null || response.getContent() == null) {
            return List.of();
        }
        return response.getContent().stream().map(this::toTransactionData).toList();
    }

    @Override
    @Query
    public SavingsTransactionQueryData getTransaction(Jwt jwt, Long savingsId, Long transactionId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_VIEW, savingsId);
        String json = fetch(() ->
                savingsAccountTransactionsApi.retrieveOneSavingsAccountTransaction(savingsId, transactionId));
        return toTransactionData(deserialize(json));
    }

    @Override
    @Query
    public SavingsApplicationTemplateQueryData getApplicationTemplate(Jwt jwt, Long productId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_APPLICATION_TEMPLATE_VIEW);
        if (productId == null) {
            List<GetSavingsProductsResponse> products = fetch(savingsProductApi::retrieveAllSavingsProducts);
            return toBrowseTemplate(products);
        }
        GetSavingsProductsProductIdResponse product = fetchProduct(productId);
        return toDetailTemplate(product);
    }

    private <T> T fetch(Supplier<T> call) {
        return FineractCaller.call(call,
                e -> new SavingsAccountNotFoundException(),
                SavingsRequestInvalidException::new,
                SavingsUpstreamUnavailableException::new);
    }

    private GetSavingsProductsProductIdResponse fetchProduct(Long productId) {
        return FineractCaller.call(() -> savingsProductApi.retrieveOneSavingsProduct(productId),
                e -> new SavingsProductNotFoundException(),
                SavingsUpstreamUnavailableException::new,
                SavingsUpstreamUnavailableException::new);
    }

    private SavingsAccountTransactionData deserialize(String json) {
        try {
            return objectMapper.readValue(json, SavingsAccountTransactionData.class);
        } catch (JacksonException e) {
            throw new SavingsUpstreamUnavailableException(e);
        }
    }

    private SavingsAccountListItemQueryData toListItem(GetClientsSavingsAccounts account) {
        return SavingsAccountListItemQueryData.builder()
                .id(account.getId())
                .accountNo(account.getAccountNo())
                .productName(account.getProductName())
                .status(account.getStatus() == null ? null : account.getStatus().getValue())
                .currency(account.getCurrency() == null ? null : account.getCurrency().getCode())
                .build();
    }

    private SavingsAccountQueryData toAccountData(SavingsAccountData account) {
        SavingsAccountSummaryData summary = account.getSummary();
        return SavingsAccountQueryData.builder()
                .id(account.getId())
                .accountNo(account.getAccountNo())
                .productName(account.getSavingsProductName())
                .status(account.getStatus() == null ? null : account.getStatus().getValue())
                .balance(summary == null ? null : summary.getAccountBalance())
                .availableBalance(summary == null ? null : summary.getAvailableBalance())
                .nominalAnnualInterestRate(account.getNominalAnnualInterestRate())
                .build();
    }

    private SavingsChargeQueryData toChargeData(GetSavingsAccountsSavingsAccountIdChargesResponse charge) {
        return SavingsChargeQueryData.builder()
                .id(charge.getId())
                .name(charge.getName())
                .amount(charge.getAmount())
                .amountOutstanding(charge.getAmountOutstanding())
                .build();
    }

    private SavingsTransactionQueryData toTransactionData(GetSavingsAccountTransactionsPageItem transaction) {
        return SavingsTransactionQueryData.builder()
                .id(transaction.getId())
                .type(transaction.getTransactionType() == null ? null : transaction.getTransactionType().getValue())
                .amount(transaction.getAmount())
                .runningBalance(transaction.getRunningBalance())
                .date(transaction.getDate())
                .build();
    }

    private SavingsTransactionQueryData toTransactionData(SavingsAccountTransactionData transaction) {
        return SavingsTransactionQueryData.builder()
                .id(transaction.getId())
                .type(transaction.getTransactionType() == null ? null : transaction.getTransactionType().getValue())
                .amount(transaction.getAmount())
                .runningBalance(transaction.getRunningBalance())
                .date(transaction.getDate())
                .build();
    }

    private SavingsApplicationTemplateQueryData toBrowseTemplate(List<GetSavingsProductsResponse> products) {
        List<SavingsProductOptionQueryData> options = products == null
                ? List.of()
                : products.stream()
                        .map(product -> SavingsProductOptionQueryData.builder()
                                .id(toLong(product.getId()))
                                .name(product.getName())
                                .build())
                        .toList();
        return SavingsApplicationTemplateQueryData.builder()
                .productOptions(options)
                .build();
    }

    private SavingsApplicationTemplateQueryData toDetailTemplate(GetSavingsProductsProductIdResponse product) {
        return SavingsApplicationTemplateQueryData.builder()
                .productId(product.getId())
                .productName(product.getName())
                .shortName(product.getShortName())
                .description(product.getDescription())
                .currencyCode(product.getCurrency() == null ? null : product.getCurrency().getCode())
                .nominalAnnualInterestRate(product.getNominalAnnualInterestRate())
                .interestCompoundingPeriodTypeId(product.getInterestCompoundingPeriodType() == null
                        ? null
                        : toLong(product.getInterestCompoundingPeriodType().getId()))
                .interestCompoundingPeriodTypeCode(product.getInterestCompoundingPeriodType() == null
                        ? null
                        : product.getInterestCompoundingPeriodType().getCode())
                .interestPostingPeriodTypeId(product.getInterestPostingPeriodType() == null
                        ? null
                        : toLong(product.getInterestPostingPeriodType().getId()))
                .interestPostingPeriodTypeCode(product.getInterestPostingPeriodType() == null
                        ? null
                        : product.getInterestPostingPeriodType().getCode())
                .interestCalculationTypeId(product.getInterestCalculationType() == null
                        ? null
                        : toLong(product.getInterestCalculationType().getId()))
                .interestCalculationTypeCode(product.getInterestCalculationType() == null
                        ? null
                        : product.getInterestCalculationType().getCode())
                .interestCalculationDaysInYearTypeId(product.getInterestCalculationDaysInYearType() == null
                        ? null
                        : toLong(product.getInterestCalculationDaysInYearType().getId()))
                .interestCalculationDaysInYearTypeCode(product.getInterestCalculationDaysInYearType() == null
                        ? null
                        : product.getInterestCalculationDaysInYearType().getCode())
                .build();
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private static String isoDate(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
