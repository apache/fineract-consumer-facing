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
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { BeneficiaryQueryData, SavingsAccountListItemQueryData } from '@bff/client';
import { NotificationService } from '../../core/notifications/notification.service';
import { BeneficiariesStore } from '../beneficiaries/beneficiaries.store';
import { SavingsStore } from '../savings/savings.store';
import { TransferFormComponent } from './transfer-form.component';
import { TransfersStore } from './transfers.store';

const FROM_ACCOUNT_ID = 7;
const TO_ACCOUNT_ID = 9;
const FROM_DESTINATION = `SAVINGS:${FROM_ACCOUNT_ID}`;
const TO_DESTINATION = `SAVINGS:${TO_ACCOUNT_ID}`;
const AMOUNT = 25;
const FROM_ACCOUNT_KEY = 'transfers.form.error.selectFromAccount';
const TO_ACCOUNT_KEY = 'transfers.form.error.selectToAccount';
const AMOUNT_KEY = 'transfers.form.error.amountRequired';
const MY_SAVINGS_GROUP = 'transfers.form.toGroup.mySavings';
const BENEFICIARIES_GROUP = 'transfers.form.toGroup.beneficiaries';

const savingsAccounts: SavingsAccountListItemQueryData[] = [
  { id: FROM_ACCOUNT_ID, accountNo: '000000007', productName: 'Savings' },
  { id: TO_ACCOUNT_ID, accountNo: '000000009', productName: 'Savings' },
];

interface TransferFormInternals {
  form: FormGroup<{
    fromAccountId: FormControl<number | null>;
    toDestination: FormControl<string>;
    amount: FormControl<number | null>;
  }>;
  initiate(): void;
  toGroups(): { labelKey: string; options: { key: string; label: string }[] }[];
}

const showError = vi.fn();
const transfersInitiate = vi.fn();
const loadAccounts = vi.fn();
const loadBeneficiaries = vi.fn();

function createComponent(
  beneficiaries: BeneficiaryQueryData[] = [],
): ComponentFixture<TransferFormComponent> {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideIonicAngular({ mode: 'md' }),
      provideTranslateService(),
      provideRouter([]),
      { provide: NotificationService, useValue: { showError } },
      { provide: SavingsStore, useValue: { accounts: signal(savingsAccounts), loadAccounts } },
      {
        provide: BeneficiariesStore,
        useValue: { beneficiaries: signal(beneficiaries), load: loadBeneficiaries },
      },
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
  return TestBed.createComponent(TransferFormComponent);
}

describe('TransferFormComponent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    loadBeneficiaries.mockReturnValue(of([]));
  });

  describe('submit guards', () => {
    it('toasts and refuses to dispatch when no source account is chosen', () => {
      const fixture = createComponent();
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as TransferFormInternals;
      component.form.controls.toDestination.setValue(TO_DESTINATION);
      component.form.controls.amount.setValue(AMOUNT);
      component.initiate();

      expect(showError).toHaveBeenCalledWith(FROM_ACCOUNT_KEY);
      expect(transfersInitiate).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch when no destination is chosen', () => {
      const fixture = createComponent();
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as TransferFormInternals;
      component.form.controls.fromAccountId.setValue(FROM_ACCOUNT_ID);
      component.form.controls.amount.setValue(AMOUNT);
      component.initiate();

      expect(showError).toHaveBeenCalledWith(TO_ACCOUNT_KEY);
      expect(transfersInitiate).not.toHaveBeenCalled();
    });

    it.each([null, 0, -5])('toasts and refuses to dispatch for amount %s', (amount) => {
      const fixture = createComponent();
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as TransferFormInternals;
      component.form.controls.fromAccountId.setValue(FROM_ACCOUNT_ID);
      component.form.controls.toDestination.setValue(TO_DESTINATION);
      component.form.controls.amount.setValue(amount);
      component.initiate();

      expect(showError).toHaveBeenCalledWith(AMOUNT_KEY);
      expect(transfersInitiate).not.toHaveBeenCalled();
    });

    it('dispatches the transfer once every field is supplied', () => {
      transfersInitiate.mockReturnValue(of({ stepUpToken: 'step-up', sentTo: 'demo@example.com' }));
      const fixture = createComponent();
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as TransferFormInternals;
      component.form.controls.fromAccountId.setValue(FROM_ACCOUNT_ID);
      component.form.controls.toDestination.setValue(TO_DESTINATION);
      component.form.controls.amount.setValue(AMOUNT);
      component.initiate();

      expect(showError).not.toHaveBeenCalled();
      expect(transfersInitiate).toHaveBeenCalledWith({
        fromAccountId: FROM_ACCOUNT_ID,
        toAccountId: TO_ACCOUNT_ID,
        toAccountType: 'SAVINGS',
        amount: AMOUNT,
      });
    });
  });

  describe('destination options', () => {
    function optionKeys(component: TransferFormInternals, labelKey: string): string[] {
      return (component.toGroups().find((group) => group.labelKey === labelKey)?.options ?? []).map(
        (option) => option.key,
      );
    }

    it('offers every savings account while no source is chosen', () => {
      const fixture = createComponent();
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as TransferFormInternals;

      expect(optionKeys(component, MY_SAVINGS_GROUP)).toEqual([FROM_DESTINATION, TO_DESTINATION]);
    });

    it('drops the chosen source account from the destination list', () => {
      const fixture = createComponent();
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as TransferFormInternals;
      component.form.controls.fromAccountId.setValue(FROM_ACCOUNT_ID);

      expect(optionKeys(component, MY_SAVINGS_GROUP)).toEqual([TO_DESTINATION]);
    });

    it('drops a beneficiary that points at the chosen source account', () => {
      const fixture = createComponent([
        { publicId: 'b1', name: 'Asha', fineractAccountId: FROM_ACCOUNT_ID },
      ]);
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as TransferFormInternals;
      component.form.controls.fromAccountId.setValue(FROM_ACCOUNT_ID);

      expect(optionKeys(component, BENEFICIARIES_GROUP)).toEqual([]);
    });

    it('clears a destination that the newly chosen source invalidates', () => {
      const fixture = createComponent();
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as TransferFormInternals;
      component.form.controls.toDestination.setValue(TO_DESTINATION);
      component.form.controls.fromAccountId.setValue(TO_ACCOUNT_ID);

      expect(component.form.controls.toDestination.value).toBe('');
    });
  });
});
