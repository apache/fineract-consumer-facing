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

  it('previewSchedule posts to schedule-preview and sets the schedulePreview signal', () => {
    store.previewSchedule(previewRequest).subscribe();
    controller
      .expectOne(SCHEDULE_PREVIEW_URL)
      .flush({ totalRepaymentExpected: 1024, periods: [{ period: 1 }] });

    expect(store.schedulePreview()?.totalRepaymentExpected).toBe(1024);
  });

  it('submit stores the returned draft', () => {
    store.submit(previewRequest).subscribe();
    controller.expectOne(LOANS_URL).flush({ loanId: LOAN_ID });

    expect(store.draft()?.loanId).toBe(LOAN_ID);
  });

  it('withdraw sends command=withdraw and clears the draft', () => {
    store.draft.set({ loanId: LOAN_ID });

    store.withdraw(LOAN_ID, { withdrawnOnDate: '2026-06-25' }).subscribe();
    const req = controller.expectOne(r => r.url === LOAN_URL);
    expect(req.request.params.get('command')).toBe('withdraw');
    req.flush({ loanId: LOAN_ID });

    expect(store.draft()).toBeNull();
  });
});
