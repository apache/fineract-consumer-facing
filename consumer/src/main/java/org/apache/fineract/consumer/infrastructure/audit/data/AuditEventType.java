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

package org.apache.fineract.consumer.infrastructure.audit.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditEventType {
    LOGIN_SUCCESS(AuditSeverity.INFO, false),
    LOGIN_FAILURE(AuditSeverity.WARN, false),
    OTP_CHALLENGE_ISSUED(AuditSeverity.INFO, false),
    OTP_VALIDATION_FAILED(AuditSeverity.WARN, false),
    OTP_ATTEMPTS_EXCEEDED(AuditSeverity.CRITICAL, false),
    DEVICE_FINGERPRINT_MISMATCH(AuditSeverity.CRITICAL, false),
    REFRESH_TOKEN_REJECTED(AuditSeverity.WARN, false),
    ACCESS_DENIED(AuditSeverity.WARN, false),
    PASSWORD_CHANGE_INITIATED(AuditSeverity.INFO, false),
    PASSWORD_CHANGE_CONFIRMED(AuditSeverity.INFO, false),
    SENSITIVE_VIEW(AuditSeverity.INFO, true),
    SENSITIVE_ACTION(AuditSeverity.INFO, true),
    CLIENT_ERROR(AuditSeverity.WARN, true),
    API_FAILURE(AuditSeverity.WARN, true),
    NAVIGATION(AuditSeverity.INFO, true),
    LOGOUT(AuditSeverity.INFO, true),
    CONSENT_CREATED(AuditSeverity.INFO, false),
    CONSENT_GRANTED(AuditSeverity.INFO, false),
    CONSENT_DENIED(AuditSeverity.WARN, false),
    CONSENT_REVOKED(AuditSeverity.INFO, false);

    private final AuditSeverity severity;
    private final boolean clientSubmittable;
}
