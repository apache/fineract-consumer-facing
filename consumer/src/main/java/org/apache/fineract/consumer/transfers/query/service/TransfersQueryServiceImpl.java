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

package org.apache.fineract.consumer.transfers.query.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.AccountTransfersApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.AccountTransferData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetAccountTransfersPageItems;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetAccountTransfersPageItemsToAccountType;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdAccountsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsSavingsAccounts;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountTransactionData;
import org.apache.fineract.consumer.infrastructure.fineractclient.service.FineractCaller;
import org.apache.fineract.consumer.transfers.query.data.TransferDirection;
import org.apache.fineract.consumer.transfers.query.data.TransferHistoryQuery;
import org.apache.fineract.consumer.transfers.query.data.TransferQueryData;
import org.apache.fineract.consumer.transfers.query.data.TransferQueryResponse;
import org.apache.fineract.consumer.transfers.query.exception.TransferQueryAccessDeniedException;
import org.apache.fineract.consumer.transfers.query.exception.TransferQueryUpstreamUnavailableException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransfersQueryServiceImpl implements TransfersQueryService {

    private static final String TRANSACTIONS_ASSOCIATION = "transactions";
    private static final Long LOAN_ACCOUNT_TYPE_ID = 1L;
    private static final int ACCOUNT_NO_PADDED_WIDTH = 9;
    private static final String ACCOUNT_NO_PAD_CHAR = "0";

    private final ClientApi clientApi;
    private final SavingsAccountApi savingsAccountApi;
    private final AccountTransfersApi accountTransfersApi;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final UserClientResolver userClientResolver;

    @Override
    public TransferQueryResponse listTransfers(Jwt jwt, TransferHistoryQuery query) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.TRANSFER_LIST);
        Long clientId = userClientResolver.resolveClientId(jwt);
        Map<Long, TransferLegs> legsByTransferId = collectLegs(jwt, clientId);
        resolveExternalDestinations(legsByTransferId);
        List<TransferLegs> matches = legsByTransferId.values().stream()
                .filter(legs -> withinRange(legs.transfer.getTransferDate(), query.getFromDate(), query.getToDate()))
                .sorted(newestFirst())
                .toList();
        List<TransferQueryData> content = matches.stream()
                .skip((long) query.getPage() * query.getSize())
                .limit(query.getSize())
                .map(TransfersQueryServiceImpl::toQueryData)
                .toList();
        return toTransferPage(content, query.getPage(), query.getSize(), matches.size());
    }

    private static TransferQueryResponse toTransferPage(
            List<TransferQueryData> content, int page, int size, long totalElements) {
        return TransferQueryResponse.builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages((int) Math.ceil(totalElements / (double) size))
                .build();
    }

    private Map<Long, TransferLegs> collectLegs(Jwt jwt, Long clientId) {
        GetClientsClientIdAccountsResponse accounts = fetch(() -> clientApi.retrieveAllClientAccounts(clientId));
        Map<Long, TransferLegs> legsByTransferId = new LinkedHashMap<>();
        if (accounts == null || accounts.getSavingsAccounts() == null) {
            return legsByTransferId;
        }
        for (GetClientsSavingsAccounts account : accounts.getSavingsAccounts()) {
            Long accountId = account.getId();
            accessPolicyEvaluator.authorize(jwt, ConsumerAction.SAVINGS_VIEW, accountId,
                    TransferQueryAccessDeniedException::new);
            SavingsAccountData details = fetch(() ->
                    savingsAccountApi.retrieveSavingsAccount(accountId, null, null, TRANSACTIONS_ASSOCIATION));
            mergeAccountLegs(legsByTransferId, details);
        }
        return legsByTransferId;
    }

    private void resolveExternalDestinations(Map<Long, TransferLegs> legsByTransferId) {
        Iterator<TransferLegs> legsIterator = legsByTransferId.values().iterator();
        while (legsIterator.hasNext()) {
            TransferLegs legs = legsIterator.next();
            if (legs.toAccountNo != null) {
                continue;
            }
            GetAccountTransfersPageItems details = fetch(() -> accountTransfersApi.retrieveOne6(legs.transfer.getId()));
            if (isLoanDestination(details)) {
                legsIterator.remove();
            } else {
                legs.toAccountNo = destinationAccountNo(details);
            }
        }
    }

    private static boolean isLoanDestination(GetAccountTransfersPageItems details) {
        if (details == null) {
            return false;
        }
        GetAccountTransfersPageItemsToAccountType toAccountType = details.getToAccountType();
        return toAccountType != null && LOAN_ACCOUNT_TYPE_ID.equals(toAccountType.getId());
    }

    private static String destinationAccountNo(GetAccountTransfersPageItems details) {
        if (details == null || details.getToAccount() == null || details.getToAccount().getAccountNo() == null) {
            return null;
        }
        String accountNo = String.valueOf(details.getToAccount().getAccountNo());
        if (accountNo.length() >= ACCOUNT_NO_PADDED_WIDTH) {
            return accountNo;
        }
        return ACCOUNT_NO_PAD_CHAR.repeat(ACCOUNT_NO_PADDED_WIDTH - accountNo.length()) + accountNo;
    }

    private static void mergeAccountLegs(Map<Long, TransferLegs> legsByTransferId, SavingsAccountData details) {
        if (details == null || details.getTransactions() == null) {
            return;
        }
        for (SavingsAccountTransactionData transaction : details.getTransactions()) {
            AccountTransferData transfer = transaction.getTransfer();
            if (transfer == null || transfer.getId() == null) {
                continue;
            }
            TransferLegs legs = legsByTransferId.computeIfAbsent(transfer.getId(), id -> new TransferLegs(transfer));
            if (isWithdrawal(transaction)) {
                legs.fromAccountNo = details.getAccountNo();
            } else {
                legs.toAccountNo = details.getAccountNo();
            }
        }
    }

    private static boolean isWithdrawal(SavingsAccountTransactionData transaction) {
        return transaction.getTransactionType() != null
                && Boolean.TRUE.equals(transaction.getTransactionType().getWithdrawal());
    }

    private static boolean withinRange(LocalDate date, LocalDate fromDate, LocalDate toDate) {
        if (date == null) {
            return fromDate == null && toDate == null;
        }
        if (fromDate != null && date.isBefore(fromDate)) {
            return false;
        }
        return toDate == null || !date.isAfter(toDate);
    }

    private static Comparator<TransferLegs> newestFirst() {
        return Comparator
                .comparing((TransferLegs legs) -> legs.transfer.getTransferDate(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(legs -> legs.transfer.getId(), Comparator.reverseOrder());
    }

    private static TransferQueryData toQueryData(TransferLegs legs) {
        AccountTransferData transfer = legs.transfer;
        return TransferQueryData.builder()
                .transferId(transfer.getId())
                .date(transfer.getTransferDate())
                .amount(transfer.getTransferAmount())
                .currency(transfer.getCurrency() == null ? null : transfer.getCurrency().getCode())
                .fromAccount(legs.fromAccountNo)
                .toAccount(legs.toAccountNo)
                .direction(legs.fromAccountNo != null ? TransferDirection.OUTGOING : TransferDirection.INCOMING)
                .build();
    }

    private <T> T fetch(Supplier<T> call) {
        return FineractCaller.call(call,
                TransferQueryUpstreamUnavailableException::new,
                TransferQueryUpstreamUnavailableException::new,
                TransferQueryUpstreamUnavailableException::new);
    }

    private static final class TransferLegs {

        private final AccountTransferData transfer;
        private String fromAccountNo;
        private String toAccountNo;

        private TransferLegs(AccountTransferData transfer) {
            this.transfer = transfer;
        }
    }
}
