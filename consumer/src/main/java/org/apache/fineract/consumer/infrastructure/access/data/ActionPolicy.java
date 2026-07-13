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

package org.apache.fineract.consumer.infrastructure.access.data;

import java.util.function.Supplier;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.apache.fineract.consumer.infrastructure.exception.AbstractConsumerException;

@Getter
@RequiredArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
public final class ActionPolicy {

    private final ConsumerAction action;
    private final String requiredScope;
    private final boolean requiresKycVerified;
    private final ResourceType ownership;
    private final Supplier<AbstractConsumerException> denialException;
}
