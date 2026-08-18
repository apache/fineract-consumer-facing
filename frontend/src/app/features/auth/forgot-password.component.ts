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

import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonInput,
  IonProgressBar,
  ToastController,
} from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { ProfileStore } from '../profile/profile.store';

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/;
const OTP_PATTERN = /^[A-Z0-9]{6}$/;

@Component({
  selector: 'app-forgot-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgOptimizedImage,
    ReactiveFormsModule,
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardTitle,
    IonInput,
    IonProgressBar,
    RouterLink,
    TranslatePipe,
  ],
  template: `
    <ion-card class="page-card">
      <ion-card-header>
        <img
          ngSrc="/apache-fineract-logo.png"
          width="64"
          height="64"
          alt=""
          class="page-logo"
          priority
        />
        <ion-card-title>{{ 'auth.forgotPassword.title' | translate }}</ion-card-title>
      </ion-card-header>

      @if (loading()) {
        <ion-progress-bar type="indeterminate" />
      }

      <ion-card-content>
        @if (step() === 'email') {
          <form [formGroup]="emailForm" (ngSubmit)="requestReset()">
            <ion-input
              type="email"
              formControlName="email"
              fill="outline"
              labelPlacement="stacked"
              [label]="'common.field.email' | translate"
              autocomplete="username"
            />
            <ion-button type="submit" [disabled]="loading()">
              {{ 'auth.forgotPassword.sendCta' | translate }}
            </ion-button>
          </form>
        } @else {
          <p>{{ 'auth.forgotPassword.sentNotice' | translate }}</p>
          <form [formGroup]="resetForm" (ngSubmit)="reset()">
            <ion-input
              class="otp-input"
              formControlName="otp"
              fill="outline"
              labelPlacement="stacked"
              [label]="'common.otp.codeLabel' | translate"
              autocomplete="one-time-code"
              autocapitalize="characters"
              [maxlength]="6"
              (ionInput)="uppercase($event)"
            />
            <ion-input
              type="password"
              formControlName="newPassword"
              fill="outline"
              labelPlacement="stacked"
              [label]="'auth.forgotPassword.newPasswordLabel' | translate"
              [helperText]="'registration.identity.passwordHint' | translate"
              autocomplete="new-password"
            />
            <ion-button type="submit" [disabled]="loading()">
              {{ 'auth.forgotPassword.resetCta' | translate }}
            </ion-button>
          </form>
        }
        <p class="prompt">
          <a routerLink="/login">{{ 'auth.forgotPassword.backToLogin' | translate }}</a>
        </p>
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: ['../../shared/css/centered-page.scss', '../../shared/css/form.scss'],
})
export class ForgotPasswordComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly store = inject(ProfileStore);
  private readonly router = inject(Router);
  private readonly toastCtrl = inject(ToastController);
  private readonly i18n = inject(I18nService);
  private readonly notifications = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly step = signal<'email' | 'reset'>('email');
  protected readonly loading = signal(false);

  protected readonly emailForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
  });

  protected readonly resetForm = this.fb.group({
    otp: ['', [Validators.required, Validators.pattern(OTP_PATTERN)]],
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

  protected requestReset(): void {
    const email = this.emailForm.controls.email;
    if (email.hasError('required')) {
      this.notifications.showError('auth.forgotPassword.error.emailRequired');
      return;
    }
    if (email.hasError('email')) {
      this.notifications.showError('auth.forgotPassword.error.emailInvalid');
      return;
    }
    this.loading.set(true);
    this.store
      .forgotPassword({ email: this.emailForm.getRawValue().email })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.step.set('reset');
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected reset(): void {
    const controls = this.resetForm.controls;
    if (controls.otp.hasError('required')) {
      this.notifications.showError('auth.forgotPassword.error.otpRequired');
      return;
    }
    if (controls.otp.hasError('pattern')) {
      this.notifications.showError('auth.forgotPassword.error.otpInvalid');
      return;
    }
    if (controls.newPassword.hasError('required')) {
      this.notifications.showError('auth.forgotPassword.error.passwordRequired');
      return;
    }
    if (
      controls.newPassword.hasError('minlength') ||
      controls.newPassword.hasError('maxlength') ||
      controls.newPassword.hasError('pattern')
    ) {
      this.notifications.showError('common.error.passwordRules');
      return;
    }
    const { otp, newPassword } = this.resetForm.getRawValue();
    this.loading.set(true);
    this.store
      .resetPassword({ email: this.emailForm.getRawValue().email, otp, newPassword })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.presentSuccessToast();
          this.router.navigate(['/login']);
        },
        error: () => this.loading.set(false),
      });
  }

  protected uppercase(event: CustomEvent<{ value?: string | null }>): void {
    const value = (event.detail.value ?? '').toUpperCase();
    this.resetForm.controls.otp.setValue(value);
  }

  private presentSuccessToast(): void {
    this.toastCtrl
      .create({
        message: this.i18n.translate('auth.forgotPassword.success'),
        duration: 5000,
        buttons: [{ text: this.i18n.translate('common.action.dismiss'), role: 'cancel' }],
      })
      .then((toast) => toast.present());
  }
}
