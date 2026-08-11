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
import { SummaryStore } from './summary.store';

const ACCOUNTS_SUMMARY_URL = '/api/v1/summary/accounts';

describe('SummaryStore', () => {
  let store: SummaryStore;
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
    store = TestBed.inject(SummaryStore);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('maps the single accounts-summary response into savings and loan cards', () => {
    store.load();

    controller.expectOne(ACCOUNTS_SUMMARY_URL).flush({
      savings: [
        {
          id: 1,
          accountNo: '000001',
          productName: 'Passbook',
          status: 'savingsAccountStatusType.active',
          currency: 'USD',
          accountBalance: 100,
          availableBalance: 90,
        },
      ],
      loans: [
        {
          id: 5,
          accountNo: 'L-5',
          productName: 'Personal',
          status: 'loanStatusType.active',
          currency: 'USD',
          loanBalance: 250,
        },
      ],
    });

    expect(store.savingsCards()).toEqual([
      {
        id: 1,
        accountNo: '000001',
        productName: 'Passbook',
        status: 'savingsAccountStatusType.active',
        currency: 'USD',
        balance: 100,
      },
    ]);
    expect(store.loanCards()).toEqual([
      {
        id: 5,
        accountNo: 'L-5',
        productName: 'Personal',
        status: 'loanStatusType.active',
        currency: 'USD',
        totalOutstanding: 250,
      },
    ]);
    expect(store.loading()).toBe(false);
  });

  it('sorts by account number, slices the top three, and counts all accounts', () => {
    store.load();

    controller.expectOne(ACCOUNTS_SUMMARY_URL).flush({
      savings: [
        { id: 3, accountNo: '000003', productName: 'C', currency: 'USD', accountBalance: 3 },
        { id: 9, productName: 'No number', currency: 'USD', accountBalance: 9 },
        { id: 1, accountNo: '000001', productName: 'A', currency: 'USD', accountBalance: 1 },
        { id: 4, accountNo: '000004', productName: 'D', currency: 'USD', accountBalance: 4 },
        { id: 2, accountNo: '000002', productName: 'B', currency: 'USD', accountBalance: 2 },
      ],
      loans: [
        { id: 12, accountNo: 'L-2', productName: 'Personal', currency: 'USD', loanBalance: 20 },
        { id: 14, accountNo: 'L-4', productName: 'Auto', currency: 'USD', loanBalance: 40 },
        { id: 11, accountNo: 'L-1', productName: 'Home', currency: 'USD', loanBalance: 10 },
        { id: 13, accountNo: 'L-3', productName: 'Gold', currency: 'USD', loanBalance: 30 },
      ],
    });

    expect(store.savingsCards().map((card) => card.accountNo)).toEqual([
      '000001',
      '000002',
      '000003',
      '000004',
      undefined,
    ]);
    expect(store.topSavings().map((card) => card.id)).toEqual([1, 2, 3]);
    expect(store.savingsCount()).toBe(5);

    expect(store.loanCards().map((card) => card.accountNo)).toEqual(['L-1', 'L-2', 'L-3', 'L-4']);
    expect(store.topLoans().map((card) => card.id)).toEqual([11, 12, 13]);
    expect(store.loansCount()).toBe(4);
  });

  it('renders null balances as zero', () => {
    store.load();

    controller.expectOne(ACCOUNTS_SUMMARY_URL).flush({
      savings: [{ id: 2, accountNo: '000002', productName: 'Passbook', currency: 'USD' }],
      loans: [{ id: 7, accountNo: 'L-7', productName: 'Personal', currency: 'USD' }],
    });

    expect(store.savingsCards()).toEqual([
      {
        id: 2,
        accountNo: '000002',
        productName: 'Passbook',
        currency: 'USD',
        balance: 0,
      },
    ]);
    expect(store.loanCards()).toEqual([
      { id: 7, accountNo: 'L-7', productName: 'Personal', currency: 'USD', totalOutstanding: 0 },
    ]);
    expect(store.loading()).toBe(false);
  });

  it('handles an empty summary', () => {
    store.load();

    controller.expectOne(ACCOUNTS_SUMMARY_URL).flush({});

    expect(store.savingsCards()).toEqual([]);
    expect(store.loanCards()).toEqual([]);
    expect(store.loading()).toBe(false);
  });
});
