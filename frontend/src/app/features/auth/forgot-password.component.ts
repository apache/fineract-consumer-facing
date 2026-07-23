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
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ProfileStore } from '../profile/profile.store';

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/;
const OTP_PATTERN = /^[A-Z0-9]{6}$/;

@Component({
  selector: 'app-forgot-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressBarModule,
    RouterLink,
    TranslatePipe,
  ],
  template: `
    <mat-card class="forgot-card">
      <mat-card-header>
        <mat-card-title>{{ 'auth.forgotPassword.title' | translate }}</mat-card-title>
      </mat-card-header>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <mat-card-content>
        @if (step() === 'email') {
          <form [formGroup]="emailForm" (ngSubmit)="requestReset()">
            <mat-form-field appearance="fill">
              <mat-label>{{ 'common.field.email' | translate }}</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="username" />
            </mat-form-field>
            <button
              mat-flat-button
              color="primary"
              type="submit"
              [disabled]="loading() || emailForm.invalid"
            >
              {{ 'auth.forgotPassword.sendCta' | translate }}
            </button>
          </form>
        } @else {
          <p>{{ 'auth.forgotPassword.sentNotice' | translate }}</p>
          <form [formGroup]="resetForm" (ngSubmit)="reset()">
            <mat-form-field appearance="fill">
              <mat-label>{{ 'common.otp.codeLabel' | translate }}</mat-label>
              <input
                matInput
                formControlName="otp"
                autocomplete="one-time-code"
                autocapitalize="characters"
                maxlength="6"
                (input)="uppercase($event)"
              />
            </mat-form-field>
            <mat-form-field appearance="fill">
              <mat-label>{{ 'auth.forgotPassword.newPasswordLabel' | translate }}</mat-label>
              <input
                matInput
                type="password"
                formControlName="newPassword"
                autocomplete="new-password"
              />
              <mat-hint>{{ 'registration.identity.passwordHint' | translate }}</mat-hint>
            </mat-form-field>
            <button
              mat-flat-button
              color="primary"
              type="submit"
              [disabled]="loading() || resetForm.invalid"
            >
              {{ 'auth.forgotPassword.resetCta' | translate }}
            </button>
          </form>
        }
        <p class="login-prompt">
          <a routerLink="/login">{{ 'auth.forgotPassword.backToLogin' | translate }}</a>
        </p>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    :host {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100dvh;
      padding: 2rem 1rem;
    }
    .forgot-card {
      width: 100%;
      max-width: 24rem;
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    input {
      text-transform: none;
    }
    input[formControlName='otp'] {
      text-transform: uppercase;
    }
    .login-prompt {
      margin: 1rem 0 0;
      text-align: center;
    }
  `,
})
export class ForgotPasswordComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly store = inject(ProfileStore);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
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
    if (this.emailForm.invalid) {
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
    if (this.resetForm.invalid) {
      return;
    }
    const { otp, newPassword } = this.resetForm.getRawValue();
    this.loading.set(true);
    this.store
      .resetPassword({ email: this.emailForm.getRawValue().email, otp, newPassword })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.snackBar.open(
            this.translate.instant('auth.forgotPassword.success'),
            this.translate.instant('common.action.dismiss'),
            { duration: 5000 },
          );
          this.router.navigate(['/login']);
        },
        error: () => this.loading.set(false),
      });
  }

  protected uppercase(event: Event): void {
    const value = (event.target as HTMLInputElement).value.toUpperCase();
    this.resetForm.controls.otp.setValue(value);
  }
}
