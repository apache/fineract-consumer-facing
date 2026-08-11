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
import { provideIonicAngular, ToastController } from '@ionic/angular/standalone';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { BeneficiaryQueryData } from '@bff/client';
import { NotificationService } from '../../core/notifications/notification.service';
import { BeneficiariesComponent } from './beneficiaries.component';
import { BeneficiariesStore } from './beneficiaries.store';

const NAME_REQUIRED_KEY = 'beneficiaries.form.error.nameRequired';
const NAME_TOO_LONG_KEY = 'beneficiaries.form.error.nameTooLong';
const OFFICE_NAME_REQUIRED_KEY = 'beneficiaries.form.error.officeNameRequired';
const ACCOUNT_NUMBER_REQUIRED_KEY = 'beneficiaries.form.error.accountNumberRequired';
const TRANSFER_LIMIT_KEY = 'beneficiaries.form.error.transferLimitTooSmall';

const NAME = 'Ada Lovelace';
const TOO_LONG_NAME = 'a'.repeat(51);
const OFFICE_NAME = 'Head Office';
const ACCOUNT_NUMBER = '000000007';
const TRANSFER_LIMIT = 250;

const existing: BeneficiaryQueryData = {
  publicId: 'ben-1',
  name: NAME,
  transferLimit: TRANSFER_LIMIT,
};

interface BeneficiariesInternals {
  addForm: FormGroup<{
    name: FormControl<string>;
    officeName: FormControl<string>;
    accountNumber: FormControl<string>;
    transferLimit: FormControl<number | null>;
  }>;
  editForm: FormGroup<{
    name: FormControl<string>;
    transferLimit: FormControl<number | null>;
  }>;
  startEdit(row: BeneficiaryQueryData): void;
  submitAdd(): void;
  submitEdit(): void;
}

const showError = vi.fn();
const load = vi.fn(() => of([]));
const initiateAdd = vi.fn();
const initiateUpdate = vi.fn();

function createComponent(): BeneficiariesInternals {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideIonicAngular({ mode: 'md' }),
      provideTranslateService(),
      { provide: NotificationService, useValue: { showError } },
      {
        provide: ToastController,
        useValue: { create: () => Promise.resolve({ present: vi.fn() }) },
      },
      {
        provide: BeneficiariesStore,
        useValue: {
          load,
          initiateAdd,
          initiateUpdate,
          confirmAdd: vi.fn(),
          confirmUpdate: vi.fn(),
          delete: vi.fn(),
          beneficiaries: signal([existing]),
          challenge: signal(null),
        },
      },
    ],
  });
  const fixture: ComponentFixture<BeneficiariesComponent> =
    TestBed.createComponent(BeneficiariesComponent);
  fixture.detectChanges();
  return fixture.componentInstance as unknown as BeneficiariesInternals;
}

function fillAddForm(component: BeneficiariesInternals): void {
  component.addForm.setValue({
    name: NAME,
    officeName: OFFICE_NAME,
    accountNumber: ACCOUNT_NUMBER,
    transferLimit: null,
  });
}

describe('BeneficiariesComponent', () => {
  beforeEach(() => vi.clearAllMocks());

  describe('add form submit guards', () => {
    it('toasts and refuses to dispatch when the name is blank', () => {
      const component = createComponent();
      fillAddForm(component);
      component.addForm.controls.name.setValue('');
      component.submitAdd();

      expect(showError).toHaveBeenCalledWith(NAME_REQUIRED_KEY);
      expect(initiateAdd).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch when the name is too long', () => {
      const component = createComponent();
      fillAddForm(component);
      component.addForm.controls.name.setValue(TOO_LONG_NAME);
      component.submitAdd();

      expect(showError).toHaveBeenCalledWith(NAME_TOO_LONG_KEY);
      expect(initiateAdd).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch when the office name is blank', () => {
      const component = createComponent();
      fillAddForm(component);
      component.addForm.controls.officeName.setValue('');
      component.submitAdd();

      expect(showError).toHaveBeenCalledWith(OFFICE_NAME_REQUIRED_KEY);
      expect(initiateAdd).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch when the account number is blank', () => {
      const component = createComponent();
      fillAddForm(component);
      component.addForm.controls.accountNumber.setValue('');
      component.submitAdd();

      expect(showError).toHaveBeenCalledWith(ACCOUNT_NUMBER_REQUIRED_KEY);
      expect(initiateAdd).not.toHaveBeenCalled();
    });

    it.each([0, -5])('toasts and refuses to dispatch for transfer limit %s', (transferLimit) => {
      const component = createComponent();
      fillAddForm(component);
      component.addForm.controls.transferLimit.setValue(transferLimit);
      component.submitAdd();

      expect(showError).toHaveBeenCalledWith(TRANSFER_LIMIT_KEY);
      expect(initiateAdd).not.toHaveBeenCalled();
    });

    it('dispatches the step-up challenge, omitting an unset transfer limit', () => {
      initiateAdd.mockReturnValue(of({ stepUpToken: 'tok', sentTo: 'a***@example.com' }));
      const component = createComponent();
      fillAddForm(component);
      component.submitAdd();

      expect(showError).not.toHaveBeenCalled();
      expect(initiateAdd).toHaveBeenCalledWith({
        name: NAME,
        officeName: OFFICE_NAME,
        accountNumber: ACCOUNT_NUMBER,
      });
    });
  });

  describe('edit form submit guards', () => {
    it('toasts and refuses to dispatch when the name is blank', () => {
      const component = createComponent();
      component.startEdit(existing);
      component.editForm.controls.name.setValue('');
      component.submitEdit();

      expect(showError).toHaveBeenCalledWith(NAME_REQUIRED_KEY);
      expect(initiateUpdate).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch when the name is too long', () => {
      const component = createComponent();
      component.startEdit(existing);
      component.editForm.controls.name.setValue(TOO_LONG_NAME);
      component.submitEdit();

      expect(showError).toHaveBeenCalledWith(NAME_TOO_LONG_KEY);
      expect(initiateUpdate).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch for a transfer limit below the minimum', () => {
      const component = createComponent();
      component.startEdit(existing);
      component.editForm.controls.transferLimit.setValue(0);
      component.submitEdit();

      expect(showError).toHaveBeenCalledWith(TRANSFER_LIMIT_KEY);
      expect(initiateUpdate).not.toHaveBeenCalled();
    });

    it('dispatches the step-up challenge for the edited beneficiary', () => {
      initiateUpdate.mockReturnValue(of({ stepUpToken: 'tok', sentTo: 'a***@example.com' }));
      const component = createComponent();
      component.startEdit(existing);
      component.submitEdit();

      expect(showError).not.toHaveBeenCalled();
      expect(initiateUpdate).toHaveBeenCalledWith(existing.publicId, {
        name: NAME,
        transferLimit: TRANSFER_LIMIT,
      });
    });
  });
});
