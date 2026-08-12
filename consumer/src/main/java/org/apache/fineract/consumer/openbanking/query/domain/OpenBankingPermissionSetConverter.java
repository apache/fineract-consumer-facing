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

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.fineract.consumer.openbanking.query.data.OpenBankingPermission;

@Converter
public class OpenBankingPermissionSetConverter implements AttributeConverter<Set<OpenBankingPermission>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(Set<OpenBankingPermission> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "";
        }
        return EnumSet.copyOf(attribute).stream()
                .map(OpenBankingPermission::getCode)
                .collect(Collectors.joining(SEPARATOR));
    }

    @Override
    public Set<OpenBankingPermission> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return EnumSet.noneOf(OpenBankingPermission.class);
        }
        return Arrays.stream(dbData.split(SEPARATOR))
                .map(OpenBankingPermission::fromCode)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(OpenBankingPermission.class)));
    }
}
