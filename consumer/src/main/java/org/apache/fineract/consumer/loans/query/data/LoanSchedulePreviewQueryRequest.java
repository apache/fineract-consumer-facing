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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public final class LoanSchedulePreviewQueryRequest {

    @NotNull
    @Positive
    private final Long productId;

    @NotNull
    @Positive
    private final BigDecimal principal;

    @NotNull
    private final Integer loanTermFrequency;

    @NotNull
    private final Integer loanTermFrequencyType;

    @NotNull
    private final Integer numberOfRepayments;

    @NotNull
    private final Integer repaymentEvery;

    @NotNull
    private final Integer repaymentFrequencyType;

    @NotNull
    private final BigDecimal interestRatePerPeriod;

    @NotNull
    private final Integer amortizationType;

    @NotNull
    private final Integer interestType;

    @NotNull
    private final Integer interestCalculationPeriodType;

    @NotBlank
    private final String transactionProcessingStrategyCode;

    @NotNull
    private final LocalDate expectedDisbursementDate;

    @NotNull
    private final LocalDate submittedOnDate;
}
