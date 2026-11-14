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
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonDatetime,
  IonDatetimeButton,
  IonIcon,
  IonInput,
  IonModal,
  IonProgressBar,
  IonRouterLink,
  IonSelect,
  IonSelectOption,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { arrowBack, calendar } from 'ionicons/icons';
import {
  SCHEDULED_TRANSFER_FREQUENCIES,
  ScheduledTransferFrequency,
  TranslatePipe,
} from '../../core/adapters';
import { NotificationService } from '../../core/notifications/notification.service';
import { OtpComponent } from '../../shared/otp/otp.component';
import { toIsoDate } from '../../shared/utils/date';
import { BeneficiariesStore } from '../beneficiaries/beneficiaries.store';
import { SavingsStore } from '../savings/savings.store';
import { accountLabel, accountLabels, labelFor } from './account-labels';
import { ScheduledTransfersStore } from './scheduled-transfers.store';

const MIN_AMOUNT = 0.01;

interface ToOption {
  key: string;
  label: string;
}

interface ToGroup {
  labelKey: string;
  options: ToOption[];
}

/** Tomorrow in the browser's calendar, as `YYYY-MM-DD`. A starting suggestion only. */
function tomorrow(): string {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

@Component({
  selector: 'app-scheduled-transfer-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardTitle,
    IonDatetime,
    IonDatetimeButton,
    IonIcon,
    IonInput,
    IonModal,
    IonProgressBar,
    IonRouterLink,
    IonSelect,
    IonSelectOption,
    DatePipe,
    DecimalPipe,
    TranslatePipe,
    OtpComponent,
  ],
  template: `
    <ion-card class="form-card">
      <ion-card-header>
        <ion-card-title>{{ 'scheduledTransfers.form.title' | appTranslate }}</ion-card-title>
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
                [label]="'transfers.form.fromAccountLabel' | appTranslate"
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
                [label]="'transfers.form.toAccountLabel' | appTranslate"
              >
                @for (group of toGroups(); track group.labelKey) {
                  @if (group.options.length > 0) {
                    <ion-select-option class="group-header" [disabled]="true">{{
                      group.labelKey | appTranslate
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
                [label]="'scheduledTransfers.form.amountLabel' | appTranslate"
              />
              <ion-select
                formControlName="frequency"
                interface="popover"
                fill="outline"
                labelPlacement="stacked"
                [label]="'scheduledTransfers.frequency.label' | appTranslate"
              >
                @for (frequency of frequencies; track frequency) {
                  <ion-select-option [value]="frequency">{{
                    'scheduledTransfers.frequency.' + frequency | appTranslate
                  }}</ion-select-option>
                }
              </ion-select>
              <div class="field">
                <span class="field-label">{{ 'scheduledTransfers.startDate' | appTranslate }}</span>
                <ion-datetime-button datetime="scheduledTransferStartDate">
                  <span slot="date-target">
                    @if (startDate(); as selected) {
                      {{ selected | date: 'mediumDate' }}
                    } @else {
                      {{ 'common.filter.date' | appTranslate }}
                    }
                  </span>
                </ion-datetime-button>
                <ion-modal [keepContentsMounted]="true">
                  <ng-template>
                    <ion-datetime
                      id="scheduledTransferStartDate"
                      presentation="date"
                      formControlName="startDate"
                      [showDefaultButtons]="true"
                    />
                  </ng-template>
                </ion-modal>
              </div>
              <div class="field">
                <span class="field-label">{{
                  'scheduledTransfers.form.endDateLabel' | appTranslate
                }}</span>
                <!-- Ionic renders today when the value is null; own the label so "none" reads as such -->
                <ion-datetime-button datetime="scheduledTransferEndDate">
                  <span slot="date-target">
                    @if (endDate(); as selected) {
                      {{ selected | date: 'mediumDate' }}
                    } @else {
                      {{ 'scheduledTransfers.noEndDate' | appTranslate }}
                    }
                  </span>
                </ion-datetime-button>
                <ion-modal [keepContentsMounted]="true">
                  <ng-template>
                    <ion-datetime
                      id="scheduledTransferEndDate"
                      presentation="date"
                      formControlName="endDate"
                      [showDefaultButtons]="true"
                      [showClearButton]="true"
                    />
                  </ng-template>
                </ion-modal>
              </div>
              <div class="actions-end">
                <ion-button type="submit" [disabled]="loading()">
                  <ion-icon slot="start" name="calendar" aria-hidden="true" />
                  {{ 'scheduledTransfers.form.submitCta' | appTranslate }}
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
              <p>{{ 'scheduledTransfers.form.done' | appTranslate }}</p>
              @if (submitted(); as summary) {
                <dl>
                  <dt>{{ 'common.filter.from' | appTranslate }}</dt>
                  <dd>{{ label(summary.fromAccountId) }}</dd>
                  <dt>{{ 'common.filter.to' | appTranslate }}</dt>
                  <dd>{{ label(summary.toAccountId) }}</dd>
                  <dt>{{ 'scheduledTransfers.form.amountLabel' | appTranslate }}</dt>
                  <dd>{{ summary.amount | number: '1.2-2' }}</dd>
                  <dt>{{ 'scheduledTransfers.frequency.label' | appTranslate }}</dt>
                  <dd>{{ 'scheduledTransfers.frequency.' + summary.frequency | appTranslate }}</dd>
                  <dt>{{ 'scheduledTransfers.startDate' | appTranslate }}</dt>
                  <dd>{{ summary.startDate | date: 'mediumDate' }}</dd>
                  <dt>{{ 'scheduledTransfers.endDate' | appTranslate }}</dt>
                  <dd>
                    @if (summary.endDate) {
                      {{ summary.endDate | date: 'mediumDate' }}
                    } @else {
                      {{ 'scheduledTransfers.noEndDate' | appTranslate }}
                    }
                  </dd>
                </dl>
              }
            </div>
            <div class="actions-end">
              <ion-button fill="outline" routerLink="/transfers/scheduled">
                <ion-icon slot="start" name="arrow-back" aria-hidden="true" />
                {{ 'scheduledTransfers.backCta' | appTranslate }}
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
    .form-card {
      width: 100%;
      max-width: 28rem;
    }
    .field {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
    }
    .field-label {
      font-size: var(--text-sm);
      color: var(--slate-500);
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
export class ScheduledTransferFormComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly notifications = inject(NotificationService);
  protected readonly store = inject(ScheduledTransfersStore);
  protected readonly savingsStore = inject(SavingsStore);
  private readonly beneficiariesStore = inject(BeneficiariesStore);

  protected readonly step = signal<'form' | 'otp' | 'done'>('form');
  protected readonly loading = signal(false);
  protected readonly frequencies = SCHEDULED_TRANSFER_FREQUENCIES;
  protected readonly accountLabel = accountLabel;

  protected readonly form = this.fb.group({
    fromAccountId: [null as number | null],
    toDestination: [''],
    amount: [null as number | null],
    frequency: ['MONTHLY' as ScheduledTransferFrequency],
    startDate: this.fb.control<string | null>(tomorrow()),
    endDate: this.fb.control<string | null>(null),
  });

  /** What was confirmed, for the "done" summary. */
  protected readonly submitted = signal<{
    fromAccountId: number;
    toAccountId: number;
    amount: number;
    frequency: ScheduledTransferFrequency;
    startDate: string;
    endDate?: string;
  } | null>(null);

  private readonly fromAccountId = toSignal(this.form.controls.fromAccountId.valueChanges, {
    initialValue: null,
  });
  protected readonly startDate = toSignal(this.form.controls.startDate.valueChanges, {
    initialValue: this.form.controls.startDate.value,
  });
  protected readonly endDate = toSignal(this.form.controls.endDate.valueChanges, {
    initialValue: this.form.controls.endDate.value,
  });

  private readonly labels = computed(() =>
    accountLabels(this.savingsStore.accounts(), this.beneficiariesStore.beneficiaries()),
  );

  protected readonly toGroups = computed<ToGroup[]>(() => {
    const sourceId = this.fromAccountId();
    return [
      {
        labelKey: 'transfers.form.toGroup.mySavings',
        options: this.savingsStore
          .accounts()
          .filter((account) => account.id !== sourceId)
          .map((account) => ({ key: String(account.id), label: accountLabel(account) })),
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
            key: String(beneficiary.fineractAccountId),
            label: beneficiary.name ?? '',
          })),
      },
    ];
  });

  private idempotencyKey: string | null = null;

  constructor() {
    addIcons({ arrowBack, calendar });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => (this.idempotencyKey = null));
    this.form.controls.fromAccountId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((fromAccountId) => this.clearDestinationIfSource(fromAccountId));
    this.savingsStore.loadAccounts();
    this.beneficiariesStore.load().pipe(takeUntilDestroyed()).subscribe();
  }

  protected label(accountId: number): string {
    return labelFor(this.labels(), accountId);
  }

  protected initiate(): void {
    const request = this.request();
    if (!request) {
      return;
    }
    this.loading.set(true);
    this.store
      .initiate(request)
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
    const request = this.request();
    if (!stepUpToken || !request) {
      return;
    }
    this.loading.set(true);
    this.idempotencyKey ??= crypto.randomUUID();
    this.store
      .confirm(this.idempotencyKey, { stepUpToken, otp, ...request })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.idempotencyKey = null;
          this.submitted.set(request);
          this.step.set('done');
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected backToForm(): void {
    this.step.set('form');
  }

  /** The initiate/confirm body, or null after toasting what is missing. */
  private request() {
    const { fromAccountId, toDestination, amount, frequency, startDate, endDate } =
      this.form.getRawValue();
    if (fromAccountId == null) {
      this.notifications.showError('transfers.form.error.selectFromAccount');
      return null;
    }
    if (!toDestination) {
      this.notifications.showError('transfers.form.error.selectToAccount');
      return null;
    }
    if (amount == null || amount < MIN_AMOUNT) {
      this.notifications.showError('transfers.form.error.amountRequired');
      return null;
    }
    const start = toIsoDate(startDate);
    if (!start) {
      this.notifications.showError('scheduledTransfers.form.error.startDateRequired');
      return null;
    }
    return {
      fromAccountId,
      toAccountId: Number(toDestination),
      amount,
      frequency,
      startDate: start,
      endDate: toIsoDate(endDate),
    };
  }

  private clearDestinationIfSource(fromAccountId: number | null): void {
    if (this.form.controls.toDestination.value === String(fromAccountId)) {
      this.form.controls.toDestination.setValue('');
    }
  }
}
