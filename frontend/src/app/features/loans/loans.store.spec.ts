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
      .expectOne('/api/v1/loans/schedule-preview')
      .flush({ totalRepaymentExpected: 1024, periods: [{ period: 1 }] });

    expect(store.schedulePreview()?.totalRepaymentExpected).toBe(1024);
  });

  it('submit stores the returned draft', () => {
    store.submit(previewRequest).subscribe();
    controller.expectOne('/api/v1/loans').flush({ loanId: 42 });

    expect(store.draft()?.loanId).toBe(42);
  });

  it('withdraw sends command=withdraw and clears the draft', () => {
    store.draft.set({ loanId: 42 });

    store.withdraw(42, { withdrawnOnDate: '2026-06-25' }).subscribe();
    const req = controller.expectOne(r => r.url === '/api/v1/loans/42');
    expect(req.request.params.get('command')).toBe('withdraw');
    req.flush({ loanId: 42 });

    expect(store.draft()).toBeNull();
  });

  it('initiateChargePayment stores the challenge and confirmChargePayment clears it', () => {
    store.initiateChargePayment(7, 3).subscribe();
    controller
      .expectOne('/api/v1/loans/7/charges/3/pay')
      .flush({ stepUpToken: 'tok', sentTo: 'a***@example.com' });
    expect(store.chargePaymentChallenge()?.stepUpToken).toBe('tok');

    store.confirmChargePayment(7, 3, { stepUpToken: 'tok', otp: '123456' }).subscribe();
    controller.expectOne('/api/v1/loans/7/charges/3/pay/confirm').flush({ loanId: 7 });
    expect(store.chargePaymentChallenge()).toBeNull();
  });
});
