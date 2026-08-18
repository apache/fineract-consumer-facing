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
import { FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { LoanAccountQueryData, SavingsAccountListItemQueryData } from '@bff/client';
import { NotificationService } from '../../core/notifications/notification.service';
import { SavingsStore } from '../savings/savings.store';
import { TransfersStore } from '../transfers/transfers.store';
import { LoanRepayComponent } from './loan-repay.component';
import { LoansStore } from './loans.store';

const LOAN_ID = 42;
const OUTSTANDING = 500;
const FROM_ACCOUNT_ID = 7;
const SELECT_ACCOUNT_KEY = 'loans.repay.error.selectAccount';
const AMOUNT_KEY = 'loans.repay.error.amountRequired';
const EXCEEDS_KEY = 'loans.repay.error.exceedsOutstanding';
const NOT_ACTIVE_KEY = 'loans.repay.error.notActive';

const activeLoan: LoanAccountQueryData = {
  id: LOAN_ID,
  accountNo: '000000042',
  productName: 'Personal Loan',
  currency: 'USD',
  totalOutstanding: OUTSTANDING,
  active: true,
};

const closedLoan: LoanAccountQueryData = { ...activeLoan, active: false, status: 'Closed' };

const savingsAccount: SavingsAccountListItemQueryData = {
  id: FROM_ACCOUNT_ID,
  accountNo: '000000007',
  productName: 'Savings',
};

interface RepayInternals {
  form: FormGroup<{
    fromAccountId: FormControl<number | null>;
    amount: FormControl<number | null>;
  }>;
  initiate(): void;
}

const showError = vi.fn();
const transfersInitiate = vi.fn();
const loadLoan = vi.fn();
const loadAccounts = vi.fn();

const selected = signal<LoanAccountQueryData | null>(null);

function createComponent(): ComponentFixture<LoanRepayComponent> {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideIonicAngular({ mode: 'md' }),
      provideTranslateService(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ loanId: String(LOAN_ID) }) } },
      },
      { provide: NotificationService, useValue: { showError } },
      { provide: LoansStore, useValue: { selected, loadLoan, loadTransactions: vi.fn() } },
      { provide: SavingsStore, useValue: { accounts: signal([savingsAccount]), loadAccounts } },
      {
        provide: TransfersStore,
        useValue: {
          initiate: transfersInitiate,
          confirm: vi.fn(),
          challenge: signal(null),
          result: signal(null),
        },
      },
    ],
  });
  return TestBed.createComponent(LoanRepayComponent);
}

describe('LoanRepayComponent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    selected.set(null);
  });

  describe('submit guards', () => {
    it('toasts and refuses to dispatch when no source account is chosen', () => {
      const fixture = createComponent();
      selected.set(activeLoan);
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as RepayInternals;
      component.form.controls.amount.setValue(OUTSTANDING);
      component.initiate();

      expect(showError).toHaveBeenCalledWith(SELECT_ACCOUNT_KEY);
      expect(transfersInitiate).not.toHaveBeenCalled();
    });

    it.each([null, 0, -5])('toasts and refuses to dispatch for amount %s', (amount) => {
      const fixture = createComponent();
      selected.set(activeLoan);
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as RepayInternals;
      component.form.controls.fromAccountId.setValue(FROM_ACCOUNT_ID);
      component.form.controls.amount.setValue(amount);
      component.initiate();

      expect(showError).toHaveBeenCalledWith(AMOUNT_KEY);
      expect(transfersInitiate).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch for an amount over the outstanding balance', () => {
      const fixture = createComponent();
      selected.set(activeLoan);
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as RepayInternals;
      component.form.controls.fromAccountId.setValue(FROM_ACCOUNT_ID);
      component.form.controls.amount.setValue(OUTSTANDING + 0.01);
      component.initiate();

      expect(showError).toHaveBeenCalledWith(EXCEEDS_KEY);
      expect(transfersInitiate).not.toHaveBeenCalled();
    });

    it('reads the ceiling live, so a refreshed smaller balance is enforced immediately', () => {
      const fixture = createComponent();
      selected.set(activeLoan);
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as RepayInternals;
      component.form.controls.fromAccountId.setValue(FROM_ACCOUNT_ID);
      component.form.controls.amount.setValue(OUTSTANDING);

      selected.set({ ...activeLoan, totalOutstanding: OUTSTANDING - 1 });
      component.initiate();

      expect(showError).toHaveBeenCalledWith(EXCEEDS_KEY);
      expect(transfersInitiate).not.toHaveBeenCalled();
    });

    it('dispatches the transfer when the amount is within the outstanding balance', () => {
      transfersInitiate.mockReturnValue(of({ stepUpToken: 'step-up', sentTo: 'demo@example.com' }));
      const fixture = createComponent();
      selected.set(activeLoan);
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as RepayInternals;
      component.form.controls.fromAccountId.setValue(FROM_ACCOUNT_ID);
      component.form.controls.amount.setValue(OUTSTANDING);
      component.initiate();

      expect(showError).not.toHaveBeenCalled();
      expect(transfersInitiate).toHaveBeenCalledWith({
        fromAccountId: FROM_ACCOUNT_ID,
        toAccountId: LOAN_ID,
        toAccountType: 'LOAN',
        amount: OUTSTANDING,
      });
    });
  });

  describe('non-active loan bounce', () => {
    it('toasts and redirects to the loan detail page', () => {
      const fixture = createComponent();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

      selected.set(closedLoan);
      fixture.detectChanges();

      expect(showError).toHaveBeenCalledWith(NOT_ACTIVE_KEY);
      expect(navigate).toHaveBeenCalledWith(['/loans', LOAN_ID]);
    });

    it('renders no repayment form for a non-active loan', () => {
      const fixture = createComponent();
      vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

      selected.set(closedLoan);
      fixture.detectChanges();

      expect((fixture.nativeElement as HTMLElement).querySelector('form')).toBeNull();
    });

    it('bounces only once even if the loan signal emits again', () => {
      const fixture = createComponent();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

      selected.set(closedLoan);
      fixture.detectChanges();
      selected.set({ ...closedLoan, status: 'Overpaid' });
      fixture.detectChanges();

      expect(showError).toHaveBeenCalledTimes(1);
      expect(navigate).toHaveBeenCalledTimes(1);
    });

    it('does not bounce an active loan', () => {
      const fixture = createComponent();
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

      selected.set(activeLoan);
      fixture.detectChanges();

      expect(showError).not.toHaveBeenCalled();
      expect(navigate).not.toHaveBeenCalled();
      expect((fixture.nativeElement as HTMLElement).querySelector('form')).not.toBeNull();
    });
  });
});
