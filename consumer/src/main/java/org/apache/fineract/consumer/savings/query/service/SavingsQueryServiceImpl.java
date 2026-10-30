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
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
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
import org.apache.fineract.consumer.infrastructure.fineractclient.service.FineractCaller;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.SavingsFineractClient;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountListItemQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsApplicationTemplateQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsChargeQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsProductOptionQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionQueryResponse;
import org.apache.fineract.consumer.savings.query.data.SavingsTransactionSearchQuery;
import org.apache.fineract.consumer.savings.query.exception.SavingsAccountNotFoundException;
import org.apache.fineract.consumer.savings.query.exception.SavingsProductNotFoundException;
import org.apache.fineract.consumer.savings.query.exception.SavingsQueryAccessDeniedException;
import org.apache.fineract.consumer.savings.query.exception.SavingsRequestInvalidException;
import org.apache.fineract.consumer.savings.query.exception.SavingsUpstreamUnavailableException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SavingsQueryServiceImpl implements SavingsQueryService {

    private static final String LOCALE = "en";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private final SavingsFineractClient savingsFineractClient;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final UserClientResolver userClientResolver;

    @Override
    public List<SavingsAccountListItemQueryData> listAccounts(Jwt jwt) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_LIST);
        Long clientId = userClientResolver.resolveClientId(jwt);
        GetClientsClientIdAccountsResponse accounts = fetch(() -> savingsFineractClient.retrieveAllClientAccounts(clientId));
        if (accounts == null || accounts.getSavingsAccounts() == null) {
            return List.of();
        }
        return accounts.getSavingsAccounts().stream().map(this::toListItem).toList();
    }

    @Override
    public SavingsAccountQueryData getAccount(Jwt jwt, Long savingsId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_VIEW, savingsId,
                SavingsQueryAccessDeniedException::new);
        SavingsAccountData account = fetch(() -> savingsFineractClient.retrieveSavingsAccount(savingsId, null, null, null));
        return toAccountData(account);
    }

    @Override
    public List<SavingsChargeQueryData> getCharges(Jwt jwt, Long savingsId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_VIEW, savingsId,
                SavingsQueryAccessDeniedException::new);
        List<GetSavingsAccountsSavingsAccountIdChargesResponse> charges =
                fetch(() -> savingsFineractClient.retrieveAllSavingsAccountCharges(savingsId, null));
        if (charges == null) {
            return List.of();
        }
        return charges.stream().map(this::toChargeData).toList();
    }

    @Override
    public SavingsTransactionQueryResponse searchTransactions(Jwt jwt, SavingsTransactionSearchQuery query) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_VIEW, query.getSavingsId(),
                SavingsQueryAccessDeniedException::new);
        Integer limit = query.getSize();
        Integer offset = query.getPage() * query.getSize();
        String orderBy = orderByOf(query.getSort());
        String sortOrder = sortOrderOf(query.getSort());
        SavingsAccountTransactionsSearchResponse response = fetch(() ->
                savingsFineractClient.searchSavingsAccountTransactions(
                        query.getSavingsId(),
                        isoDate(query.getFromDate()),
                        isoDate(query.getToDate()),
                        null, null, null, null, null, null, null,
                        offset,
                        limit,
                        orderBy,
                        sortOrder,
                        LOCALE, DATE_FORMAT));
        if (response == null || response.getContent() == null) {
            return toTransactionPage(List.of(), query.getPage(), query.getSize(), 0L);
        }
        List<SavingsTransactionQueryData> content =
                response.getContent().stream().map(this::toTransactionData).toList();
        long totalElements = response.getTotal() == null ? 0L : response.getTotal();
        return toTransactionPage(content, query.getPage(), query.getSize(), totalElements);
    }

    @Override
    public SavingsTransactionQueryData getTransaction(Jwt jwt, Long savingsId, Long transactionId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_VIEW, savingsId,
                SavingsQueryAccessDeniedException::new);
        SavingsAccountTransactionData transaction = fetch(() ->
                savingsFineractClient.retrieveOneSavingsAccountTransaction(savingsId, transactionId));
        return toTransactionData(transaction);
    }

    @Override
    public SavingsApplicationTemplateQueryData getApplicationTemplate(Jwt jwt, Long productId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_APPLICATION_TEMPLATE_VIEW);
        if (productId == null) {
            List<GetSavingsProductsResponse> products = fetch(savingsFineractClient::retrieveAllSavingsProducts);
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
        return FineractCaller.call(() -> savingsFineractClient.retrieveOneSavingsProduct(productId),
                e -> new SavingsProductNotFoundException(),
                SavingsUpstreamUnavailableException::new,
                SavingsUpstreamUnavailableException::new);
    }

    private SavingsAccountListItemQueryData toListItem(GetClientsSavingsAccounts account) {
        return SavingsAccountListItemQueryData.builder()
                .id(account.getId())
                .accountNo(account.getAccountNo())
                .productName(account.getProductName())
                .status(account.getStatus() == null ? null : account.getStatus().getCode())
                .active(account.getStatus() != null && Boolean.TRUE.equals(account.getStatus().getActive()))
                .currency(account.getCurrency() == null ? null : account.getCurrency().getCode())
                .build();
    }

    private SavingsAccountQueryData toAccountData(SavingsAccountData account) {
        SavingsAccountSummaryData summary = account.getSummary();
        return SavingsAccountQueryData.builder()
                .id(account.getId())
                .accountNo(account.getAccountNo())
                .productName(account.getSavingsProductName())
                .status(account.getStatus() == null ? null : account.getStatus().getCode())
                .active(account.getStatus() != null && Boolean.TRUE.equals(account.getStatus().getActive()))
                .balance(summary == null ? null : summary.getAccountBalance())
                .availableBalance(summary == null ? null : summary.getAvailableBalance())
                .nominalAnnualInterestRate(account.getNominalAnnualInterestRate())
                .currency(account.getCurrency() == null ? null : account.getCurrency().getCode())
                .build();
    }

    private SavingsChargeQueryData toChargeData(GetSavingsAccountsSavingsAccountIdChargesResponse charge) {
        return SavingsChargeQueryData.builder()
                .id(charge.getId())
                .name(charge.getName())
                .amount(charge.getAmount())
                .amountOutstanding(charge.getAmountOutstanding())
                .currency(charge.getCurrency() == null ? null : charge.getCurrency().getCode())
                .build();
    }

    private SavingsTransactionQueryData toTransactionData(GetSavingsAccountTransactionsPageItem transaction) {
        return SavingsTransactionQueryData.builder()
                .id(transaction.getId())
                .type(transaction.getTransactionType() == null ? null : transaction.getTransactionType().getCode())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency() == null ? null : transaction.getCurrency().getCode())
                .runningBalance(transaction.getRunningBalance())
                .date(transaction.getDate())
                .build();
    }

    private SavingsTransactionQueryData toTransactionData(SavingsAccountTransactionData transaction) {
        return SavingsTransactionQueryData.builder()
                .id(transaction.getId())
                .type(transaction.getTransactionType() == null ? null : transaction.getTransactionType().getCode())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency() == null ? null : transaction.getCurrency().getCode())
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

    private static SavingsTransactionQueryResponse toTransactionPage(
            List<SavingsTransactionQueryData> content, int page, int size, long totalElements) {
        return SavingsTransactionQueryResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages((int) Math.ceil(totalElements / (double) size))
                .build();
    }

    private static String orderByOf(String sort) {
        if (sort == null) {
            return null;
        }
        int comma = sort.indexOf(',');
        return comma < 0 ? sort : sort.substring(0, comma);
    }

    private static String sortOrderOf(String sort) {
        if (sort == null) {
            return null;
        }
        int comma = sort.indexOf(',');
        return comma < 0 ? null : sort.substring(comma + 1);
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private static String isoDate(LocalDate date) {
        return date == null ? null : date.toString();
    }
}