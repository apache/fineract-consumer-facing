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
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { of, throwError } from 'rxjs';
import {
  BeneficiaryQueryData,
  SavingsAccountListItemQueryData,
  ScheduledTransferChallengeCommandData,
  ScheduledTransferFrequency,
} from '../../core/adapters';
import { NotificationService } from '../../core/notifications/notification.service';
import { provideI18nTesting } from '../../testing/i18n-testing';
import { BeneficiariesStore } from '../beneficiaries/beneficiaries.store';
import { SavingsStore } from '../savings/savings.store';
import { ScheduledTransferFormComponent } from './scheduled-transfer-form.component';
import { ScheduledTransfersStore } from './scheduled-transfers.store';

const FROM_ACCOUNT_ID = 7;
const TO_ACCOUNT_ID = 9;
const AMOUNT = 25;

const savingsAccounts: SavingsAccountListItemQueryData[] = [
  { id: FROM_ACCOUNT_ID, accountNo: '000000007', productName: 'Savings' },
  { id: TO_ACCOUNT_ID, accountNo: '000000009', productName: 'Savings' },
];

interface FormInternals {
  form: FormGroup<{
    fromAccountId: FormControl<number | null>;
    toDestination: FormControl<string>;
    amount: FormControl<number | null>;
    frequency: FormControl<ScheduledTransferFrequency>;
    startDate: FormControl<string | null>;
    endDate: FormControl<string | null>;
  }>;
  step(): string;
  initiate(): void;
  confirm(otp: string): void;
  toGroups(): { labelKey: string; options: { key: string; label: string }[] }[];
}

function tomorrow(): string {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-');
}

const showError = vi.fn();
const initiate = vi.fn();
const confirm = vi.fn();
const challenge = signal<ScheduledTransferChallengeCommandData | null>(null);

function createComponent(
  beneficiaries: BeneficiaryQueryData[] = [],
): ComponentFixture<ScheduledTransferFormComponent> {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideIonicAngular({ mode: 'md' }),
      ...provideI18nTesting(),
      provideRouter([]),
      { provide: NotificationService, useValue: { showError } },
      {
        provide: SavingsStore,
        useValue: { accounts: signal(savingsAccounts), loadAccounts: vi.fn() },
      },
      {
        provide: BeneficiariesStore,
        useValue: { beneficiaries: signal(beneficiaries), load: () => of([]) },
      },
      { provide: ScheduledTransfersStore, useValue: { initiate, confirm, challenge } },
    ],
  });
  return TestBed.createComponent(ScheduledTransferFormComponent);
}

function filledForm(): FormInternals {
  const fixture = createComponent();
  fixture.detectChanges();
  const component = fixture.componentInstance as unknown as FormInternals;
  component.form.controls.fromAccountId.setValue(FROM_ACCOUNT_ID);
  component.form.controls.toDestination.setValue(String(TO_ACCOUNT_ID));
  component.form.controls.amount.setValue(AMOUNT);
  return component;
}

describe('ScheduledTransferFormComponent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    challenge.set(null);
  });

  describe('submit guards', () => {
    it('toasts and refuses to dispatch when no source account is chosen', () => {
      const component = filledForm();
      component.form.controls.fromAccountId.setValue(null);
      component.initiate();

      expect(showError).toHaveBeenCalledWith('transfers.form.error.selectFromAccount');
      expect(initiate).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch when no destination is chosen', () => {
      const component = filledForm();
      component.form.controls.toDestination.setValue('');
      component.initiate();

      expect(showError).toHaveBeenCalledWith('transfers.form.error.selectToAccount');
      expect(initiate).not.toHaveBeenCalled();
    });

    it.each([null, 0])('toasts and refuses to dispatch for amount %s', (amount) => {
      const component = filledForm();
      component.form.controls.amount.setValue(amount);
      component.initiate();

      expect(showError).toHaveBeenCalledWith('transfers.form.error.amountRequired');
      expect(initiate).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch when the start date is cleared', () => {
      const component = filledForm();
      component.form.controls.startDate.setValue(null);
      component.initiate();

      expect(showError).toHaveBeenCalledWith('scheduledTransfers.form.error.startDateRequired');
      expect(initiate).not.toHaveBeenCalled();
    });
  });

  it('dispatches a monthly transfer starting tomorrow by default, with no end date', () => {
    initiate.mockReturnValue(of({ stepUpToken: 'tok', sentTo: 'a***@example.com' }));
    const component = filledForm();
    component.initiate();

    expect(initiate).toHaveBeenCalledWith({
      fromAccountId: FROM_ACCOUNT_ID,
      toAccountId: TO_ACCOUNT_ID,
      amount: AMOUNT,
      frequency: 'MONTHLY',
      startDate: tomorrow(),
      endDate: undefined,
    });
    expect(component.step()).toBe('otp');
  });

  it('sends the picked dates as ISO dates', () => {
    initiate.mockReturnValue(of({ stepUpToken: 'tok' }));
    const component = filledForm();
    component.form.controls.frequency.setValue('WEEKLY');
    component.form.controls.startDate.setValue('2027-01-04T00:00:00');
    component.form.controls.endDate.setValue('2027-06-28T00:00:00');
    component.initiate();

    expect(initiate).toHaveBeenCalledWith(
      expect.objectContaining({
        frequency: 'WEEKLY',
        startDate: '2027-01-04',
        endDate: '2027-06-28',
      }),
    );
  });

  it('confirms with the challenge token and reuses the idempotency key on a retry', () => {
    initiate.mockReturnValue(of({ stepUpToken: 'tok' }));
    confirm
      .mockReturnValueOnce(throwError(() => new Error('timed out')))
      .mockReturnValueOnce(of({ publicId: 'p-1' }));
    const component = filledForm();
    component.initiate();
    challenge.set({ stepUpToken: 'tok' });

    component.confirm('ABC123');
    component.confirm('ABC123');

    const [firstKey, firstBody] = confirm.mock.calls[0];
    const [secondKey] = confirm.mock.calls[1];
    expect(firstKey).toEqual(expect.any(String));
    expect(secondKey).toBe(firstKey);
    expect(firstBody).toEqual(
      expect.objectContaining({ stepUpToken: 'tok', otp: 'ABC123', toAccountId: TO_ACCOUNT_ID }),
    );
    expect(component.step()).toBe('done');
  });

  it('offers own accounts and beneficiaries, without the chosen source', () => {
    const fixture = createComponent([
      { publicId: 'b1', name: 'Asha', fineractAccountId: 42 },
      { publicId: 'b2', name: 'Self', fineractAccountId: FROM_ACCOUNT_ID },
    ]);
    fixture.detectChanges();
    const component = fixture.componentInstance as unknown as FormInternals;
    component.form.controls.fromAccountId.setValue(FROM_ACCOUNT_ID);

    const [mySavings, beneficiaries] = component.toGroups();
    expect(mySavings.options.map((option) => option.key)).toEqual([String(TO_ACCOUNT_ID)]);
    expect(beneficiaries.options.map((option) => option.label)).toEqual(['Asha']);
  });
});
