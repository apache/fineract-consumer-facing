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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

const EMAIL_PATTERN = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;
const SSN_LIKE_PATTERN = /(?<!\d)\d{9}(?!\d)/g;
const AADHAAR_LIKE_PATTERN = /(?<!\d)\d{12}(?!\d)/g;
const CARD_LIKE_PATTERN = /(?<!\d)\d{13,19}(?!\d)/g;
const BLOCKED_KEY_PATTERN = /^(password|otp|ssn|token)$/i;

const REDACTED = '[REDACTED]';
const MAX_VALUE_LENGTH = 300;

export function scrubPii(value: string): string {
  return value
    .replace(EMAIL_PATTERN, REDACTED)
    .replace(SSN_LIKE_PATTERN, REDACTED)
    .replace(AADHAAR_LIKE_PATTERN, REDACTED)
    .replace(CARD_LIKE_PATTERN, REDACTED);
}

export function buildDetails(
  entries: Record<string, string | number | undefined>,
): Record<string, string> | undefined {
  const details: Record<string, string> = {};
  for (const [key, value] of Object.entries(entries)) {
    if (value === undefined || value === '' || BLOCKED_KEY_PATTERN.test(key)) {
      continue;
    }
    details[key] = scrubPii(String(value)).slice(0, MAX_VALUE_LENGTH);
  }
  return Object.keys(details).length > 0 ? details : undefined;
}
