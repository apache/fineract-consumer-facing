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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.beneficiaries.query.domain.BeneficiaryAccountType;
import org.apache.fineract.consumer.beneficiaries.query.domain.BeneficiaryQueryEntity;
import org.apache.fineract.consumer.beneficiaries.query.data.BeneficiaryQueryData;
import org.apache.fineract.consumer.beneficiaries.query.data.BeneficiaryTemplateQueryData;
import org.apache.fineract.consumer.beneficiaries.query.repository.BeneficiaryQueryRepository;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.query.Query;
import org.apache.fineract.consumer.infrastructure.web.UserClientResolver;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BeneficiariesQueryServiceImpl implements BeneficiariesQueryService {

    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final UserClientResolver userClientResolver;
    private final BeneficiaryQueryRepository repository;

    @Override
    @Query
    @Transactional(readOnly = true)
    public List<BeneficiaryQueryData> listBeneficiaries(Jwt jwt) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.BENEFICIARY_LIST);
        Long userId = userClientResolver.resolveUserId(jwt);
        return repository.findAllByUserIdAndActiveTrueOrderByNameAsc(userId).stream()
                .map(BeneficiariesQueryServiceImpl::toQueryData)
                .toList();
    }

    @Override
    @Query
    public BeneficiaryTemplateQueryData getTemplate(Jwt jwt) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.BENEFICIARY_LIST);
        return BeneficiaryTemplateQueryData.builder()
                .accountTypeOptions(Arrays.stream(BeneficiaryAccountType.values())
                        .map(Enum::name)
                        .toList())
                .build();
    }

    @Override
    @Query
    @Transactional(readOnly = true)
    public Optional<BeneficiaryQueryData> findActiveByAccount(
            Long userId, Long fineractAccountId, BeneficiaryAccountType accountType) {
        return repository
                .findByUserIdAndFineractAccountIdAndAccountTypeAndActiveTrue(userId, fineractAccountId, accountType)
                .map(BeneficiariesQueryServiceImpl::toQueryData);
    }

    private static BeneficiaryQueryData toQueryData(BeneficiaryQueryEntity beneficiary) {
        return BeneficiaryQueryData.builder()
                .publicId(beneficiary.getPublicId())
                .name(beneficiary.getName())
                .accountType(beneficiary.getAccountType())
                .transferLimit(beneficiary.getTransferLimit())
                .build();
    }
}
