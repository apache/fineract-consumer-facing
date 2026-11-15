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
package org.apache.fineract.consumer.beneficiaries.command.service;

import java.math.BigDecimal;

/**
 * The scheduled transfers paying into a beneficiary's account. The scheduledtransfers module implements it, since it
 * depends on this module for its destination checks and this module must not depend on it in turn.
 *
 * <p>
 * Only transfers the customer set up through the BFF count, including one whose confirm is still running. Bank-created
 * standing instructions do not, since the customer can neither see nor cancel them. Implementations read Fineract, and
 * its failures surface as {@code FeignException}.
 */
public interface BeneficiaryScheduledTransferLookupPort {

    /** Whether the customer has an active scheduled transfer to the account, or one still being set up. */
    boolean hasActiveTransfersTo(Long userId, Long fineractClientId, Long fineractAccountId);

    /**
     * Whether the customer has an active scheduled transfer to the account for more than {@code limit}, or one still
     * being set up, whose amount is not known yet.
     */
    boolean hasActiveTransfersAbove(Long userId, Long fineractClientId, Long fineractAccountId, BigDecimal limit);
}
