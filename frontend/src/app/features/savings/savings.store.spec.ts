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

const SAVINGS_URL = '/api/v1/savings';
const SAVINGS_ID = 7;
const SAVINGS_TRANSACTIONS_URL = `${SAVINGS_URL}/${SAVINGS_ID}/transactions`;

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
    controller.expectOne(SAVINGS_URL).flush(rows);

    expect(store.accounts()).toEqual(rows);
  });

  it('loadAccounts sorts the accounts by account number', () => {
    store.loadAccounts();
    controller
      .expectOne(SAVINGS_URL)
      .flush([
        { id: 2, accountNo: '000002' },
        { id: 1, accountNo: '000001' },
      ]);

    expect(store.accounts().map((row) => row.accountNo)).toEqual(['000001', '000002']);
  });

  it('loadTransactions forwards the date-range filter and unwraps the page envelope', () => {
    store.loadTransactions(SAVINGS_ID, {
      fromDate: '2026-01-01',
      toDate: '2026-02-01',
      page: 1,
      size: 10,
    });

    const req = controller.expectOne((r) => r.url === SAVINGS_TRANSACTIONS_URL);
    expect(req.request.params.get('fromDate')).toBe('2026-01-01');
    expect(req.request.params.get('toDate')).toBe('2026-02-01');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({
      content: [{ id: 11, amount: 100 }],
      page: 1,
      size: 10,
      totalElements: 25,
      totalPages: 3,
    });

    expect(store.transactions()).toEqual([{ id: 11, amount: 100 }]);
    expect(store.transactionsPage()).toBe(1);
    expect(store.transactionsSize()).toBe(10);
    expect(store.transactionsTotalElements()).toBe(25);
    expect(store.transactionsTotalPages()).toBe(3);
  });
});
