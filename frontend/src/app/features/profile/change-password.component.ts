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
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { OtpComponent } from '../../shared/otp/otp.component';
import { ProfileStore } from './profile.store';

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/;

@Component({
  selector: 'app-change-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    TranslatePipe,
    OtpComponent,
  ],
  template: `
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (step() === 'form') {
      <form [formGroup]="form" (ngSubmit)="initiate()">
        <mat-form-field appearance="fill">
          <mat-label>{{ 'profile.changePassword.currentLabel' | translate }}</mat-label>
          <input
            matInput
            type="password"
            formControlName="currentPassword"
            autocomplete="current-password"
          />
        </mat-form-field>
        <mat-form-field appearance="fill">
          <mat-label>{{ 'profile.changePassword.newLabel' | translate }}</mat-label>
          <input
            matInput
            type="password"
            formControlName="newPassword"
            autocomplete="new-password"
          />
          <mat-hint>{{ 'registration.identity.passwordHint' | translate }}</mat-hint>
        </mat-form-field>
        <div class="actions">
          <button
            mat-flat-button
            color="primary"
            type="submit"
            [disabled]="loading() || form.invalid"
          >
            {{ 'profile.changePassword.submitCta' | translate }}
          </button>
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
  styles: `
    form {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      max-width: 24rem;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
    }
  `,
})
export class ChangePasswordComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly snackBar = inject(MatSnackBar);
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
          this.snackBar.open(
            this.translate.instant('profile.changePassword.success'),
            this.translate.instant('common.action.dismiss'),
            { duration: 5000 },
          );
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
}
