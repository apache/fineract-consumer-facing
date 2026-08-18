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
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Configuration } from '@bff/client';
import { TransfersStore } from './transfers.store';

describe('TransfersStore', () => {
  let store: TransfersStore;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Configuration, useValue: new Configuration({ basePath: '' }) },
      ],
    });
    store = TestBed.inject(TransfersStore);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('initiate stores the challenge', () => {
    store
      .initiate({ fromAccountId: 1, toAccountId: 2, toAccountType: 'SAVINGS', amount: 50 })
      .subscribe();
    controller
      .expectOne('/api/v1/transfers/initiate')
      .flush({ stepUpToken: 'tok', sentTo: 'a***@example.com' });

    expect(store.challenge()?.stepUpToken).toBe('tok');
  });

  it('confirm stores the result and clears the challenge', () => {
    store.challenge.set({ stepUpToken: 'tok' });

    store
      .confirm('key-123', {
        stepUpToken: 'tok',
        otp: 'ABC123',
        fromAccountId: 1,
        toAccountId: 2,
        toAccountType: 'SAVINGS',
        amount: 50,
      })
      .subscribe();
    const req = controller.expectOne('/api/v1/transfers/confirm');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-123');
    req.flush({ transferId: 9, fromAccountId: 1, toAccountId: 2, amount: 50 });

    expect(store.result()?.transferId).toBe(9);
    expect(store.challenge()).toBeNull();
  });

  it('loadHistory forwards paging as query params and stores the rows and totals', () => {
    store.loadHistory(1, 10);

    const req = controller.expectOne((r) => r.url === '/api/v1/transfers');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({
      content: [
        {
          transferId: 9,
          date: '2026-08-01',
          amount: 50,
          currency: 'USD',
          fromAccount: '000000001',
          toAccount: '000000002',
          direction: 'OUTGOING',
        },
      ],
      page: 1,
      size: 10,
      totalElements: 11,
      totalPages: 2,
    });

    expect(store.history().length).toBe(1);
    expect(store.historyPage()).toBe(1);
    expect(store.historySize()).toBe(10);
    expect(store.historyTotalElements()).toBe(11);
    expect(store.historyTotalPages()).toBe(2);
    expect(store.historyLoading()).toBe(false);
  });

  it('loadHistory reports the last page when the total is an exact multiple of the size', () => {
    store.loadHistory(1, 10);

    const req = controller.expectOne((r) => r.url === '/api/v1/transfers');
    req.flush({
      content: Array.from({ length: 10 }, (_, i) => ({ transferId: i, amount: 50 })),
      page: 1,
      size: 10,
      totalElements: 20,
      totalPages: 2,
    });

    expect(store.history().length).toBe(10);
    expect(store.historyPage()).toBe(store.historyTotalPages() - 1);
  });

  it('loadHistory reports zero total pages when there are no transfers', () => {
    store.loadHistory(0, 10);

    controller
      .expectOne((r) => r.url === '/api/v1/transfers')
      .flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });

    expect(store.history()).toEqual([]);
    expect(store.historyTotalPages()).toBe(0);
  });
});
