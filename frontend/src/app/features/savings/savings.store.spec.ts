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
import { SavingsStore } from './savings.store';

describe('SavingsStore', () => {
  let store: SavingsStore;
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
    store = TestBed.inject(SavingsStore);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('loadAccounts populates the accounts signal', () => {
    const rows = [{ id: 1, accountNo: '000001', productName: 'Passbook' }];

    store.loadAccounts();
    controller.expectOne('/api/v1/savings').flush(rows);

    expect(store.accounts()).toEqual(rows);
  });

  it('loadTransactions forwards the date-range filter as query params', () => {
    store.loadTransactions(7, { fromDate: '2026-01-01', toDate: '2026-02-01', limit: 10, offset: 5 });

    const req = controller.expectOne(r => r.url === '/api/v1/savings/7/transactions');
    expect(req.request.params.get('fromDate')).toBe('2026-01-01');
    expect(req.request.params.get('toDate')).toBe('2026-02-01');
    expect(req.request.params.get('limit')).toBe('10');
    expect(req.request.params.get('offset')).toBe('5');
    req.flush([]);
  });

  it('initiateChargePayment stores the challenge and confirmChargePayment clears it', () => {
    store.initiateChargePayment(7, 3, { amount: 10 }).subscribe();
    controller
      .expectOne('/api/v1/savings/7/charges/3/pay')
      .flush({ stepUpToken: 'tok', sentTo: 'a***@example.com' });
    expect(store.chargePaymentChallenge()?.stepUpToken).toBe('tok');

    store.confirmChargePayment(7, 3, { stepUpToken: 'tok', otp: '123456', amount: 10 }).subscribe();
    controller.expectOne('/api/v1/savings/7/charges/3/pay/confirm').flush({ savingsId: 7 });
    expect(store.chargePaymentChallenge()).toBeNull();
  });
});
