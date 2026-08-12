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

package org.apache.fineract.consumer.openbanking.query.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenBankingAccountIdTest {

    @Test
    void parsesSavingsCompositeId() {
        Optional<OpenBankingAccountId> parsed = OpenBankingAccountId.parse(OpenBankingAccountId.compose(OpenBankingAccountId.Type.SAVINGS, 21L));

        assertThat(parsed).hasValueSatisfying(accountId -> {
            assertThat(accountId.getType()).isEqualTo(OpenBankingAccountId.Type.SAVINGS);
            assertThat(accountId.getFineractId()).isEqualTo(21L);
        });
    }

    @Test
    void parsesLoanCompositeId() {
        Optional<OpenBankingAccountId> parsed = OpenBankingAccountId.parse(OpenBankingAccountId.compose(OpenBankingAccountId.Type.LOAN, 31L));

        assertThat(parsed).hasValueSatisfying(accountId -> {
            assertThat(accountId.getType()).isEqualTo(OpenBankingAccountId.Type.LOAN);
            assertThat(accountId.getFineractId()).isEqualTo(31L);
        });
    }

    @Test
    void valueRoundTripsThroughParse() {
        String composed = OpenBankingAccountId.compose(OpenBankingAccountId.Type.SAVINGS, 7L);

        assertThat(OpenBankingAccountId.parse(composed).orElseThrow().value()).isEqualTo(composed);
    }

    @Test
    void composeUsesTypePrefixAndFineractId() {
        assertThat(OpenBankingAccountId.compose(OpenBankingAccountId.Type.SAVINGS, 21L))
                .startsWith(OpenBankingAccountId.Type.SAVINGS.getPrefix())
                .endsWith("21");
        assertThat(OpenBankingAccountId.compose(OpenBankingAccountId.Type.LOAN, 31L))
                .startsWith(OpenBankingAccountId.Type.LOAN.getPrefix())
                .endsWith("31");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "savings",
            "-9",
            "checking-9",
            "savings-",
            "savings-abc",
            "savings-0",
            "savings--1",
            "savings-1x",
            "SAVINGS-1"
    })
    void malformedValuesParseToEmpty(String value) {
        assertThat(OpenBankingAccountId.parse(value)).isEmpty();
    }
}
