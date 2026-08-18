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
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup } from '@angular/forms';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { NotificationService } from '../../core/notifications/notification.service';
import { RegistrationComponent } from './registration.component';
import { RegistrationService } from './registration.service';

interface IdentityValue {
  fineractClientId: number | null;
  email: string;
  password: string;
  documentTypeName: string;
  documentKey: string;
}

const VALID_IDENTITY: IdentityValue = {
  fineractClientId: 42,
  email: 'jane@example.com',
  password: 'Sup3rSecret!Passw0rd',
  documentTypeName: 'SSN',
  documentKey: '123-45-6789',
};

const CLIENT_ID_REQUIRED_KEY = 'registration.identity.error.clientIdRequired';
const CLIENT_ID_INVALID_KEY = 'registration.identity.error.clientIdInvalid';
const EMAIL_REQUIRED_KEY = 'registration.identity.error.emailRequired';
const EMAIL_INVALID_KEY = 'registration.identity.error.emailInvalid';
const PASSWORD_REQUIRED_KEY = 'registration.identity.error.passwordRequired';
const PASSWORD_RULES_KEY = 'common.error.passwordRules';
const DOCUMENT_TYPE_REQUIRED_KEY = 'registration.identity.error.documentTypeRequired';
const DOCUMENT_KEY_REQUIRED_KEY = 'registration.identity.error.documentKeyRequired';
const GENERIC_KEY = 'registration.identity.error.invalid';

interface RegistrationInternals {
  identityForm: FormGroup<{
    fineractClientId: FormControl<number | null>;
    email: FormControl<string>;
    password: FormControl<string>;
    documentTypeName: FormControl<string>;
    documentKey: FormControl<string>;
  }>;
  submitIdentity(): void;
}

const showError = vi.fn();
const submitIdentity = vi.fn();
const sendOtp = vi.fn();

function createComponent(): ComponentFixture<RegistrationComponent> {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideIonicAngular({ mode: 'md' }),
      provideTranslateService(),
      provideRouter([]),
      { provide: NotificationService, useValue: { showError } },
      { provide: RegistrationService, useValue: { submitIdentity, sendOtp, verifyOtp: vi.fn() } },
    ],
  });
  const fixture = TestBed.createComponent(RegistrationComponent);
  fixture.detectChanges();
  return fixture;
}

function fillIdentity(
  component: RegistrationInternals,
  overrides: Partial<IdentityValue> = {},
): void {
  component.identityForm.setValue({ ...VALID_IDENTITY, ...overrides });
}

describe('RegistrationComponent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sendOtp.mockReturnValue(of({ sentTo: 'j***@example.com', tokenLiveTimeInSec: 0 }));
  });

  describe('identity submit guards', () => {
    it.each([
      { overrides: { fineractClientId: null }, key: CLIENT_ID_REQUIRED_KEY },
      { overrides: { fineractClientId: 0 }, key: CLIENT_ID_INVALID_KEY },
      { overrides: { email: '' }, key: EMAIL_REQUIRED_KEY },
      { overrides: { email: 'jane@' }, key: EMAIL_INVALID_KEY },
      { overrides: { password: '' }, key: PASSWORD_REQUIRED_KEY },
      { overrides: { password: 'Sh0rt!Pass' }, key: PASSWORD_RULES_KEY },
      { overrides: { password: `Sup3rSecret!${'a'.repeat(60)}` }, key: PASSWORD_RULES_KEY },
      { overrides: { password: 'nouppercasedigitorspecial' }, key: PASSWORD_RULES_KEY },
      { overrides: { documentTypeName: '' }, key: DOCUMENT_TYPE_REQUIRED_KEY },
      { overrides: { documentKey: '' }, key: DOCUMENT_KEY_REQUIRED_KEY },
    ])('toasts $key and refuses to dispatch', ({ overrides, key }) => {
      const component = createComponent().componentInstance as unknown as RegistrationInternals;
      fillIdentity(component, overrides);

      component.submitIdentity();

      expect(showError).toHaveBeenCalledWith(key);
      expect(submitIdentity).not.toHaveBeenCalled();
    });

    it('toasts a generic reason for a validation error the toast table does not name', () => {
      const component = createComponent().componentInstance as unknown as RegistrationInternals;
      fillIdentity(component);
      component.identityForm.controls.documentKey.setErrors({ serverRejected: true });

      component.submitIdentity();

      expect(showError).toHaveBeenCalledWith(GENERIC_KEY);
      expect(submitIdentity).not.toHaveBeenCalled();
    });

    it('dispatches the registration when every field is valid', () => {
      submitIdentity.mockReturnValue(of({ registrationId: 'reg-1', maskedLastFour: '6789' }));
      const component = createComponent().componentInstance as unknown as RegistrationInternals;
      fillIdentity(component);

      component.submitIdentity();

      expect(showError).not.toHaveBeenCalled();
      expect(submitIdentity).toHaveBeenCalledWith(VALID_IDENTITY);
    });
  });
});
