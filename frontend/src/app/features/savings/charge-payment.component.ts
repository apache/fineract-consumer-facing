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

import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { TranslatePipe } from '@ngx-translate/core';
import { SavingsChargeQueryData } from '@bff/client';
import { OtpComponent } from '../../shared/otp/otp.component';
import { SavingsStore } from './savings.store';

@Component({
  selector: 'app-charge-payment',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    OtpComponent,
    TranslatePipe,
  ],
  template: `
    <div class="charge-payment">
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      @switch (step()) {
        @case ('amount') {
          <form [formGroup]="amountForm" (ngSubmit)="initiate()">
            <mat-form-field appearance="fill">
              <mat-label>{{ 'savings.charge.amountLabel' | translate }}</mat-label>
              <input matInput type="number" step="0.01" formControlName="amount" />
            </mat-form-field>
            <div class="actions">
              <button mat-button type="button" [disabled]="loading()" (click)="cancelled.emit()">
                {{ 'common.action.cancel' | translate }}
              </button>
              <button
                mat-flat-button
                color="primary"
                type="submit"
                [disabled]="loading() || amountForm.invalid"
              >
                {{ 'common.charge.payCta' | translate: { name: charge().name } }}
              </button>
            </div>
          </form>
        }
        @case ('otp') {
          <app-otp
            [sentTo]="store.chargePaymentChallenge()?.sentTo ?? null"
            [loading]="loading()"
            (submitted)="confirm($event)"
            (cancelled)="cancelled.emit()"
          />
        }
        @case ('done') {
          <p class="done">{{ 'common.charge.confirmed' | translate }}</p>
        }
      }
    </div>
  `,
  styles: `
    .charge-payment {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      padding: 0.5rem 0;
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .actions {
      display: flex;
      gap: 0.5rem;
      justify-content: flex-end;
    }
  `,
})
export class ChargePaymentComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(SavingsStore);

  readonly savingsId = input.required<number>();
  readonly charge = input.required<SavingsChargeQueryData>();
  readonly cancelled = output<void>();
  readonly paid = output<void>();

  protected readonly step = signal<'amount' | 'otp' | 'done'>('amount');
  protected readonly loading = signal(false);

  protected readonly amountForm = this.fb.group({
    amount: [0, [Validators.required, Validators.min(0.01)]],
  });

  protected initiate(): void {
    const chargeId = this.charge().id;
    if (this.amountForm.invalid || chargeId == null) {
      return;
    }
    this.loading.set(true);
    this.store
      .initiateChargePayment(this.savingsId(), chargeId, { amount: this.amountForm.controls.amount.value })
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
    const chargeId = this.charge().id;
    const stepUpToken = this.store.chargePaymentChallenge()?.stepUpToken;
    if (chargeId == null || !stepUpToken) {
      return;
    }
    this.loading.set(true);
    this.store
      .confirmChargePayment(this.savingsId(), chargeId, {
        stepUpToken,
        otp,
        amount: this.amountForm.controls.amount.value,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.step.set('done');
          this.loading.set(false);
          this.paid.emit();
        },
        error: () => this.loading.set(false),
      });
  }
}
