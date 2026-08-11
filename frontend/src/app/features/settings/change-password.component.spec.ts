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
import { provideIonicAngular } from '@ionic/angular/standalone';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { NotificationService } from '../../core/notifications/notification.service';
import { ChangePasswordComponent } from './change-password.component';
import { SettingsStore } from './settings.store';

const CURRENT_REQUIRED_KEY = 'settings.changePassword.error.currentRequired';
const NEW_REQUIRED_KEY = 'settings.changePassword.error.newRequired';
const PASSWORD_RULES_KEY = 'common.error.passwordRules';

const CURRENT_PASSWORD = 'Current-password-1!';
const VALID_NEW_PASSWORD = 'New-password-12345!';
const TOO_SHORT = 'Short-pass-1!';
const MISSING_CHARACTER_CLASSES = 'abcdefghijklmnop';
const TOO_LONG = `A${'a'.repeat(70)}1!`;

interface ChangePasswordInternals {
  form: FormGroup<{
    currentPassword: FormControl<string>;
    newPassword: FormControl<string>;
  }>;
  initiate(): void;
}

const showError = vi.fn();
const initiatePasswordChange = vi.fn();

function createComponent(): ComponentFixture<ChangePasswordComponent> {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideIonicAngular({ mode: 'md' }),
      provideTranslateService(),
      { provide: NotificationService, useValue: { showError } },
      {
        provide: SettingsStore,
        useValue: {
          initiatePasswordChange,
          confirmPasswordChange: vi.fn(),
          passwordChangeChallenge: signal(null),
        },
      },
    ],
  });
  return TestBed.createComponent(ChangePasswordComponent);
}

describe('ChangePasswordComponent', () => {
  beforeEach(() => vi.clearAllMocks());

  describe('submit guards', () => {
    it('toasts and refuses to dispatch when the current password is blank', () => {
      const fixture = createComponent();
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as ChangePasswordInternals;
      component.form.controls.newPassword.setValue(VALID_NEW_PASSWORD);
      component.initiate();

      expect(showError).toHaveBeenCalledWith(CURRENT_REQUIRED_KEY);
      expect(initiatePasswordChange).not.toHaveBeenCalled();
    });

    it('toasts and refuses to dispatch when the new password is blank', () => {
      const fixture = createComponent();
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as ChangePasswordInternals;
      component.form.controls.currentPassword.setValue(CURRENT_PASSWORD);
      component.initiate();

      expect(showError).toHaveBeenCalledWith(NEW_REQUIRED_KEY);
      expect(initiatePasswordChange).not.toHaveBeenCalled();
    });

    it.each([TOO_SHORT, MISSING_CHARACTER_CLASSES, TOO_LONG])(
      'toasts one combined shape message and refuses to dispatch for %s',
      (newPassword) => {
        const fixture = createComponent();
        fixture.detectChanges();

        const component = fixture.componentInstance as unknown as ChangePasswordInternals;
        component.form.controls.currentPassword.setValue(CURRENT_PASSWORD);
        component.form.controls.newPassword.setValue(newPassword);
        component.initiate();

        expect(showError).toHaveBeenCalledWith(PASSWORD_RULES_KEY);
        expect(initiatePasswordChange).not.toHaveBeenCalled();
      },
    );

    it('dispatches the step-up challenge when both passwords are acceptable', () => {
      initiatePasswordChange.mockReturnValue(
        of({ stepUpToken: 'tok', sentTo: 'a***@example.com' }),
      );
      const fixture = createComponent();
      fixture.detectChanges();

      const component = fixture.componentInstance as unknown as ChangePasswordInternals;
      component.form.controls.currentPassword.setValue(CURRENT_PASSWORD);
      component.form.controls.newPassword.setValue(VALID_NEW_PASSWORD);
      component.initiate();

      expect(showError).not.toHaveBeenCalled();
      expect(initiatePasswordChange).toHaveBeenCalledWith({ currentPassword: CURRENT_PASSWORD });
    });
  });
});
