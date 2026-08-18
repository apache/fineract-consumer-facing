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
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonInput,
  IonProgressBar,
} from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { OtpComponent } from '../../shared/otp/otp.component';

export function allowedReturnUrl(returnUrl: string | null): string | null {
  return returnUrl !== null && returnUrl.startsWith('/api/v1/oauth2/') ? returnUrl : null;
}

@Component({
  selector: 'app-login',
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
    OtpComponent,
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
        <ion-card-title>{{ 'auth.login.title' | translate }}</ion-card-title>
      </ion-card-header>

      @if (loading()) {
        <ion-progress-bar type="indeterminate" />
      }

      <ion-card-content>
        @if (step() === 'credentials') {
          <form [formGroup]="credentialsForm" (ngSubmit)="submitCredentials()">
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
              autocomplete="current-password"
            />
            <ion-button type="submit" [disabled]="loading() || credentialsForm.invalid">
              {{ 'common.action.continue' | translate }}
            </ion-button>
          </form>
          <p class="prompt">
            {{ 'auth.login.registerPrompt' | translate }}
            <a routerLink="/register">{{ 'auth.login.registerLink' | translate }}</a>
          </p>
          <p class="prompt">
            <a routerLink="/forgot-password">{{ 'auth.login.forgotPasswordLink' | translate }}</a>
          </p>
        } @else {
          <app-otp
            [sentTo]="sentTo()"
            [loading]="loading()"
            [showCancel]="false"
            (submitted)="submitOtp($event)"
          />
        }
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: ['../../shared/css/centered-page.scss', '../../shared/css/form.scss'],
})
export class LoginComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly step = signal<'credentials' | 'otp'>('credentials');
  protected readonly loading = signal(false);
  protected readonly sentTo = signal<string | null>(null);
  private challengeToken: string | null = null;

  protected readonly credentialsForm = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  protected submitCredentials(): void {
    if (this.credentialsForm.invalid) {
      return;
    }
    this.loading.set(true);
    this.auth
      .login(this.credentialsForm.getRawValue())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (challenge) => {
          this.challengeToken = challenge.challengeToken ?? null;
          this.sentTo.set(challenge.sentTo ?? null);
          this.step.set('otp');
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected submitOtp(code: string): void {
    if (!this.challengeToken) {
      return;
    }
    this.loading.set(true);
    this.auth
      .verifyTwoFactor({
        challengeToken: this.challengeToken,
        token: code,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.navigateAfterLogin(),
        error: () => this.loading.set(false),
      });
  }

  private navigateAfterLogin(): void {
    const returnUrl = allowedReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
    if (returnUrl) {
      window.location.assign(returnUrl);
    } else {
      this.router.navigate(['/summary']);
    }
  }
}
