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

import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { IonButton, IonInput, IonProgressBar, ToastController } from '@ionic/angular/standalone';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { OtpComponent } from '../../shared/otp/otp.component';
import { ProfileStore } from './profile.store';

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/;

@Component({
  selector: 'app-change-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    IonButton,
    IonInput,
    IonProgressBar,
    TranslatePipe,
    OtpComponent,
  ],
  template: `
    @if (loading()) {
      <ion-progress-bar type="indeterminate" />
    }

    @if (step() === 'form') {
      <form [formGroup]="form" (ngSubmit)="initiate()">
        <ion-input
          type="password"
          formControlName="currentPassword"
          fill="outline"
          labelPlacement="stacked"
          [label]="'profile.changePassword.currentLabel' | translate"
          autocomplete="current-password"
        />
        <ion-input
          type="password"
          formControlName="newPassword"
          fill="outline"
          labelPlacement="stacked"
          [label]="'profile.changePassword.newLabel' | translate"
          [helperText]="'registration.identity.passwordHint' | translate"
          autocomplete="new-password"
        />
        <div class="actions-end">
          <ion-button type="submit" [disabled]="loading() || form.invalid">
            {{ 'profile.changePassword.submitCta' | translate }}
          </ion-button>
        </div>
      </form>
    } @else {
      <app-otp
        [sentTo]="store.passwordChangeChallenge()?.sentTo ?? null"
        [loading]="loading()"
        (submitted)="confirm($event)"
        (cancelled)="backToForm()"
      />
    }
  `,
  styleUrls: ['../../shared/css/form.scss', '../../shared/css/actions.scss'],
  styles: `
    form {
      max-width: 24rem;
    }
  `,
})
export class ChangePasswordComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly toastCtrl = inject(ToastController);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(ProfileStore);

  protected readonly step = signal<'form' | 'otp'>('form');
  protected readonly loading = signal(false);

  protected readonly form = this.fb.group({
    currentPassword: ['', Validators.required],
    newPassword: [
      '',
      [
        Validators.required,
        Validators.minLength(15),
        Validators.maxLength(64),
        Validators.pattern(PASSWORD_PATTERN),
      ],
    ],
  });

  protected initiate(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.store
      .initiatePasswordChange({ currentPassword: this.form.getRawValue().currentPassword })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.step.set('otp');
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected confirm(otp: string): void {
    const stepUpToken = this.store.passwordChangeChallenge()?.stepUpToken;
    if (!stepUpToken) {
      return;
    }
    this.loading.set(true);
    this.store
      .confirmPasswordChange({ stepUpToken, otp, newPassword: this.form.getRawValue().newPassword })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.presentSuccessToast();
          this.form.reset();
          this.step.set('form');
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected backToForm(): void {
    this.step.set('form');
  }

  private presentSuccessToast(): void {
    this.toastCtrl
      .create({
        message: this.translate.instant('profile.changePassword.success'),
        duration: 5000,
        buttons: [{ text: this.translate.instant('common.action.dismiss'), role: 'cancel' }],
      })
      .then(toast => toast.present());
  }
}
