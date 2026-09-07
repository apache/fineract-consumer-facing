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

package org.apache.fineract.consumer.infrastructure.web.data;

import java.util.regex.Pattern;

public final class CorrelationConstants {

    private CorrelationConstants() {
    }

    /** SLF4J MDC key used for request correlation. */
    public static final String MDC_KEY = "correlationId";

    /**
     * Maximum accepted length for an inbound correlation identifier. Oversized
     * values are discarded and replaced with a generated id.
     */
    public static final int MAX_LENGTH = 128;

    /**
     * Allowed characters for inbound correlation identifiers: URL-safe token
     * characters commonly used by UUIDs and opaque request ids.
     */
    public static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9._-]{1," + MAX_LENGTH + "}$");
}
