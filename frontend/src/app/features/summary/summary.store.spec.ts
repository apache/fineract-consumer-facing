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

  it('merges list fields with per-account detail balances', () => {
    store.load();

    controller
      .expectOne('/api/v1/savings')
      .flush([{ id: 1, accountNo: '000001', productName: 'Passbook', currency: 'USD' }]);
    controller
      .expectOne('/api/v1/loans')
      .flush([{ id: 5, accountNo: 'L-5', productName: 'Personal', currency: 'USD' }]);

    controller.expectOne('/api/v1/savings/1').flush({ balance: 100, availableBalance: 90 });
    controller.expectOne('/api/v1/loans/5').flush({ totalOutstanding: 250 });

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

  it('skips detail fetches when an account list is empty', () => {
    store.load();

    controller.expectOne('/api/v1/savings').flush([]);
    controller.expectOne('/api/v1/loans').flush([]);

    expect(store.savingsCards()).toEqual([]);
    expect(store.loanCards()).toEqual([]);
    expect(store.loading()).toBe(false);
  });
});
