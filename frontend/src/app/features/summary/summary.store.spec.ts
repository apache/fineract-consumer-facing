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
          currency: 'USD',
          accountBalance: 100,
          availableBalance: 90,
        },
      ],
      loans: [
        { id: 5, accountNo: 'L-5', productName: 'Personal', currency: 'USD', loanBalance: 250 },
      ],
    });

    expect(store.savingsCards()).toEqual([
      {
        id: 1,
        accountNo: '000001',
        productName: 'Passbook',
        currency: 'USD',
        balance: 100,
        availableBalance: 90,
      },
    ]);
    expect(store.loanCards()).toEqual([
      { id: 5, accountNo: 'L-5', productName: 'Personal', currency: 'USD', totalOutstanding: 250 },
    ]);
    expect(store.loading()).toBe(false);
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
        availableBalance: 0,
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
