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

package org.apache.fineract.consumer.savings.query.data;

import java.math.BigDecimal;
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
public final class SavingsApplicationTemplateQueryData {

    private final List<SavingsProductOptionQueryData> productOptions;

    private final Long productId;
    private final String productName;
    private final String shortName;
    private final String description;
    private final String currencyCode;

    private final BigDecimal nominalAnnualInterestRate;

    private final Long interestCompoundingPeriodTypeId;
    private final String interestCompoundingPeriodTypeCode;
    private final Long interestPostingPeriodTypeId;
    private final String interestPostingPeriodTypeCode;
    private final Long interestCalculationTypeId;
    private final String interestCalculationTypeCode;
    private final Long interestCalculationDaysInYearTypeId;
    private final String interestCalculationDaysInYearTypeCode;
}
