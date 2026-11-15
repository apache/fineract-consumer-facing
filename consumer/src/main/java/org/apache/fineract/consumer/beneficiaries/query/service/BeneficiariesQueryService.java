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

import java.util.List;
import java.util.Optional;
import org.apache.fineract.consumer.beneficiaries.query.data.BeneficiaryQueryData;
import org.springframework.security.oauth2.jwt.Jwt;

public interface BeneficiariesQueryService {

    List<BeneficiaryQueryData> listBeneficiaries(Jwt jwt);

    Optional<BeneficiaryQueryData> findActiveByAccount(Long userId, Long fineractAccountId);

    /**
     * Like {@link #findActiveByAccount}, but holds a share lock on the beneficiary until the caller's transaction ends,
     * so it waits for a delete or limit change in progress, and one that starts later waits for it. Must be called
     * inside the caller's read-write transaction.
     */
    Optional<BeneficiaryQueryData> findActiveByAccountForShare(Long userId, Long fineractAccountId);
}
