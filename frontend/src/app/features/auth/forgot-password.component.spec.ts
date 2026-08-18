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
import { provideRouter, Router } from '@angular/router';
import { provideIonicAngular, ToastController } from '@ionic/angular/standalone';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { NotificationService } from '../../core/notifications/notification.service';
import { ProfileStore } from '../profile/profile.store';
import { ForgotPasswordComponent } from './forgot-password.component';

const EMAIL = 'demo@example.com';
const OTP = 'AB12CD';
const VALID_PASSWORD = 'Str0ng!Passphrase';
const EMAIL_REQUIRED_KEY = 'auth.forgotPassword.error.emailRequired';
const EMAIL_INVALID_KEY = 'auth.forgotPassword.error.emailInvalid';
const OTP_REQUIRED_KEY = 'auth.forgotPassword.error.otpRequired';
const OTP_INVALID_KEY = 'auth.forgotPassword.error.otpInvalid';
const PASSWORD_REQUIRED_KEY = 'auth.forgotPassword.error.passwordRequired';
const PASSWORD_RULES_KEY = 'common.error.passwordRules';

interface ForgotPasswordInternals {
  emailForm: FormGroup<{ email: FormControl<string> }>;
  resetForm: FormGroup<{ otp: FormControl<string>; newPassword: FormControl<string> }>;
  requestReset(): void;
  reset(): void;
}

const showError = vi.fn();
const forgotPassword = vi.fn();
const resetPassword = vi.fn();

function createComponent(): ComponentFixture<ForgotPasswordComponent> {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideIonicAngular({ mode: 'md' }),
      provideTranslateService(),
      provideRouter([]),
      { provide: NotificationService, useValue: { showError } },
      { provide: ProfileStore, useValue: { forgotPassword, resetPassword } },
      {
        provide: ToastController,
        useValue: { create: () => Promise.resolve({ present: vi.fn() }) },
      },
    ],
  });
  vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  return TestBed.createComponent(ForgotPasswordComponent);
}

function internals(): ForgotPasswordInternals {
  const fixture = createComponent();
  fixture.detectChanges();
  return fixture.componentInstance as unknown as ForgotPasswordInternals;
}

describe('ForgotPasswordComponent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    forgotPassword.mockReturnValue(of({}));
    resetPassword.mockReturnValue(of({}));
  });

  describe('email step guards', () => {
    it('toasts and refuses to dispatch when the email is blank', () => {
      const component = internals();
      component.requestReset();

      expect(showError).toHaveBeenCalledWith(EMAIL_REQUIRED_KEY);
      expect(forgotPassword).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch when the email is malformed', () => {
      const component = internals();
      component.emailForm.controls.email.setValue('not-an-email');
      component.requestReset();

      expect(showError).toHaveBeenCalledWith(EMAIL_INVALID_KEY);
      expect(forgotPassword).not.toHaveBeenCalled();
    });

    it('dispatches when the email is well formed', () => {
      const component = internals();
      component.emailForm.controls.email.setValue(EMAIL);
      component.requestReset();

      expect(showError).not.toHaveBeenCalled();
      expect(forgotPassword).toHaveBeenCalledWith({ email: EMAIL });
    });
  });

  describe('reset step guards', () => {
    function atResetStep(): ForgotPasswordInternals {
      const component = internals();
      component.emailForm.controls.email.setValue(EMAIL);
      component.requestReset();
      return component;
    }

    it('toasts and refuses to dispatch when the code is blank', () => {
      const component = atResetStep();
      component.resetForm.controls.newPassword.setValue(VALID_PASSWORD);
      component.reset();

      expect(showError).toHaveBeenCalledWith(OTP_REQUIRED_KEY);
      expect(resetPassword).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch when the code is not six alphanumerics', () => {
      const component = atResetStep();
      component.resetForm.controls.otp.setValue('ab-12');
      component.resetForm.controls.newPassword.setValue(VALID_PASSWORD);
      component.reset();

      expect(showError).toHaveBeenCalledWith(OTP_INVALID_KEY);
      expect(resetPassword).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch when the new password is blank', () => {
      const component = atResetStep();
      component.resetForm.controls.otp.setValue(OTP);
      component.reset();

      expect(showError).toHaveBeenCalledWith(PASSWORD_REQUIRED_KEY);
      expect(resetPassword).not.toHaveBeenCalled();
    });

    it.each([
      ['too short', 'Sh0rt!Pass'],
      ['too long', `${VALID_PASSWORD}${'a'.repeat(64)}`],
      ['missing a character class', 'passphrasewithoutanything'],
    ])('toasts and refuses to dispatch for a password %s', (_case, password) => {
      const component = atResetStep();
      component.resetForm.controls.otp.setValue(OTP);
      component.resetForm.controls.newPassword.setValue(password);
      component.reset();

      expect(showError).toHaveBeenCalledWith(PASSWORD_RULES_KEY);
      expect(resetPassword).not.toHaveBeenCalled();
    });

    it('dispatches when the code and password both satisfy their rules', () => {
      const component = atResetStep();
      component.resetForm.controls.otp.setValue(OTP);
      component.resetForm.controls.newPassword.setValue(VALID_PASSWORD);
      component.reset();

      expect(showError).not.toHaveBeenCalled();
      expect(resetPassword).toHaveBeenCalledWith({
        email: EMAIL,
        otp: OTP,
        newPassword: VALID_PASSWORD,
      });
    });
  });
});
