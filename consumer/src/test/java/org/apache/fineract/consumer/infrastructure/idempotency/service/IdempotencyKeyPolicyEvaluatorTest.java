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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.fineract.consumer.infrastructure.idempotency.exception.IdempotencyKeyMalformedException;
import org.junit.jupiter.api.Test;

class IdempotencyKeyPolicyEvaluatorTest {

    private static void assertRejected(String rawKey) {
        assertThatThrownBy(() -> IdempotencyKeyPolicyEvaluator.validate(rawKey))
                .isInstanceOf(IdempotencyKeyMalformedException.class);
    }

    @Test
    void acceptsSingleCharacterKey() {
        assertThatCode(() -> IdempotencyKeyPolicyEvaluator.validate("k")).doesNotThrowAnyException();
    }

    @Test
    void acceptsMaxLengthKey() {
        assertThatCode(() -> IdempotencyKeyPolicyEvaluator.validate("a".repeat(IdempotencyKeyPolicyEvaluator.MAX_LENGTH)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsUuidShapedKey() {
        assertThatCode(() -> IdempotencyKeyPolicyEvaluator.validate("3f2c8a1e-0000-4000-8000-000000000001"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNullKey() {
        assertRejected(null);
    }

    @Test
    void rejectsEmptyKey() {
        assertRejected("");
    }

    @Test
    void rejectsKeyOverMaxLength() {
        assertRejected("a".repeat(IdempotencyKeyPolicyEvaluator.MAX_LENGTH + 1));
    }

    @Test
    void rejectsKeyContainingSpace() {
        assertRejected("has space");
    }

    @Test
    void rejectsKeyContainingControlCharacter() {
        assertRejected("has\tcontrol");
    }

    @Test
    void rejectsKeyContainingNonAsciiCharacter() {
        assertRejected("clé-non-ascii");
    }
}
