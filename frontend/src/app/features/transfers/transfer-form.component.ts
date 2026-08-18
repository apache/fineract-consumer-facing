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
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonIcon,
  IonInput,
  IonProgressBar,
  IonRouterLink,
  IonSelect,
  IonSelectOption,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { arrowBack, swapHorizontal } from 'ionicons/icons';
import { TranslatePipe } from '@ngx-translate/core';
import { OtpComponent } from '../../shared/otp/otp.component';
import { BeneficiariesStore } from '../beneficiaries/beneficiaries.store';
import { NotificationService } from '../../core/notifications/notification.service';
import { SavingsStore } from '../savings/savings.store';
import { TransfersStore } from './transfers.store';

const MIN_AMOUNT = 0.01;

interface ToOption {
  key: string;
  label: string;
}

interface ToGroup {
  labelKey: string;
  options: ToOption[];
}

@Component({
  selector: 'app-transfer-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardTitle,
    IonIcon,
    IonInput,
    IonProgressBar,
    IonRouterLink,
    IonSelect,
    IonSelectOption,
    DecimalPipe,
    TranslatePipe,
    OtpComponent,
  ],
  template: `
    <ion-card class="transfer-card">
      <ion-card-header>
        <ion-card-title>{{ 'transfers.title' | translate }}</ion-card-title>
      </ion-card-header>

      @if (loading()) {
        <ion-progress-bar type="indeterminate" />
      }

      <ion-card-content>
        @switch (step()) {
          @case ('form') {
            <form [formGroup]="form" (ngSubmit)="initiate()">
              <ion-select
                formControlName="fromAccountId"
                interface="popover"
                fill="outline"
                labelPlacement="stacked"
                [label]="'transfers.form.fromAccountLabel' | translate"
              >
                @for (account of savingsStore.accounts(); track account.id) {
                  <ion-select-option [value]="account.id">{{
                    accountLabel(account)
                  }}</ion-select-option>
                }
              </ion-select>
              <ion-select
                formControlName="toDestination"
                interface="popover"
                fill="outline"
                labelPlacement="stacked"
                [label]="'transfers.form.toAccountLabel' | translate"
              >
                @for (group of toGroups(); track group.labelKey) {
                  @if (group.options.length > 0) {
                    <ion-select-option class="group-header" [disabled]="true">{{
                      group.labelKey | translate
                    }}</ion-select-option>
                    @for (option of group.options; track option.key) {
                      <ion-select-option [value]="option.key">{{ option.label }}</ion-select-option>
                    }
                  }
                }
              </ion-select>
              <ion-input
                type="number"
                step="0.01"
                formControlName="amount"
                fill="outline"
                labelPlacement="stacked"
                [label]="'transfers.form.amountLabel' | translate"
              />
              <div class="actions-end">
                <ion-button type="submit" [disabled]="loading()">
                  <ion-icon slot="start" name="swap-horizontal" />
                  {{ 'transfers.form.submitCta' | translate }}
                </ion-button>
              </div>
            </form>
          }
          @case ('otp') {
            <app-otp
              [sentTo]="store.challenge()?.sentTo ?? null"
              [loading]="loading()"
              (submitted)="confirm($event)"
              (cancelled)="backToForm()"
            />
          }
          @case ('done') {
            <div class="done">
              <p>{{ 'transfers.done.complete' | translate }}</p>
              @if (store.result(); as result) {
                <dl>
                  <dt>{{ 'transfers.done.reference' | translate }}</dt>
                  <dd>{{ result.transferId }}</dd>
                  <dt>{{ 'common.filter.from' | translate }}</dt>
                  <dd>{{ result.fromAccountId }}</dd>
                  <dt>{{ 'common.filter.to' | translate }}</dt>
                  <dd>{{ result.toAccountId }}</dd>
                  <dt>{{ 'transfers.done.amount' | translate }}</dt>
                  <dd>{{ result.amount | number: '1.2-2' }}</dd>
                </dl>
              }
            </div>
            <div class="actions-end">
              <ion-button fill="outline" routerLink="/transfers">
                <ion-icon slot="start" name="arrow-back" aria-hidden="true" />
                {{ 'transfers.backCta' | translate }}
              </ion-button>
            </div>
          }
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
    .transfer-card {
      width: 100%;
      max-width: 28rem;
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
export class TransferFormComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly notifications = inject(NotificationService);
  protected readonly store = inject(TransfersStore);
  protected readonly savingsStore = inject(SavingsStore);
  private readonly beneficiariesStore = inject(BeneficiariesStore);

  protected readonly step = signal<'form' | 'otp' | 'done'>('form');
  protected readonly loading = signal(false);

  protected readonly form = this.fb.group({
    fromAccountId: [null as number | null],
    toDestination: [''],
    amount: [null as number | null],
  });

  private readonly fromAccountId = toSignal(this.form.controls.fromAccountId.valueChanges, {
    initialValue: null,
  });

  protected readonly toGroups = computed<ToGroup[]>(() => {
    const sourceId = this.fromAccountId();
    return [
      {
        labelKey: 'transfers.form.toGroup.mySavings',
        options: this.savingsStore
          .accounts()
          .filter((account) => account.id !== sourceId)
          .map((account) => ({ key: `SAVINGS:${account.id}`, label: this.accountLabel(account) })),
      },
      {
        labelKey: 'transfers.form.toGroup.beneficiaries',
        options: this.beneficiariesStore
          .beneficiaries()
          .filter(
            (beneficiary) =>
              beneficiary.fineractAccountId != null && beneficiary.fineractAccountId !== sourceId,
          )
          .map((beneficiary) => ({
            key: `SAVINGS:${beneficiary.fineractAccountId}`,
            label: beneficiary.name ?? '',
          })),
      },
    ];
  });

  private idempotencyKey: string | null = null;

  constructor() {
    addIcons({ arrowBack, swapHorizontal });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => (this.idempotencyKey = null));
    this.form.controls.fromAccountId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((fromAccountId) => this.clearDestinationIfSource(fromAccountId));
    this.savingsStore.loadAccounts();
    this.beneficiariesStore.load().pipe(takeUntilDestroyed()).subscribe();
  }

  private clearDestinationIfSource(fromAccountId: number | null): void {
    if (this.form.controls.toDestination.value === `SAVINGS:${fromAccountId}`) {
      this.form.controls.toDestination.setValue('');
    }
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
    const { fromAccountId, toDestination, amount } = this.form.getRawValue();
    if (fromAccountId == null) {
      this.notifications.showError('transfers.form.error.selectFromAccount');
      return;
    }
    if (!toDestination) {
      this.notifications.showError('transfers.form.error.selectToAccount');
      return;
    }
    if (amount == null || amount < MIN_AMOUNT) {
      this.notifications.showError('transfers.form.error.amountRequired');
      return;
    }
    const { toAccountId, toAccountType } = this.destination(toDestination);
    this.loading.set(true);
    this.store
      .initiate({ fromAccountId, toAccountId, toAccountType, amount })
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
    const stepUpToken = this.store.challenge()?.stepUpToken;
    const { fromAccountId, toDestination, amount } = this.form.getRawValue();
    if (!stepUpToken || fromAccountId == null || !toDestination || amount == null) {
      return;
    }
    const { toAccountId, toAccountType } = this.destination(toDestination);
    this.loading.set(true);
    this.idempotencyKey ??= crypto.randomUUID();
    this.store
      .confirm(this.idempotencyKey, {
        stepUpToken,
        otp,
        fromAccountId,
        toAccountId,
        toAccountType,
        amount,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.idempotencyKey = null;
          this.step.set('done');
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected backToForm(): void {
    this.step.set('form');
  }

  private destination(key: string): { toAccountId: number; toAccountType: string } {
    const [toAccountType, id] = key.split(':');
    return { toAccountType, toAccountId: Number(id) };
  }
}
