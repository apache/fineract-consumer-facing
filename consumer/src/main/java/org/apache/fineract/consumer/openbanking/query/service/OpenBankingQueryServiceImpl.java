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

package org.apache.fineract.consumer.openbanking.query.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.apache.fineract.consumer.loans.query.data.LoanAccountListItemQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanAccountQueryData;
import org.apache.fineract.consumer.loans.query.service.LoansQueryService;
import org.apache.fineract.consumer.openbanking.query.data.ConsentStatus;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingAccountQueryData;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingBalanceQueryData;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingPermission;
import org.apache.fineract.consumer.openbanking.query.domain.OpenBankingAccountId;
import org.apache.fineract.consumer.openbanking.query.domain.OpenBankingConsentQueryEntity;
import org.apache.fineract.consumer.openbanking.query.exception.OpenBankingAccountNotFoundException;
import org.apache.fineract.consumer.openbanking.query.exception.OpenBankingConsentInvalidException;
import org.apache.fineract.consumer.openbanking.query.repository.OpenBankingConsentQueryRepository;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountListItemQueryData;
import org.apache.fineract.consumer.savings.query.data.SavingsAccountQueryData;
import org.apache.fineract.consumer.savings.query.service.SavingsQueryService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OpenBankingQueryServiceImpl implements OpenBankingQueryService {

    private static final String BALANCE_TYPE_CLOSING_BOOKED = "ClosingBooked";
    private static final String BALANCE_TYPE_INTERIM_AVAILABLE = "InterimAvailable";

    private final OpenBankingConsentQueryRepository openBankingConsentQueryRepository;
    private final SavingsQueryService savingsQueryService;
    private final LoansQueryService loansQueryService;
    private final AccessPolicyEvaluator accessPolicyEvaluator;

    @Override
    @Transactional(readOnly = true)
    public List<OpenBankingAccountQueryData> listAccounts(Jwt jwt) {
        requireValidConsent(jwt, OpenBankingPermission.READ_ACCOUNTS_BASIC);
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.OPENBANKING_ACCOUNTS_VIEW);
        List<OpenBankingAccountQueryData> accounts = new ArrayList<>();
        savingsQueryService.listAccounts(jwt).forEach(account -> accounts.add(toAccountData(account)));
        loansQueryService.listAccounts(jwt).forEach(account -> accounts.add(toAccountData(account)));
        return accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OpenBankingBalanceQueryData> getBalances(Jwt jwt, String accountId) {
        requireValidConsent(jwt, OpenBankingPermission.READ_BALANCES);
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.OPENBANKING_ACCOUNTS_VIEW);
        OpenBankingAccountId parsed = OpenBankingAccountId.parse(accountId).orElseThrow(OpenBankingAccountNotFoundException::new);
        return switch (parsed.getType()) {
            case SAVINGS -> savingsBalances(jwt, parsed);
            case LOAN -> loanBalances(jwt, parsed);
        };
    }

    private void requireValidConsent(Jwt jwt, OpenBankingPermission requiredPermission) {
        UUID consentId = parseUuid(jwt.getClaimAsString(OAuth2Constants.CONSENT_ID));
        if (consentId == null) {
            throw new OpenBankingConsentInvalidException();
        }
        OpenBankingConsentQueryEntity consent = openBankingConsentQueryRepository.findById(consentId)
                .orElseThrow(OpenBankingConsentInvalidException::new);
        UUID subject = parseUuid(jwt.getSubject());
        boolean valid = consent.getStatus() == ConsentStatus.AUTHORISED
                && consent.getExpiresAt().isAfter(Instant.now())
                && consent.getTppClientId().equals(tokenTppClientId(jwt))
                && consent.getUserId() != null && consent.getUserId().equals(subject)
                && consent.getPermissions().contains(requiredPermission);
        if (!valid) {
            throw new OpenBankingConsentInvalidException();
        }
    }

    private List<OpenBankingBalanceQueryData> savingsBalances(Jwt jwt, OpenBankingAccountId accountId) {
        SavingsAccountListItemQueryData listItem = savingsQueryService.listAccounts(jwt).stream()
                .filter(account -> accountId.getFineractId().equals(account.getId()))
                .findFirst()
                .orElseThrow(OpenBankingAccountNotFoundException::new);
        SavingsAccountQueryData account = savingsQueryService.getAccount(jwt, accountId.getFineractId());
        List<OpenBankingBalanceQueryData> balances = new ArrayList<>();
        addBalance(balances, accountId, BALANCE_TYPE_CLOSING_BOOKED, account.getBalance(),
                listItem.getCurrency());
        addBalance(balances, accountId, BALANCE_TYPE_INTERIM_AVAILABLE, account.getAvailableBalance(),
                listItem.getCurrency());
        return balances;
    }

    private List<OpenBankingBalanceQueryData> loanBalances(Jwt jwt, OpenBankingAccountId accountId) {
        loansQueryService.listAccounts(jwt).stream()
                .filter(account -> accountId.getFineractId().equals(account.getId()))
                .findFirst()
                .orElseThrow(OpenBankingAccountNotFoundException::new);
        LoanAccountQueryData loan = loansQueryService.getLoan(jwt, accountId.getFineractId());
        List<OpenBankingBalanceQueryData> balances = new ArrayList<>();
        addBalance(balances, accountId, BALANCE_TYPE_CLOSING_BOOKED, loan.getTotalOutstanding(),
                loan.getCurrency());
        return balances;
    }

    private static void addBalance(List<OpenBankingBalanceQueryData> balances, OpenBankingAccountId accountId,
            String type, BigDecimal amount, String currency) {
        if (amount == null) {
            return;
        }
        balances.add(OpenBankingBalanceQueryData.builder()
                .accountId(accountId.value())
                .type(type)
                .amount(amount)
                .currency(currency)
                .build());
    }

    private static OpenBankingAccountQueryData toAccountData(SavingsAccountListItemQueryData account) {
        return OpenBankingAccountQueryData.builder()
                .accountId(OpenBankingAccountId.compose(OpenBankingAccountId.Type.SAVINGS, account.getId()))
                .accountType(OpenBankingAccountId.Type.SAVINGS.getPrefix())
                .accountNo(account.getAccountNo())
                .productName(account.getProductName())
                .status(account.getStatus())
                .currency(account.getCurrency())
                .build();
    }

    private static OpenBankingAccountQueryData toAccountData(LoanAccountListItemQueryData account) {
        return OpenBankingAccountQueryData.builder()
                .accountId(OpenBankingAccountId.compose(OpenBankingAccountId.Type.LOAN, account.getId()))
                .accountType(OpenBankingAccountId.Type.LOAN.getPrefix())
                .accountNo(account.getAccountNo())
                .productName(account.getProductName())
                .status(account.getStatus())
                .currency(account.getCurrency())
                .build();
    }

    private static String tokenTppClientId(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        return audience == null || audience.isEmpty() ? null : audience.get(0);
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
