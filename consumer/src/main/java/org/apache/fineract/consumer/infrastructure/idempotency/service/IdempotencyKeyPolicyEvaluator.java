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

package org.apache.fineract.consumer.infrastructure.idempotency.service;

import java.util.regex.Pattern;
import org.apache.fineract.consumer.infrastructure.idempotency.exception.IdempotencyKeyMalformedException;

public final class IdempotencyKeyPolicyEvaluator {

    public static final int MIN_LENGTH = 1;
    public static final int MAX_LENGTH = 64;
    public static final String VISIBLE_ASCII_PATTERN = "^[\\x21-\\x7E]{" + MIN_LENGTH + "," + MAX_LENGTH + "}$";

    private static final Pattern KEY_PATTERN = Pattern.compile(VISIBLE_ASCII_PATTERN);

    private IdempotencyKeyPolicyEvaluator() {
    }

    public static void validate(String rawKey) {
        if (rawKey == null || !KEY_PATTERN.matcher(rawKey).matches()) {
            throw new IdempotencyKeyMalformedException();
        }
    }
}
