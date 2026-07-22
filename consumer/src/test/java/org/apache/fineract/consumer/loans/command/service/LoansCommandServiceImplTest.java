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

package org.apache.fineract.consumer.loans.command.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.time.LocalDate;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.OwnedAccountsCache;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PostLoansResponse;
import org.apache.fineract.consumer.loans.command.data.SubmitLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.exception.LoanCommandUpstreamUnavailableException;
import org.apache.fineract.consumer.user.query.domain.UserStatus;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class LoansCommandServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long CLIENT_ID = 42L;
    private static final Long LOAN_ID = 7L;
    private static final String EMAIL = "user@test.com";

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private OwnedAccountsCache ownedAccountsCache;

    @Mock
    private LoansApi loansApi;

    @InjectMocks
    private LoansCommandServiceImpl service;

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .claim("scope", "read")
                .build();
    }

    private static UserQueryData user() {
        return UserQueryData.builder()
                .id(1L)
                .publicId(PUBLIC_ID)
                .fineractClientId(CLIENT_ID)
                .email(EMAIL)
                .status(UserStatus.BOUND)
                .build();
    }

    @Test
    void submitAuthorizesLoanApplicationSubmit() {
        Jwt jwt = jwt();
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(loansApi.calculateLoanScheduleOrSubmitLoanApplication(any(), isNull()))
                .thenReturn(new PostLoansResponse().loanId(LOAN_ID).resourceId(99L));

        service.submitApplication(jwt, SubmitLoanApplicationCommand.builder()
                .productId(1L)
                .expectedDisbursementDate(LocalDate.of(2026, 7, 1))
                .submittedOnDate(LocalDate.of(2026, 7, 1))
                .build());

        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.LOAN_APPLICATION_SUBMIT);
        verify(ownedAccountsCache).evict(CLIENT_ID);
    }

    @Test
    void submitDoesNotEvictOwnershipCacheWhenUpstreamFails() {
        Jwt jwt = jwt();
        when(userQueryService.findByPublicId(PUBLIC_ID)).thenReturn(user());
        when(loansApi.calculateLoanScheduleOrSubmitLoanApplication(any(), isNull()))
                .thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> service.submitApplication(jwt, SubmitLoanApplicationCommand.builder()
                .productId(1L)
                .expectedDisbursementDate(LocalDate.of(2026, 7, 1))
                .submittedOnDate(LocalDate.of(2026, 7, 1))
                .build()))
                .isInstanceOf(LoanCommandUpstreamUnavailableException.class);

        verify(ownedAccountsCache, never()).evict(any());
    }
}
