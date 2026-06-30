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
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';
import { VerifyOtpCommandData } from '@bff/client';
import { OtpComponent } from '../../shared/otp/otp.component';
import { RegistrationService } from './registration.service';

@Component({
  selector: 'app-registration',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressBarModule,
    MatSelectModule,
    OtpComponent,
    TranslatePipe,
  ],
  template: `
    <mat-card class="registration-card">
      @if (step() !== 'done') {
        <mat-card-header>
          <mat-card-title>{{ 'registration.title' | translate }}</mat-card-title>
        </mat-card-header>
      }

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <mat-card-content>
        @switch (step()) {
          @case ('identity') {
            <form [formGroup]="identityForm" (ngSubmit)="submitIdentity()">
              <mat-form-field appearance="fill">
                <mat-label>{{ 'registration.identity.clientIdLabel' | translate }}</mat-label>
                <input matInput type="number" formControlName="fineractClientId" min="1" />
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'common.field.email' | translate }}</mat-label>
                <input matInput type="email" formControlName="email" autocomplete="username" />
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'common.field.password' | translate }}</mat-label>
                <input
                  matInput
                  type="password"
                  formControlName="password"
                  autocomplete="new-password"
                />
                <mat-hint>{{ 'registration.identity.passwordHint' | translate }}</mat-hint>
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'registration.identity.documentTypeLabel' | translate }}</mat-label>
                <mat-select formControlName="documentTypeName">
                  <mat-option value="SSN">SSN</mat-option>
                  <mat-option value="Aadhaar">Aadhaar</mat-option>
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'registration.identity.documentNumberLabel' | translate }}</mat-label>
                <input
                  matInput
                  type="password"
                  formControlName="documentKey"
                  autocomplete="off"
                />
              </mat-form-field>
              <button
                mat-flat-button
                color="primary"
                type="submit"
                [disabled]="loading() || identityForm.invalid"
              >
                {{ 'common.action.continue' | translate }}
              </button>
            </form>
          }
          @case ('otp') {
            <app-otp
              [sentTo]="sentTo()"
              [loading]="loading()"
              (submitted)="verifyOtp($event)"
              (cancelled)="backToIdentity()"
            />
            <button
              mat-button
              type="button"
              [disabled]="loading() || resendCooldown() > 0"
              (click)="resend()"
            >
              @if (resendCooldown() > 0) {
                {{ 'registration.otp.resendIn' | translate: { seconds: resendCooldown() } }}
              } @else {
                {{ 'registration.otp.resend' | translate }}
              }
            </button>
          }
          @case ('done') {
            <div class="done">
              <h2 class="done-title">{{ 'registration.done.title' | translate }}</h2>
              @if (maskedLastFour()) {
                <p class="done-sub">
                  {{ 'registration.done.idEnding' | translate: { lastFour: maskedLastFour() } }}
                </p>
              }
              <a mat-flat-button color="primary" routerLink="/login">{{
                'registration.done.continueToLogin' | translate
              }}</a>
            </div>
          }
        }
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
    .registration-card {
      width: 100%;
      max-width: 24rem;
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .done {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      gap: 1rem;
      padding: 1rem 0;
    }
    .done-title {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 500;
    }
    .done-sub {
      margin: 0;
      color: rgba(0, 0, 0, 0.6);
    }
  `,
})
export class RegistrationComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly registration = inject(RegistrationService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly step = signal<'identity' | 'otp' | 'done'>('identity');
  protected readonly loading = signal(false);
  protected readonly registrationId = signal<string | null>(null);
  protected readonly sentTo = signal<string | null>(null);
  protected readonly maskedLastFour = signal<string | null>(null);
  protected readonly resendCooldown = signal(0);

  private cooldownHandle: ReturnType<typeof setInterval> | null = null;

  protected readonly identityForm = this.fb.group({
    fineractClientId: this.fb.control<number | null>(null, [
      Validators.required,
      Validators.min(1),
    ]),
    email: ['', [Validators.required, Validators.email]],
    password: [
      '',
      [
        Validators.required,
        Validators.minLength(15),
        Validators.maxLength(64),
        Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/),
      ],
    ],
    documentTypeName: ['', Validators.required],
    documentKey: ['', Validators.required],
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.clearCooldown());
  }

  protected submitIdentity(): void {
    if (this.identityForm.invalid) {
      return;
    }
    const raw = this.identityForm.getRawValue();
    this.loading.set(true);
    this.registration
      .submitIdentity({
        fineractClientId: Number(raw.fineractClientId),
        email: raw.email,
        password: raw.password,
        documentTypeName: raw.documentTypeName,
        documentKey: raw.documentKey,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: data => {
          this.registrationId.set(data.registrationId ?? null);
          this.maskedLastFour.set(data.maskedLastFour ?? null);
          this.step.set('otp');
          this.loading.set(false);
          this.requestOtp();
        },
        error: () => this.loading.set(false),
      });
  }

  protected verifyOtp(code: string): void {
    const id = this.registrationId();
    if (!id) {
      return;
    }
    this.loading.set(true);
    this.registration
      .verifyOtp({ registrationId: id, token: code })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: data => {
          if (data.status === VerifyOtpCommandData.StatusEnum.Bound) {
            this.clearCooldown();
            this.step.set('done');
          }
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected resend(): void {
    if (this.loading() || this.resendCooldown() > 0) {
      return;
    }
    this.requestOtp();
  }

  protected backToIdentity(): void {
    this.clearCooldown();
    this.resendCooldown.set(0);
    this.step.set('identity');
  }

  private requestOtp(): void {
    const id = this.registrationId();
    if (!id) {
      return;
    }
    this.loading.set(true);
    this.registration
      .sendOtp({ registrationId: id, deliveryMethod: 'email' })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: data => {
          this.sentTo.set(data.sentTo ?? null);
          this.startCooldown(data.tokenLiveTimeInSec ?? 0);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  private startCooldown(seconds: number): void {
    this.clearCooldown();
    const initial = Math.max(0, Math.floor(seconds));
    this.resendCooldown.set(initial);
    if (initial === 0) {
      return;
    }
    this.cooldownHandle = setInterval(() => {
      this.resendCooldown.update(remaining => {
        if (remaining <= 1) {
          this.clearCooldown();
          return 0;
        }
        return remaining - 1;
      });
    }, 1000);
  }

  private clearCooldown(): void {
    if (this.cooldownHandle) {
      clearInterval(this.cooldownHandle);
      this.cooldownHandle = null;
    }
  }
}
