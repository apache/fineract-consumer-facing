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
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { TranslatePipe } from '@ngx-translate/core';
import { LoanChargeQueryData } from '@bff/client';
import { OtpComponent } from '../../shared/otp/otp.component';
import { LoansStore } from './loans.store';

@Component({
  selector: 'app-charge-payment',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatProgressBarModule, OtpComponent, TranslatePipe],
  template: `
    <div class="charge-payment">
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      @switch (step()) {
        @case ('initiate') {
          <p>
            {{
              'loans.charge.confirmPrompt'
                | translate: { amount: charge().amountOutstanding, name: charge().name }
            }}
          </p>
          <div class="actions">
            <button mat-button type="button" [disabled]="loading()" (click)="cancelled.emit()">
              {{ 'common.action.cancel' | translate }}
            </button>
            <button mat-flat-button color="primary" type="button" [disabled]="loading()" (click)="initiate()">
              {{ 'common.charge.payCta' | translate: { name: charge().name } }}
            </button>
          </div>
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
    .actions {
      display: flex;
      gap: 0.5rem;
      justify-content: flex-end;
    }
  `,
})
export class ChargePaymentComponent {
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(LoansStore);

  readonly loanId = input.required<number>();
  readonly charge = input.required<LoanChargeQueryData>();
  readonly cancelled = output<void>();
  readonly paid = output<void>();

  protected readonly step = signal<'initiate' | 'otp' | 'done'>('initiate');
  protected readonly loading = signal(false);

  protected initiate(): void {
    const chargeId = this.charge().id;
    if (chargeId == null) {
      return;
    }
    this.loading.set(true);
    this.store
      .initiateChargePayment(this.loanId(), chargeId)
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
      .confirmChargePayment(this.loanId(), chargeId, { stepUpToken, otp })
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
