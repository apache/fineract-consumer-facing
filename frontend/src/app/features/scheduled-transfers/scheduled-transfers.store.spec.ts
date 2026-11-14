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

import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { SCHEDULED_TRANSFERS_DISABLED_CODE, ScheduledTransferQueryData } from '../../core/adapters';
import { AuditService } from '../../core/audit/audit.service';
import { QUIET_ERROR_CODES } from '../../core/interceptors/quiet-error-codes';
import { provideBffApiTesting } from '../../testing/bff-api-testing';
import { ScheduledTransfersStore } from './scheduled-transfers.store';

const BASE = '/api/v1/scheduled-transfers';
const PUBLIC_ID = '7b0c6f1e-2f3a-4c5d-8e9f-0a1b2c3d4e5f';

const monthly: ScheduledTransferQueryData = {
  publicId: PUBLIC_ID,
  fromAccountId: 1,
  toAccountId: 2,
  amount: 50,
  frequency: 'MONTHLY',
  startDate: '2026-12-01',
};

const request = {
  fromAccountId: 1,
  toAccountId: 2,
  amount: 50,
  frequency: 'MONTHLY' as const,
  startDate: '2026-12-01',
};

describe('ScheduledTransfersStore', () => {
  const record = vi.fn();
  let store: ScheduledTransfersStore;
  let controller: HttpTestingController;

  beforeEach(() => {
    record.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        ...provideBffApiTesting(),
        { provide: AuditService, useValue: { record } },
      ],
    });
    store = TestBed.inject(ScheduledTransfersStore);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  describe('load', () => {
    it('stores the active transfers', () => {
      store.load().subscribe();
      controller.expectOne(BASE).flush([monthly]);

      expect(store.transfers()).toEqual([monthly]);
      expect(store.loaded()).toBe(true);
      expect(store.disabled()).toBe(false);
    });

    it('asks the error interceptor not to toast the "turned off" answer', () => {
      store.load().subscribe();
      const req = controller.expectOne(BASE);

      expect(req.request.context.get(QUIET_ERROR_CODES)).toEqual([
        SCHEDULED_TRANSFERS_DISABLED_CODE,
      ]);
      req.flush([]);
    });

    it('treats the "turned off" answer as a state, not an error', () => {
      let rows: unknown;
      let failed = false;
      store.load().subscribe({ next: (value) => (rows = value), error: () => (failed = true) });

      controller
        .expectOne(BASE)
        .flush(
          { code: SCHEDULED_TRANSFERS_DISABLED_CODE, defaultMessage: 'not enabled' },
          { status: 404, statusText: 'Not Found' },
        );

      expect(failed).toBe(false);
      expect(rows).toEqual([]);
      expect(store.disabled()).toBe(true);
      expect(store.loaded()).toBe(true);
    });

    it('rethrows any other failure', () => {
      let received: unknown;
      store.load().subscribe({ error: (error: unknown) => (received = error) });

      controller.expectOne(BASE).flush(
        {
          code: 'error.msg.consumer.scheduledtransfers.query.upstream.unavailable',
          defaultMessage: 'down',
        },
        { status: 502, statusText: 'Bad Gateway' },
      );

      expect(received).toBeInstanceOf(HttpErrorResponse);
      expect(store.disabled()).toBe(false);
      expect(store.loaded()).toBe(false);
    });
  });

  it('initiate records the sensitive action and stores the challenge', () => {
    store.initiate(request).subscribe();
    const req = controller.expectOne(`${BASE}/initiate`);
    expect(req.request.body).toEqual(request);
    expect(req.request.headers.has('X-Device-Fingerprint')).toBe(true);
    req.flush({ stepUpToken: 'tok', sentTo: 'a***@example.com' });

    expect(record).toHaveBeenCalledWith('SENSITIVE_ACTION', {
      action: 'SCHEDULED_TRANSFER_CREATE',
    });
    expect(store.challenge()?.stepUpToken).toBe('tok');
  });

  it('confirm sends the idempotency key, stores the result and clears the challenge', () => {
    store.challenge.set({ stepUpToken: 'tok' });

    store.confirm('key-123', { stepUpToken: 'tok', otp: 'ABC123', ...request }).subscribe();
    const req = controller.expectOne(`${BASE}/confirm`);
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-123');
    req.flush({ publicId: PUBLIC_ID });

    expect(store.result()?.publicId).toBe(PUBLIC_ID);
    expect(store.challenge()).toBeNull();
  });

  it('initiateCancel records the sensitive action and stores the challenge', () => {
    store.initiateCancel(PUBLIC_ID).subscribe();
    controller
      .expectOne(`${BASE}/${PUBLIC_ID}/cancel/initiate`)
      .flush({ stepUpToken: 'cancel-tok', sentTo: 'a***@example.com' });

    expect(record).toHaveBeenCalledWith('SENSITIVE_ACTION', {
      action: 'SCHEDULED_TRANSFER_CANCEL',
    });
    expect(store.challenge()?.stepUpToken).toBe('cancel-tok');
  });

  it('confirmCancel sends the idempotency key and drops the transfer from the list', () => {
    store.transfers.set([monthly, { ...monthly, publicId: 'other' }]);
    store.challenge.set({ stepUpToken: 'cancel-tok' });

    store
      .confirmCancel(PUBLIC_ID, 'key-9', { stepUpToken: 'cancel-tok', otp: 'ABC123' })
      .subscribe();
    const req = controller.expectOne(`${BASE}/${PUBLIC_ID}/cancel/confirm`);
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-9');
    expect(req.request.body).toEqual({ stepUpToken: 'cancel-tok', otp: 'ABC123' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(store.transfers().map((row) => row.publicId)).toEqual(['other']);
    expect(store.challenge()).toBeNull();
  });

  it('loadRuns forwards paging and stores the runs and totals', () => {
    store.loadRuns(PUBLIC_ID, 1, 10);

    const req = controller.expectOne((r) => r.url === `${BASE}/${PUBLIC_ID}/runs`);
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({
      content: [
        { date: '2026-12-01', status: 'SUCCEEDED', amount: 50 },
        { date: '2027-01-01', status: 'FAILED' },
      ],
      page: 1,
      size: 10,
      totalElements: 12,
      totalPages: 2,
    });

    expect(store.runs().map((run) => run.status)).toEqual(['SUCCEEDED', 'FAILED']);
    expect(store.runsPage()).toBe(1);
    expect(store.runsSize()).toBe(10);
    expect(store.runsTotalElements()).toBe(12);
    expect(store.runsTotalPages()).toBe(2);
    expect(store.runsLoading()).toBe(false);
  });
});
