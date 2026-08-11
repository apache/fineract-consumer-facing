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
import { Configuration, LoanSchedulePreviewQueryRequest } from '@bff/client';
import { LoansStore } from './loans.store';

const LOANS_URL = '/api/v1/loans';
const SCHEDULE_PREVIEW_URL = `${LOANS_URL}/schedule-preview`;
const LOAN_ID = 42;
const LOAN_URL = `${LOANS_URL}/${LOAN_ID}`;
const OBLIGEES_URL = '/api/v1/user/obligees';
const OBLIGEE_NAME = 'Ravi Patel';

const previewRequest: LoanSchedulePreviewQueryRequest = {
  productId: 1,
  principal: 1000,
  loanTermFrequency: 12,
  loanTermFrequencyType: 2,
  numberOfRepayments: 12,
  repaymentEvery: 1,
  repaymentFrequencyType: 2,
  interestRatePerPeriod: 2,
  amortizationType: 1,
  interestType: 0,
  interestCalculationPeriodType: 1,
  transactionProcessingStrategyCode: 'mifos-standard-strategy',
  expectedDisbursementDate: '2026-07-01',
  submittedOnDate: '2026-06-25',
};

describe('LoansStore', () => {
  let store: LoansStore;
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
    store = TestBed.inject(LoansStore);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('loadLoans sorts the loans by account number', () => {
    store.loadLoans();
    controller.expectOne(LOANS_URL).flush([
      { id: 2, accountNo: '000002' },
      { id: 1, accountNo: '000001' },
    ]);

    expect(store.loans().map((row) => row.accountNo)).toEqual(['000001', '000002']);
  });

  it('loadObligees sets the obligees signal', () => {
    store.loadObligees();
    controller.expectOne(OBLIGEES_URL).flush([
      {
        displayName: OBLIGEE_NAME,
        accountNumber: 'L-42',
        loanAmount: 1000,
        guaranteeAmount: 250,
        amountReleased: 0,
      },
    ]);

    expect(store.obligees().length).toBe(1);
    expect(store.obligees()[0].displayName).toBe(OBLIGEE_NAME);
  });

  it('loadTransactions forwards the filter and unwraps the page envelope', () => {
    store.loadTransactions(LOAN_ID, {
      fromDate: '2026-01-01',
      toDate: '2026-02-01',
      page: 1,
      size: 10,
    });

    const req = controller.expectOne((r) => r.url === `${LOAN_URL}/transactions`);
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

  it('previewSchedule posts to schedule-preview and sets the schedulePreview signal', () => {
    store.previewSchedule(previewRequest).subscribe();
    controller
      .expectOne(SCHEDULE_PREVIEW_URL)
      .flush({ totalRepaymentExpected: 1024, periods: [{ period: 1 }] });

    expect(store.schedulePreview()?.totalRepaymentExpected).toBe(1024);
  });

  it('submit stores the returned draft', () => {
    store.submit('key-submit', previewRequest).subscribe();
    const req = controller.expectOne(LOANS_URL);
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-submit');
    req.flush({ loanId: LOAN_ID });

    expect(store.draft()?.loanId).toBe(LOAN_ID);
  });

  it('withdraw sends command=withdraw and clears the draft', () => {
    store.draft.set({ loanId: LOAN_ID });

    store.withdraw(LOAN_ID, 'key-withdraw', { withdrawnOnDate: '2026-06-25' }).subscribe();
    const req = controller.expectOne((r) => r.url === LOAN_URL);
    expect(req.request.params.get('command')).toBe('withdraw');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-withdraw');
    req.flush({ loanId: LOAN_ID });

    expect(store.draft()).toBeNull();
  });
});
