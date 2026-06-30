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
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../core/auth/auth.service';
import { OtpComponent } from '../../shared/otp/otp.component';

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressBarModule,
    OtpComponent,
    RouterLink,
    TranslatePipe,
  ],
  template: `
    <mat-card class="login-card">
      <mat-card-header>
        <mat-card-title>{{ 'auth.login.title' | translate }}</mat-card-title>
      </mat-card-header>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <mat-card-content>
        @if (step() === 'credentials') {
          <form [formGroup]="credentialsForm" (ngSubmit)="submitCredentials()">
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
                autocomplete="current-password"
              />
            </mat-form-field>
            <button
              mat-flat-button
              color="primary"
              type="submit"
              [disabled]="loading() || credentialsForm.invalid"
            >
              {{ 'common.action.continue' | translate }}
            </button>
          </form>
          <p class="register-prompt">
            {{ 'auth.login.registerPrompt' | translate }}
            <a routerLink="/register">{{ 'auth.login.registerLink' | translate }}</a>
          </p>
        } @else {
          <app-otp
            [sentTo]="sentTo()"
            [loading]="loading()"
            [showCancel]="false"
            (submitted)="submitOtp($event)"
          />
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
    .login-card {
      width: 100%;
      max-width: 24rem;
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .register-prompt {
      margin: 1rem 0 0;
      text-align: center;
    }
  `,
})
export class LoginComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
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
        next: challenge => {
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
        next: () => this.router.navigate(['/summary']),
        error: () => this.loading.set(false),
      });
  }
}
