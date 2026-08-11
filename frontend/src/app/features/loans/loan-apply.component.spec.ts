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

import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { NotificationService } from '../../core/notifications/notification.service';
import { LoanApplyComponent } from './loan-apply.component';
import { LoansStore } from './loans.store';

const DRAFT_LOAN_ID = 42;

const validTerms = {
  productId: 1,
  principal: 1000,
  numberOfRepayments: 6,
  repaymentEvery: 1,
  loanTermFrequency: 6,
  interestRatePerPeriod: 0,
  expectedDisbursementDate: '2026-01-01',
  submittedOnDate: '2026-01-01',
};

const invalidFieldCases: readonly [string, number | string, string][] = [
  ['productId', 0, 'loans.apply.error.productRequired'],
  ['principal', 0, 'loans.apply.error.principalRequired'],
  ['numberOfRepayments', 0, 'loans.apply.error.numberOfRepaymentsRequired'],
  ['repaymentEvery', 0, 'loans.apply.error.repaymentEveryRequired'],
  ['loanTermFrequency', 0, 'loans.apply.error.loanTermFrequencyRequired'],
  ['interestRatePerPeriod', -1, 'loans.apply.error.interestRateRequired'],
  ['expectedDisbursementDate', '', 'loans.apply.error.expectedDisbursementDateRequired'],
  ['submittedOnDate', '', 'loans.apply.error.submittedOnDateRequired'],
];

interface ApplyInternals {
  form: FormGroup;
  submit(): void;
  modify(loanId: number | undefined): void;
}

const showError = vi.fn();
const submitApplication = vi.fn();
const modifyApplication = vi.fn();
const loadTemplate = vi.fn();

function createComponent(): ComponentFixture<LoanApplyComponent> {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideIonicAngular({ mode: 'md' }),
      provideTranslateService(),
      provideRouter([]),
      { provide: NotificationService, useValue: { showError } },
      {
        provide: LoansStore,
        useValue: {
          loadTemplate,
          submit: submitApplication,
          modify: modifyApplication,
          previewSchedule: vi.fn(),
          withdraw: vi.fn(),
          template: signal(null),
          schedulePreview: signal(null),
          draft: signal({ loanId: DRAFT_LOAN_ID }),
        },
      },
    ],
  });
  return TestBed.createComponent(LoanApplyComponent);
}

function componentWithValidTerms(): ApplyInternals {
  const fixture = createComponent();
  fixture.detectChanges();
  const component = fixture.componentInstance as unknown as ApplyInternals;
  component.form.patchValue(validTerms);
  return component;
}

describe('LoanApplyComponent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    submitApplication.mockReturnValue(of({ loanId: DRAFT_LOAN_ID }));
    modifyApplication.mockReturnValue(of({ loanId: DRAFT_LOAN_ID }));
  });

  describe('submit guards', () => {
    it.each(invalidFieldCases)(
      'toasts and refuses to dispatch when %s is %s',
      (control, value, key) => {
        const component = componentWithValidTerms();
        component.form.get(control)?.setValue(value);

        component.submit();

        expect(showError).toHaveBeenCalledWith(key);
        expect(submitApplication).not.toHaveBeenCalled();
      },
    );

    it('reports only the first invalid field so the user gets one message at a time', () => {
      const component = componentWithValidTerms();
      component.form.get('principal')?.setValue(0);
      component.form.get('submittedOnDate')?.setValue('');

      component.submit();

      expect(showError).toHaveBeenCalledTimes(1);
      expect(showError).toHaveBeenCalledWith('loans.apply.error.principalRequired');
    });

    it('dispatches the application when every field is valid', () => {
      const component = componentWithValidTerms();

      component.submit();

      expect(showError).not.toHaveBeenCalled();
      expect(submitApplication).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining(validTerms),
      );
    });
  });

  describe('modify guards', () => {
    it('toasts and refuses to dispatch when the draft has no loan id', () => {
      const component = componentWithValidTerms();

      component.modify(undefined);

      expect(showError).toHaveBeenCalledWith('loans.apply.error.draftUnavailable');
      expect(modifyApplication).not.toHaveBeenCalled();
    });

    it('toasts the field reason and refuses to dispatch when the terms are invalid', () => {
      const component = componentWithValidTerms();
      component.form.get('productId')?.setValue(0);

      component.modify(DRAFT_LOAN_ID);

      expect(showError).toHaveBeenCalledWith('loans.apply.error.productRequired');
      expect(modifyApplication).not.toHaveBeenCalled();
    });

    it('dispatches the modification when every field is valid', () => {
      const component = componentWithValidTerms();

      component.modify(DRAFT_LOAN_ID);

      expect(showError).not.toHaveBeenCalled();
      expect(modifyApplication).toHaveBeenCalledWith(
        DRAFT_LOAN_ID,
        expect.any(String),
        expect.objectContaining(validTerms),
      );
    });
  });
});
