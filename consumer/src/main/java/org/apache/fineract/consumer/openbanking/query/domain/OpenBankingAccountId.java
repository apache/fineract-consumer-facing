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

import java.util.Optional;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode
@ToString
public final class OpenBankingAccountId {

    private static final String SEPARATOR = "-";

    @Getter
    @RequiredArgsConstructor
    public enum Type {
        SAVINGS("savings"),
        LOAN("loan");

        private final String prefix;
    }

    private final Type type;
    private final Long fineractId;

    public static Optional<OpenBankingAccountId> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        int separatorIndex = value.indexOf(SEPARATOR);
        if (separatorIndex <= 0) {
            return Optional.empty();
        }
        String prefix = value.substring(0, separatorIndex);
        Optional<Type> type = Stream.of(Type.values())
                .filter(candidate -> candidate.prefix.equals(prefix))
                .findFirst();
        if (type.isEmpty()) {
            return Optional.empty();
        }
        try {
            long fineractId = Long.parseLong(value.substring(separatorIndex + 1));
            return fineractId > 0
                    ? Optional.of(new OpenBankingAccountId(type.get(), fineractId))
                    : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static String compose(Type type, Long fineractId) {
        return type.prefix + SEPARATOR + fineractId;
    }

    public String value() {
        return compose(type, fineractId);
    }
}
