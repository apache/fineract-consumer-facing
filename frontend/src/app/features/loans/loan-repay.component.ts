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

import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  signal,
} from '@angular/core';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonIcon,
  IonInput,
  IonProgressBar,
  IonSelect,
  IonSelectOption,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { arrowBack, cash } from 'ionicons/icons';
import { TranslatePipe } from '@ngx-translate/core';
import { NotificationService } from '../../core/notifications/notification.service';
import { OtpComponent } from '../../shared/otp/otp.component';
import { SavingsStore } from '../savings/savings.store';
import { TransfersStore } from '../transfers/transfers.store';
import { LoansStore } from './loans.store';

const LOAN_ACCOUNT_TYPE = 'LOAN';
const MIN_AMOUNT = 0.01;

@Component({
  selector: 'app-loan-repay',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardSubtitle,
    IonCardTitle,
    IonIcon,
    IonInput,
    IonProgressBar,
    IonSelect,
    IonSelectOption,
    CurrencyPipe,
    DecimalPipe,
    TranslatePipe,
    OtpComponent,
  ],
  template: `
    <ion-card class="repay-card">
      <ion-card-header>
        <ion-card-title>{{ 'loans.repay.title' | translate }}</ion-card-title>
        @if (loansStore.selected(); as loan) {
          <ion-card-subtitle>{{ loan.productName }} — {{ loan.accountNo }}</ion-card-subtitle>
        }
      </ion-card-header>

      @if (loading()) {
        <ion-progress-bar type="indeterminate" />
      }

      <ion-card-content>
        @switch (step()) {
          @case ('form') {
            <!-- Non-active loans redirect away (see constructor); render nothing meanwhile. -->
            @if (activeLoan(); as loan) {
              <p class="outstanding">
                {{ 'loans.detail.totalOutstandingLabel' | translate }}
                <span class="amount">{{ loan.totalOutstanding | currency: loan.currency }}</span>
              </p>
              <form [formGroup]="form" (ngSubmit)="initiate()">
                <ion-select
                  formControlName="fromAccountId"
                  interface="popover"
                  fill="outline"
                  labelPlacement="stacked"
                  [label]="'loans.repay.fromAccountLabel' | translate"
                >
                  @for (account of savingsStore.accounts(); track account.id) {
                    <ion-select-option [value]="account.id">{{
                      accountLabel(account)
                    }}</ion-select-option>
                  }
                </ion-select>
                <ion-input
                  type="number"
                  step="0.01"
                  [max]="loan.totalOutstanding"
                  formControlName="amount"
                  fill="outline"
                  labelPlacement="stacked"
                  [label]="'loans.repay.amountLabel' | translate"
                />
                <div class="actions-end">
                  <ion-button type="submit" [disabled]="loading()">
                    <ion-icon slot="start" name="cash" aria-hidden="true" />
                    {{ 'loans.repay.submitCta' | translate }}
                  </ion-button>
                </div>
              </form>
            }
          }
          @case ('otp') {
            <app-otp
              [sentTo]="transfersStore.challenge()?.sentTo ?? null"
              [loading]="loading()"
              (submitted)="confirm($event)"
              (cancelled)="backToForm()"
            />
          }
          @case ('done') {
            <div class="done">
              <p>{{ 'loans.repay.done.complete' | translate }}</p>
              @if (transfersStore.result(); as result) {
                <dl>
                  <dt>{{ 'loans.repay.done.reference' | translate }}</dt>
                  <dd>{{ result.transferId }}</dd>
                  <dt>{{ 'loans.repay.done.amount' | translate }}</dt>
                  <dd>{{ result.amount | number: '1.2-2' }}</dd>
                </dl>
              }
            </div>
          }
        }

        @if (step() !== 'otp') {
          <div class="actions-end">
            <ion-button fill="outline" [routerLink]="['/loans', loanId]">
              <ion-icon slot="start" name="arrow-back" aria-hidden="true" />
              {{ 'loans.transaction.backToLoan' | translate }}
            </ion-button>
          </div>
        }
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: [
    '../../shared/css/centered-page.scss',
    '../../shared/css/form.scss',
    '../../shared/css/actions.scss',
  ],
  styles: `
    :host {
      flex: 1;
      min-height: 0;
      flex-direction: column;
      gap: 1rem;
    }
    .repay-card {
      width: 100%;
      max-width: 28rem;
    }
    .outstanding {
      margin: 0 0 1rem;
    }
    .outstanding .amount {
      font-weight: 600;
      color: var(--ink);
    }
    dl {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 0.25rem 1rem;
    }
    dt {
      font-weight: 600;
    }
    dd {
      margin: 0;
    }
    form {
      gap: 0.875rem;
    }
  `,
})
export class LoanRepayComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly notifications = inject(NotificationService);
  protected readonly transfersStore = inject(TransfersStore);
  protected readonly loansStore = inject(LoansStore);
  protected readonly savingsStore = inject(SavingsStore);

  protected readonly loanId = Number(this.route.snapshot.paramMap.get('loanId'));
  protected readonly step = signal<'form' | 'otp' | 'done'>('form');
  protected readonly loading = signal(false);

  protected readonly activeLoan = computed(() => {
    const loan = this.loansStore.selected();
    return loan?.active === true ? loan : null;
  });

  protected readonly form = this.fb.group({
    fromAccountId: [null as number | null],
    amount: [null as number | null],
  });

  private idempotencyKey: string | null = null;
  private redirected = false;

  constructor() {
    addIcons({ arrowBack, cash });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => (this.idempotencyKey = null));

    effect(() => {
      const loan = this.loansStore.selected();
      if (loan && loan.active !== true) {
        this.bounce();
      }
    });

    this.savingsStore.loadAccounts();
    this.loansStore.loadLoan(this.loanId);
  }

  private bounce(): void {
    if (this.redirected) {
      return;
    }
    this.redirected = true;
    this.notifications.showError('loans.repay.error.notActive');
    void this.router.navigate(['/loans', this.loanId]);
  }

  protected accountLabel(account: {
    accountNo?: string;
    productName?: string;
    id?: number;
  }): string {
    return (
      [account.productName, account.accountNo].filter(Boolean).join(' · ') || String(account.id)
    );
  }

  protected initiate(): void {
    const { fromAccountId, amount } = this.form.getRawValue();
    if (fromAccountId == null) {
      this.notifications.showError('loans.repay.error.selectAccount');
      return;
    }
    if (amount == null || amount < MIN_AMOUNT) {
      this.notifications.showError('loans.repay.error.amountRequired');
      return;
    }
    const outstanding = this.activeLoan()?.totalOutstanding;
    if (outstanding != null && amount > outstanding) {
      this.notifications.showError('loans.repay.error.exceedsOutstanding');
      return;
    }
    this.loading.set(true);
    this.transfersStore
      .initiate({
        fromAccountId,
        toAccountId: this.loanId,
        toAccountType: LOAN_ACCOUNT_TYPE,
        amount,
      })
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
    const stepUpToken = this.transfersStore.challenge()?.stepUpToken;
    const { fromAccountId, amount } = this.form.getRawValue();
    if (!stepUpToken || fromAccountId == null || amount == null) {
      return;
    }
    this.loading.set(true);
    this.idempotencyKey ??= crypto.randomUUID();
    this.transfersStore
      .confirm(this.idempotencyKey, {
        stepUpToken,
        otp,
        fromAccountId,
        toAccountId: this.loanId,
        toAccountType: LOAN_ACCOUNT_TYPE,
        amount,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.idempotencyKey = null;
          this.step.set('done');
          this.loading.set(false);
          this.loansStore.loadLoan(this.loanId);
          this.loansStore.loadTransactions(this.loanId, { page: 0, size: 20 });
        },
        error: () => this.loading.set(false),
      });
  }

  protected backToForm(): void {
    this.step.set('form');
  }
}
