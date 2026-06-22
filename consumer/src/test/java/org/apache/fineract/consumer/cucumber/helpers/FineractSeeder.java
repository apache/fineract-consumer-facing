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

package org.apache.fineract.consumer.cucumber.helpers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Feign;
import feign.RequestInterceptor;
import feign.auth.BasicAuthRequestInterceptor;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.okhttp.OkHttpClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.apache.fineract.consumer.infrastructure.fineractclient.FineractHeaders;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientIdentifierApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.CodeValuesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.CodesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.CurrencyApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoanProductsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountTransactionsApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsProductApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.CurrencyConfigurationData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.CurrencyData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.CurrencyUpdateRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetCodeValuesDataResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetCodesResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostClientsClientIdIdentifiersRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostClientsRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoanProductsRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansLoanIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountTransactionsRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsAccountIdRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsAccountsRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostSavingsProductsRequest;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

public class FineractSeeder {

    private static final String BASE_URL = System.getenv().getOrDefault(
            "FINERACT_BASE_URL", "http://localhost:8888/fineract-provider/api");
    private static final String USERNAME = System.getenv().getOrDefault("FINERACT_USERNAME", "mifos");
    private static final String PASSWORD = System.getenv().getOrDefault("FINERACT_PASSWORD", "password");
    private static final String TENANT = System.getenv().getOrDefault("FINERACT_TENANT", "default");
    private static final long HEAD_OFFICE_ID = 1L;
    private static final String CUSTOMER_IDENTIFIER_CODE = "Customer Identifier";
    private static final String PASSPORT_DOCUMENT_TYPE = "Passport";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final long LEGAL_FORM_PERSON = 1L;
    private static final String LOCALE = "en";
    private static final String DATE_FORMAT = "dd MMMM yyyy";
    private static final String FIXED_DATE = "01 January 2023";
    private static final String USD = "USD";
    private static final String ACTIVATE_COMMAND = "activate";
    private static final String APPROVE_COMMAND = "approve";
    private static final String DISBURSE_COMMAND = "disburse";
    private static final String DEPOSIT_COMMAND = "deposit";
    private static final int SEED_PAYMENT_TYPE_ID = 2;
    private static final String FIRST_NAME = "Test";
    private static final String LAST_NAME_PREFIX = "User-";
    private static final String DOCUMENT_KEY_PREFIX = "PASS-";
    private static final String TRANSACTION_PROCESSING_STRATEGY = "mifos-standard-strategy";
    private static final String LOAN_TYPE_INDIVIDUAL = "individual";
    private static final String SAVINGS_PRODUCT_NAME_PREFIX = "CUKE-SAV-";
    private static final String LOAN_PRODUCT_NAME_PREFIX = "CUKE-LOAN-";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final ClientApi CLIENTS = buildFeignClient(ClientApi.class);
    private static final ClientIdentifierApi IDENTIFIERS = buildFeignClient(ClientIdentifierApi.class);
    private static final CodesApi CODES = buildFeignClient(CodesApi.class);
    private static final CodeValuesApi CODE_VALUES = buildFeignClient(CodeValuesApi.class);
    private static final CurrencyApi CURRENCIES = buildFeignClient(CurrencyApi.class);
    private static final SavingsProductApi SAVINGS_PRODUCTS = buildFeignClient(SavingsProductApi.class);
    private static final SavingsAccountApi SAVINGS_ACCOUNTS = buildFeignClient(SavingsAccountApi.class);
    private static final SavingsAccountTransactionsApi SAVINGS_TRANSACTIONS =
            buildFeignClient(SavingsAccountTransactionsApi.class);
    private static final LoanProductsApi LOAN_PRODUCTS = buildFeignClient(LoanProductsApi.class);
    private static final LoansApi LOANS = buildFeignClient(LoansApi.class);

    private static volatile Long cachedPassportCodeValueId;
    private static volatile Long cachedSavingsProductId;
    private static volatile Long cachedLoanProductId;

    public record SeededClient(long fineractClientId, String documentTypeName, String documentKey) {}

    public record SeededClientWithAccounts(
            long fineractClientId,
            String documentTypeName,
            String documentKey,
            long savingsAccountId,
            long loanAccountId) {}

    public SeededClient seedClientWithPassport() {
        long clientId = createClient();
        String documentKey = DOCUMENT_KEY_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        attachPassportIdentifier(clientId, documentKey);
        return new SeededClient(clientId, PASSPORT_DOCUMENT_TYPE, documentKey);
    }

    public SeededClientWithAccounts seedActiveClientWithAccounts() {
        ensureUsdCurrency();
        long clientId = createActiveClient();
        String documentKey = DOCUMENT_KEY_PREFIX + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        attachPassportIdentifier(clientId, documentKey);
        long savingsAccountId = createActiveSavingsAccount(clientId);
        long loanAccountId = createDisbursedLoanAccount(clientId);
        return new SeededClientWithAccounts(
                clientId, PASSPORT_DOCUMENT_TYPE, documentKey, savingsAccountId, loanAccountId);
    }

    public void depositToSavings(long savingsId, BigDecimal amount) {
        SAVINGS_TRANSACTIONS.createSavingsAccountTransaction(savingsId,
                new PostSavingsAccountTransactionsRequest()
                        .transactionAmount(amount)
                        .transactionDate(FIXED_DATE)
                        .paymentTypeId(SEED_PAYMENT_TYPE_ID)
                        .locale(LOCALE)
                        .dateFormat(DATE_FORMAT),
                DEPOSIT_COMMAND);
    }

    private long createActiveClient() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        PostClientsRequest body = new PostClientsRequest()
                .officeId(HEAD_OFFICE_ID)
                .firstname(FIRST_NAME)
                .lastname(LAST_NAME_PREFIX + suffix)
                .active(true)
                .activationDate(FIXED_DATE)
                .legalFormId(LEGAL_FORM_PERSON)
                .locale(LOCALE)
                .dateFormat(DATE_FORMAT);
        return CLIENTS.createClient(body).getClientId();
    }

    private long createActiveSavingsAccount(long clientId) {
        long productId = savingsProductId();
        PostSavingsAccountsRequest submit = new PostSavingsAccountsRequest()
                .clientId(clientId)
                .productId(productId)
                .submittedOnDate(FIXED_DATE)
                .locale(LOCALE)
                .dateFormat(DATE_FORMAT);
        long savingsId = SAVINGS_ACCOUNTS.submitSavingsApplication(submit).getSavingsId();
        SAVINGS_ACCOUNTS.handleCommandsSavingsAccount(savingsId,
                new PostSavingsAccountsAccountIdRequest()
                        .approvedOnDate(FIXED_DATE)
                        .locale(LOCALE)
                        .dateFormat(DATE_FORMAT),
                APPROVE_COMMAND);
        SAVINGS_ACCOUNTS.handleCommandsSavingsAccount(savingsId,
                new PostSavingsAccountsAccountIdRequest()
                        .activatedOnDate(FIXED_DATE)
                        .locale(LOCALE)
                        .dateFormat(DATE_FORMAT),
                ACTIVATE_COMMAND);
        return savingsId;
    }

    private long createDisbursedLoanAccount(long clientId) {
        long productId = loanProductId();
        PostLoansRequest submit = new PostLoansRequest()
                .clientId(clientId)
                .productId(productId)
                .principal(new BigDecimal("10000"))
                .loanTermFrequency(5)
                .loanTermFrequencyType(2)
                .numberOfRepayments(5)
                .repaymentEvery(1)
                .repaymentFrequencyType(2)
                .interestRatePerPeriod(new BigDecimal("2"))
                .amortizationType(1)
                .interestType(1)
                .interestCalculationPeriodType(1)
                .transactionProcessingStrategyCode(TRANSACTION_PROCESSING_STRATEGY)
                .expectedDisbursementDate(FIXED_DATE)
                .submittedOnDate(FIXED_DATE)
                .loanType(LOAN_TYPE_INDIVIDUAL)
                .locale(LOCALE)
                .dateFormat(DATE_FORMAT);
        long loanId = LOANS.calculateLoanScheduleOrSubmitLoanApplication(submit, null).getLoanId();
        LOANS.stateTransitions(loanId,
                new PostLoansLoanIdRequest()
                        .approvedOnDate(FIXED_DATE)
                        .locale(LOCALE)
                        .dateFormat(DATE_FORMAT),
                APPROVE_COMMAND);
        LOANS.stateTransitions(loanId,
                new PostLoansLoanIdRequest()
                        .actualDisbursementDate(FIXED_DATE)
                        .locale(LOCALE)
                        .dateFormat(DATE_FORMAT),
                DISBURSE_COMMAND);
        return loanId;
    }

    private long savingsProductId() {
        Long cached = cachedSavingsProductId;
        if (cached != null) {
            return cached;
        }
        synchronized (FineractSeeder.class) {
            if (cachedSavingsProductId != null) {
                return cachedSavingsProductId;
            }
            String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            PostSavingsProductsRequest body = new PostSavingsProductsRequest()
                    .name(SAVINGS_PRODUCT_NAME_PREFIX + suffix)
                    .shortName(suffix)
                    .currencyCode(USD)
                    .digitsAfterDecimal(4)
                    .nominalAnnualInterestRate(5.0)
                    .interestCompoundingPeriodType(4)
                    .interestPostingPeriodType(4)
                    .interestCalculationType(1)
                    .interestCalculationDaysInYearType(365)
                    .accountingRule(1)
                    .locale(LOCALE);
            long productId = SAVINGS_PRODUCTS.createSavingsProduct(body).getResourceId();
            cachedSavingsProductId = productId;
            return productId;
        }
    }

    private long loanProductId() {
        Long cached = cachedLoanProductId;
        if (cached != null) {
            return cached;
        }
        synchronized (FineractSeeder.class) {
            if (cachedLoanProductId != null) {
                return cachedLoanProductId;
            }
            String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            PostLoanProductsRequest body = new PostLoanProductsRequest()
                    .name(LOAN_PRODUCT_NAME_PREFIX + suffix)
                    .shortName(suffix)
                    .currencyCode(USD)
                    .digitsAfterDecimal(2)
                    .principal(10000.0)
                    .numberOfRepayments(5)
                    .repaymentEvery(1)
                    .repaymentFrequencyType(2L)
                    .interestRatePerPeriod(2.0)
                    .interestRateFrequencyType(2)
                    .amortizationType(1)
                    .interestType(1)
                    .interestCalculationPeriodType(1)
                    .transactionProcessingStrategyCode(TRANSACTION_PROCESSING_STRATEGY)
                    .accountingRule(1)
                    .daysInYearType(365)
                    .daysInMonthType(30)
                    .isInterestRecalculationEnabled(false)
                    .locale(LOCALE);
            long productId = LOAN_PRODUCTS.createLoanProduct(body).getResourceId();
            cachedLoanProductId = productId;
            return productId;
        }
    }

    private void ensureUsdCurrency() {
        CurrencyConfigurationData config = CURRENCIES.retrieveCurrencies();
        List<CurrencyData> selected = config.getSelectedCurrencyOptions();
        boolean usdEnabled = selected != null && selected.stream()
                .anyMatch(currency -> USD.equals(currency.getCode()));
        if (usdEnabled) {
            return;
        }
        List<String> codes = new ArrayList<>();
        if (selected != null) {
            selected.forEach(currency -> codes.add(currency.getCode()));
        }
        codes.add(USD);
        CURRENCIES.updateCurrencies(new CurrencyUpdateRequest().currencies(codes));
    }

    private long createClient() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        PostClientsRequest body = new PostClientsRequest()
                .officeId(HEAD_OFFICE_ID)
                .firstname(FIRST_NAME)
                .lastname(LAST_NAME_PREFIX + suffix)
                .active(false)
                .legalFormId(LEGAL_FORM_PERSON)
                .locale(LOCALE)
                .dateFormat(DATE_FORMAT);
        return CLIENTS.createClient(body).getClientId();
    }

    private void attachPassportIdentifier(long clientId, String documentKey) {
        long passportId = resolvePassportCodeValueId();
        PostClientsClientIdIdentifiersRequest body = new PostClientsClientIdIdentifiersRequest()
                .documentTypeId(passportId)
                .documentKey(documentKey)
                .status(ACTIVE_STATUS);
        IDENTIFIERS.createClientIdentifier(clientId, body);
    }

    private long resolvePassportCodeValueId() {
        Long cached = cachedPassportCodeValueId;
        if (cached != null) {
            return cached;
        }
        synchronized (FineractSeeder.class) {
            if (cachedPassportCodeValueId != null) {
                return cachedPassportCodeValueId;
            }
            long codeId = findIdByName(CODES.retrieveCodes(), GetCodesResponse::getName, GetCodesResponse::getId,
                    CUSTOMER_IDENTIFIER_CODE);
            long passportId = findIdByName(CODE_VALUES.retrieveAllCodeValues(codeId),
                    GetCodeValuesDataResponse::getName, GetCodeValuesDataResponse::getId, PASSPORT_DOCUMENT_TYPE);
            cachedPassportCodeValueId = passportId;
            return passportId;
        }
    }

    private <T> long findIdByName(List<T> entries, Function<T, String> name, Function<T, Long> id, String target) {
        return entries.stream()
                .filter(entry -> target.equals(name.apply(entry)))
                .map(id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fineract entry not found: " + target));
    }

    private static <T> T buildFeignClient(Class<T> apiType) {
        return Feign.builder()
                .contract(new SpringMvcContract())
                .client(new OkHttpClient())
                .encoder(new JacksonEncoder(OBJECT_MAPPER))
                .decoder(new JacksonDecoder(OBJECT_MAPPER))
                .requestInterceptor(new BasicAuthRequestInterceptor(USERNAME, PASSWORD))
                .requestInterceptor(tenantInterceptor())
                .target(apiType, BASE_URL);
    }

    private static RequestInterceptor tenantInterceptor() {
        return template -> template.header(FineractHeaders.TENANT_ID, TENANT);
    }
}
