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
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonIcon,
  IonInput,
  IonProgressBar,
  IonSelect,
  IonSelectOption,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { swapHorizontal } from 'ionicons/icons';
import { TranslatePipe } from '@ngx-translate/core';
import { OtpComponent } from '../../shared/otp/otp.component';
import { TransfersStore } from './transfers.store';

@Component({
  selector: 'app-transfer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardTitle,
    IonIcon,
    IonInput,
    IonProgressBar,
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
              <ion-input
                type="number"
                formControlName="fromAccountId"
                fill="outline"
                labelPlacement="stacked"
                [label]="'transfers.form.fromAccountLabel' | translate"
              />
              <ion-input
                type="number"
                formControlName="toAccountId"
                fill="outline"
                labelPlacement="stacked"
                [label]="'transfers.form.toAccountLabel' | translate"
              />
              <ion-select
                formControlName="toAccountType"
                interface="popover"
                fill="outline"
                labelPlacement="stacked"
                [label]="'transfers.form.toAccountTypeLabel' | translate"
              >
                @for (type of accountTypes; track type.value) {
                  <ion-select-option [value]="type.value">{{ type.labelKey | translate }}</ion-select-option>
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
                <ion-button type="submit" [disabled]="loading() || form.invalid">
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
export class TransferComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(TransfersStore);

  protected readonly step = signal<'form' | 'otp' | 'done'>('form');
  protected readonly loading = signal(false);

  protected readonly accountTypes = [
    { value: 'SAVINGS', labelKey: 'transfers.accountType.savings' },
    { value: 'LOAN', labelKey: 'transfers.accountType.loan' },
  ];

  protected readonly form = this.fb.group({
    fromAccountId: [null as number | null, [Validators.required]],
    toAccountId: [null as number | null, [Validators.required]],
    toAccountType: ['SAVINGS', [Validators.required]],
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
  });

  constructor() {
    addIcons({ swapHorizontal });
  }

  protected initiate(): void {
    const { fromAccountId, toAccountId, toAccountType, amount } = this.form.getRawValue();
    if (this.form.invalid || fromAccountId == null || toAccountId == null || amount == null) {
      return;
    }
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
    const { fromAccountId, toAccountId, toAccountType, amount } = this.form.getRawValue();
    if (!stepUpToken || fromAccountId == null || toAccountId == null || amount == null) {
      return;
    }
    this.loading.set(true);
    this.store
      .confirm({ stepUpToken, otp, fromAccountId, toAccountId, toAccountType, amount })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.step.set('done');
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected backToForm(): void {
    this.step.set('form');
  }
}
