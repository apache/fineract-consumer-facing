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
import { RouterLink } from '@angular/router';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonInput,
  IonProgressBar,
  IonRouterLink,
  IonSelect,
  IonSelectOption,
} from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';
import { VerifyOtpCommandData } from '@bff/client';
import { NotificationService } from '../../core/notifications/notification.service';
import { OtpComponent } from '../../shared/otp/otp.component';
import { RegistrationService } from './registration.service';

type IdentityField = 'fineractClientId' | 'email' | 'password' | 'documentTypeName' | 'documentKey';

const IDENTITY_ERRORS: readonly { field: IdentityField; error: string; key: string }[] = [
  {
    field: 'fineractClientId',
    error: 'required',
    key: 'registration.identity.error.clientIdRequired',
  },
  { field: 'fineractClientId', error: 'min', key: 'registration.identity.error.clientIdInvalid' },
  { field: 'email', error: 'required', key: 'registration.identity.error.emailRequired' },
  { field: 'email', error: 'email', key: 'registration.identity.error.emailInvalid' },
  { field: 'password', error: 'required', key: 'registration.identity.error.passwordRequired' },
  { field: 'password', error: 'minlength', key: 'common.error.passwordRules' },
  { field: 'password', error: 'maxlength', key: 'common.error.passwordRules' },
  { field: 'password', error: 'pattern', key: 'common.error.passwordRules' },
  {
    field: 'documentTypeName',
    error: 'required',
    key: 'registration.identity.error.documentTypeRequired',
  },
  {
    field: 'documentKey',
    error: 'required',
    key: 'registration.identity.error.documentKeyRequired',
  },
];

const GENERIC_ERROR_KEY = 'registration.identity.error.invalid';

@Component({
  selector: 'app-registration',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NgOptimizedImage,
    ReactiveFormsModule,
    RouterLink,
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardTitle,
    IonInput,
    IonProgressBar,
    IonRouterLink,
    IonSelect,
    IonSelectOption,
    OtpComponent,
    TranslatePipe,
  ],
  template: `
    <ion-card class="page-card">
      @if (step() !== 'done') {
        <ion-card-header>
          <img
            ngSrc="/apache-fineract-logo.png"
            width="64"
            height="64"
            alt=""
            class="page-logo"
            priority
          />
          <ion-card-title>{{ 'registration.title' | translate }}</ion-card-title>
        </ion-card-header>
      }

      @if (loading()) {
        <ion-progress-bar type="indeterminate" />
      }

      <ion-card-content>
        @switch (step()) {
          @case ('identity') {
            <form [formGroup]="identityForm" (ngSubmit)="submitIdentity()">
              <ion-input
                type="number"
                formControlName="fineractClientId"
                fill="outline"
                labelPlacement="stacked"
                [label]="'registration.identity.clientIdLabel' | translate"
                min="1"
              />
              <ion-input
                type="email"
                formControlName="email"
                fill="outline"
                labelPlacement="stacked"
                [label]="'common.field.email' | translate"
                autocomplete="username"
              />
              <ion-input
                type="password"
                formControlName="password"
                fill="outline"
                labelPlacement="stacked"
                [label]="'common.field.password' | translate"
                [helperText]="'registration.identity.passwordHint' | translate"
                autocomplete="new-password"
              />
              <ion-select
                formControlName="documentTypeName"
                fill="outline"
                labelPlacement="stacked"
                interface="popover"
                [label]="'registration.identity.documentTypeLabel' | translate"
              >
                <ion-select-option value="SSN">SSN</ion-select-option>
                <ion-select-option value="Aadhaar">Aadhaar</ion-select-option>
              </ion-select>
              <ion-input
                type="password"
                formControlName="documentKey"
                fill="outline"
                labelPlacement="stacked"
                [label]="'registration.identity.documentNumberLabel' | translate"
                autocomplete="off"
              />
              <ion-button type="submit" [disabled]="loading()">
                {{ 'common.action.continue' | translate }}
              </ion-button>
            </form>
          }
          @case ('otp') {
            <app-otp
              [sentTo]="sentTo()"
              [loading]="loading()"
              (submitted)="verifyOtp($event)"
              (cancelled)="backToIdentity()"
            />
            <ion-button
              fill="clear"
              type="button"
              [disabled]="loading() || resendCooldown() > 0"
              (click)="resend()"
            >
              @if (resendCooldown() > 0) {
                {{ 'registration.otp.resendIn' | translate: { seconds: resendCooldown() } }}
              } @else {
                {{ 'registration.otp.resend' | translate }}
              }
            </ion-button>
          }
          @case ('done') {
            <div class="done">
              <h2 class="done-title">{{ 'registration.done.title' | translate }}</h2>
              @if (maskedLastFour()) {
                <p class="done-sub">
                  {{ 'registration.done.idEnding' | translate: { lastFour: maskedLastFour() } }}
                </p>
              }
              <ion-button routerLink="/login">{{
                'registration.done.continueToLogin' | translate
              }}</ion-button>
            </div>
          }
        }
        @if (step() !== 'done') {
          <p class="prompt">
            <a routerLink="/login">{{ 'registration.backToLogin' | translate }}</a>
          </p>
        }
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: ['../../shared/css/centered-page.scss', '../../shared/css/form.scss'],
  styles: `
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
      color: var(--ink);
    }
    .done-sub {
      margin: 0;
      color: var(--slate-500);
    }
  `,
})
export class RegistrationComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly registration = inject(RegistrationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly notifications = inject(NotificationService);

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
    const problem = IDENTITY_ERRORS.find(({ field, error }) =>
      this.identityForm.controls[field].hasError(error),
    );
    if (problem || this.identityForm.invalid) {
      this.notifications.showError(problem?.key ?? GENERIC_ERROR_KEY);
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
        next: (data) => {
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
        next: (data) => {
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
        next: (data) => {
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
      this.resendCooldown.update((remaining) => {
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
