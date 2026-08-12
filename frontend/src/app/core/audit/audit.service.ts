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

import { Injectable } from '@angular/core';
import { AuditEventCommandRequest, SubmitAuditEventsCommandRequest } from '@bff/client';
import { deviceFingerprint } from '../auth/device-fingerprint';

export const AUDIT_EVENTS_PATH = '/api/v1/audit/events';

export type ClientAuditEventType =
  | 'SENSITIVE_VIEW'
  | 'SENSITIVE_ACTION'
  | 'CLIENT_ERROR'
  | 'API_FAILURE'
  | 'NAVIGATION'
  | 'LOGOUT';

const FLUSH_THRESHOLD = 20;
const FLUSH_INTERVAL_MS = 30_000;
const MAX_BATCH_SIZE = 50;
const MAX_BUFFER_SIZE = 50;
const MAX_CONSECUTIVE_FAILURES = 3;

@Injectable({ providedIn: 'root' })
export class AuditService {

  private readonly buffer: AuditEventCommandRequest[] = [];
  private consecutiveFailures = 0;
  private flushing = false;

  constructor() {
    setInterval(() => this.flush(), FLUSH_INTERVAL_MS);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') {
        this.flush();
      }
    });
  }

  record(eventType: ClientAuditEventType, details?: Record<string, string>): void {
    try {
      if (this.buffer.length >= MAX_BUFFER_SIZE) {
        this.buffer.shift();
      }
      this.buffer.push({
        eventUuid: crypto.randomUUID(),
        eventType,
        occurredAt: new Date().toISOString(),
        details,
      });
      if (eventType === 'LOGOUT' || this.buffer.length >= FLUSH_THRESHOLD) {
        this.flush();
      }
    } catch {
    }
  }

  private flush(): void {
    if (this.flushing || this.buffer.length === 0) {
      return;
    }
    this.flushing = true;
    const batch = this.buffer.splice(0, MAX_BATCH_SIZE);
    const body: SubmitAuditEventsCommandRequest = { events: batch };
    fetch(AUDIT_EVENTS_PATH, {
      method: 'POST',
      keepalive: true,
      headers: {
        'Content-Type': 'application/json',
        'X-Device-Fingerprint': deviceFingerprint(),
      },
      body: JSON.stringify(body),
    })
      .then((response) => {
        if (response.ok) {
          this.consecutiveFailures = 0;
        } else {
          this.requeue(batch);
        }
      })
      .catch(() => this.requeue(batch))
      .finally(() => {
        this.flushing = false;
      });
  }

  private requeue(batch: AuditEventCommandRequest[]): void {
    this.consecutiveFailures++;
    if (this.consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
      this.buffer.length = 0;
      this.consecutiveFailures = 0;
      return;
    }
    this.buffer.unshift(...batch);
  }
}

