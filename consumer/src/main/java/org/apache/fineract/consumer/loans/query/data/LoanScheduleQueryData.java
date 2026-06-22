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

package org.apache.fineract.consumer.loans.query.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public final class LoanScheduleQueryData {

    private final String currency;
    private final Long totalPrincipalDisbursed;
    private final BigDecimal totalInterestCharged;
    private final BigDecimal totalRepaymentExpected;
    private final List<Period> periods;

    @Getter
    @RequiredArgsConstructor
    @Builder
    @EqualsAndHashCode
    @ToString
    public static final class Period {

        private final Integer period;
        private final LocalDate dueDate;
        private final Long principalDue;
        private final Long totalDueForPeriod;
        private final Long outstandingBalance;
    }
}
