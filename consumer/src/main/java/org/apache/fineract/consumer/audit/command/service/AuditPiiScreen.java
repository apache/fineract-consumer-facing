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
package org.apache.fineract.consumer.audit.command.service;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AuditPiiScreen {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern SSN_LIKE_PATTERN = Pattern.compile("(?<!\\d)\\d{9}(?!\\d)");
    private static final Pattern AADHAAR_LIKE_PATTERN = Pattern.compile("(?<!\\d)\\d{12}(?!\\d)");
    private static final Pattern CARD_LIKE_PATTERN = Pattern.compile("(?<!\\d)\\d{13,19}(?!\\d)");

    private static final Pattern FORBIDDEN_KEY_PATTERN =
            Pattern.compile("(?i)\"(password|otp|ssn|token)\"\\s*:");

    private static final List<Pattern> PATTERNS = List.of(
            EMAIL_PATTERN, SSN_LIKE_PATTERN, AADHAAR_LIKE_PATTERN, CARD_LIKE_PATTERN, FORBIDDEN_KEY_PATTERN);

    public boolean containsPii(String serializedDetails) {
        if (serializedDetails == null || serializedDetails.isEmpty()) {
            return false;
        }
        return PATTERNS.stream().anyMatch(pattern -> pattern.matcher(serializedDetails).find());
    }
}
