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

package org.apache.fineract.consumer.beneficiaries.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.beneficiaries.query.domain.BeneficiaryAccountType;
import org.apache.fineract.consumer.beneficiaries.query.domain.BeneficiaryQueryEntity;
import org.apache.fineract.consumer.beneficiaries.query.data.BeneficiaryQueryData;
import org.apache.fineract.consumer.beneficiaries.query.data.BeneficiaryTemplateQueryData;
import org.apache.fineract.consumer.beneficiaries.query.repository.BeneficiaryQueryRepository;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.web.UserClientResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class BeneficiariesQueryServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long OFFICE_ID = 1L;
    private static final Long CLIENT_ID = 11L;
    private static final Long ACCOUNT_ID = 21L;
    private static final String ALICE_NAME = "Alice";
    private static final String BOB_NAME = "Bob";
    private static final BigDecimal TRANSFER_LIMIT = new BigDecimal("500.00");

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private UserClientResolver userClientResolver;

    @Mock
    private BeneficiaryQueryRepository repository;

    @InjectMocks
    private BeneficiariesQueryServiceImpl service;

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .build();
    }

    private static BeneficiaryQueryEntity beneficiary(String name, BeneficiaryAccountType accountType,
            BigDecimal transferLimit) {
        return BeneficiaryQueryEntity.of(PUBLIC_ID, USER_ID, name, OFFICE_ID, CLIENT_ID, ACCOUNT_ID,
                accountType, transferLimit);
    }

    @Test
    void listBeneficiariesMapsEntitiesToQueryData() {
        BeneficiaryQueryEntity savings = beneficiary(ALICE_NAME, BeneficiaryAccountType.SAVINGS, TRANSFER_LIMIT);
        BeneficiaryQueryEntity loan = beneficiary(BOB_NAME, BeneficiaryAccountType.LOAN, null);
        when(userClientResolver.resolveUserId(any(Jwt.class))).thenReturn(USER_ID);
        when(repository.findAllByUserIdAndActiveTrueOrderByNameAsc(USER_ID)).thenReturn(List.of(savings, loan));

        List<BeneficiaryQueryData> result = service.listBeneficiaries(jwt());

        verify(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.BENEFICIARY_LIST));
        assertThat(result).containsExactly(
                BeneficiaryQueryData.builder()
                        .publicId(PUBLIC_ID)
                        .name(ALICE_NAME)
                        .accountType(BeneficiaryAccountType.SAVINGS)
                        .transferLimit(TRANSFER_LIMIT)
                        .build(),
                BeneficiaryQueryData.builder()
                        .publicId(PUBLIC_ID)
                        .name(BOB_NAME)
                        .accountType(BeneficiaryAccountType.LOAN)
                        .transferLimit(null)
                        .build());
    }

    @Test
    void listBeneficiariesReturnsEmptyListWhenNoneRegistered() {
        when(userClientResolver.resolveUserId(any(Jwt.class))).thenReturn(USER_ID);
        when(repository.findAllByUserIdAndActiveTrueOrderByNameAsc(USER_ID)).thenReturn(List.of());

        assertThat(service.listBeneficiaries(jwt())).isEmpty();
    }

    @Test
    void listBeneficiariesDeniedWhenPolicyRejects() {
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.BENEFICIARY_LIST));

        assertThatThrownBy(() -> service.listBeneficiaries(jwt()))
                .isInstanceOf(AccessScopeInsufficientException.class)
                .hasFieldOrPropertyWithValue("code", AccessScopeInsufficientException.CODE);

        verify(repository, never()).findAllByUserIdAndActiveTrueOrderByNameAsc(anyLong());
    }

    @Test
    void getTemplateReturnsAllAccountTypeOptions() {
        BeneficiaryTemplateQueryData template = service.getTemplate(jwt());

        verify(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.BENEFICIARY_LIST));
        assertThat(template.getAccountTypeOptions()).containsExactly(
                BeneficiaryAccountType.SAVINGS.name(),
                BeneficiaryAccountType.LOAN.name());
    }

    @Test
    void getTemplateDeniedWhenPolicyRejects() {
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(any(Jwt.class), eq(ConsumerAction.BENEFICIARY_LIST));

        assertThatThrownBy(() -> service.getTemplate(jwt()))
                .isInstanceOf(AccessScopeInsufficientException.class);
    }

    @Test
    void findActiveByAccountMapsMatchWithoutAuthorize() {
        BeneficiaryQueryEntity match = beneficiary(ALICE_NAME, BeneficiaryAccountType.SAVINGS, TRANSFER_LIMIT);
        when(repository.findByUserIdAndFineractAccountIdAndAccountTypeAndActiveTrue(
                USER_ID, ACCOUNT_ID, BeneficiaryAccountType.SAVINGS)).thenReturn(Optional.of(match));

        Optional<BeneficiaryQueryData> result =
                service.findActiveByAccount(USER_ID, ACCOUNT_ID, BeneficiaryAccountType.SAVINGS);

        assertThat(result).contains(BeneficiaryQueryData.builder()
                .publicId(PUBLIC_ID)
                .name(ALICE_NAME)
                .accountType(BeneficiaryAccountType.SAVINGS)
                .transferLimit(TRANSFER_LIMIT)
                .build());
        verify(accessPolicyEvaluator, never()).authorize(any(Jwt.class), any());
    }

    @Test
    void findActiveByAccountReturnsEmptyWhenNoMatch() {
        when(repository.findByUserIdAndFineractAccountIdAndAccountTypeAndActiveTrue(
                USER_ID, ACCOUNT_ID, BeneficiaryAccountType.LOAN)).thenReturn(Optional.empty());

        assertThat(service.findActiveByAccount(USER_ID, ACCOUNT_ID, BeneficiaryAccountType.LOAN)).isEmpty();
    }
}
