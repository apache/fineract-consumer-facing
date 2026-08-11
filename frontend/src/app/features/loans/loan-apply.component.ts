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
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { CdkTableModule } from '@angular/cdk/table';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonInput,
  IonProgressBar,
  IonSelect,
  IonSelectOption,
} from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';
import { SubmitLoanApplicationCommandRequest } from '@bff/client';
import { NotificationService } from '../../core/notifications/notification.service';
import { LoansStore } from './loans.store';

const FIELD_ERROR_KEYS = [
  ['productId', 'loans.apply.error.productRequired'],
  ['principal', 'loans.apply.error.principalRequired'],
  ['numberOfRepayments', 'loans.apply.error.numberOfRepaymentsRequired'],
  ['repaymentEvery', 'loans.apply.error.repaymentEveryRequired'],
  ['loanTermFrequency', 'loans.apply.error.loanTermFrequencyRequired'],
  ['interestRatePerPeriod', 'loans.apply.error.interestRateRequired'],
  ['expectedDisbursementDate', 'loans.apply.error.expectedDisbursementDateRequired'],
  ['submittedOnDate', 'loans.apply.error.submittedOnDateRequired'],
] as const;

@Component({
  selector: 'app-loan-apply',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    CdkTableModule,
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardSubtitle,
    IonCardTitle,
    IonInput,
    IonProgressBar,
    IonSelect,
    IonSelectOption,
    TranslatePipe,
  ],
  template: `
    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'loans.apply.title' | translate }}</ion-card-title>
      </ion-card-header>

      @if (loading()) {
        <ion-progress-bar type="indeterminate" />
      }

      <ion-card-content>
        <form [formGroup]="form" class="terms">
          <ion-select
            formControlName="productId"
            fill="outline"
            labelPlacement="stacked"
            interface="popover"
            [label]="'loans.apply.productLabel' | translate"
            (ionChange)="onProductChange($event)"
          >
            @for (option of store.template()?.productOptions ?? []; track option.id) {
              <ion-select-option [value]="option.id">{{ option.name }}</ion-select-option>
            }
          </ion-select>

          <ion-input
            type="number"
            formControlName="principal"
            fill="outline"
            labelPlacement="stacked"
            [label]="'loans.apply.principalLabel' | translate"
          />
          <ion-input
            type="number"
            formControlName="numberOfRepayments"
            fill="outline"
            labelPlacement="stacked"
            [label]="'loans.apply.numberOfRepaymentsLabel' | translate"
          />
          <ion-input
            type="number"
            formControlName="repaymentEvery"
            fill="outline"
            labelPlacement="stacked"
            [label]="'loans.apply.repaymentEveryLabel' | translate"
          />
          <ion-input
            type="number"
            formControlName="loanTermFrequency"
            fill="outline"
            labelPlacement="stacked"
            [label]="'loans.apply.loanTermFrequencyLabel' | translate"
          />
          <ion-input
            type="number"
            step="0.01"
            formControlName="interestRatePerPeriod"
            fill="outline"
            labelPlacement="stacked"
            [label]="'loans.apply.interestRateLabel' | translate"
          />
          <ion-input
            formControlName="expectedDisbursementDate"
            fill="outline"
            labelPlacement="stacked"
            [label]="'loans.apply.expectedDisbursementDateLabel' | translate"
            [placeholder]="'common.placeholder.date' | translate"
          />
          <ion-input
            formControlName="submittedOnDate"
            fill="outline"
            labelPlacement="stacked"
            [label]="'loans.apply.submittedOnDateLabel' | translate"
            [placeholder]="'common.placeholder.date' | translate"
          />
        </form>

        <div class="actions-row">
          <ion-button
            fill="outline"
            class="btn-secondary"
            type="button"
            [disabled]="loading() || form.invalid"
            (click)="preview()"
          >
            {{ 'loans.apply.previewCta' | translate }}
          </ion-button>
          <ion-button type="button" [disabled]="loading()" (click)="submit()">
            {{ 'loans.apply.submitCta' | translate }}
          </ion-button>
        </div>
      </ion-card-content>
    </ion-card>

    @if (store.schedulePreview(); as schedule) {
      <ion-card>
        <ion-card-header>
          <ion-card-title>{{ 'loans.apply.scheduleTitle' | translate }}</ion-card-title>
          <ion-card-subtitle>
            {{
              'loans.apply.totalRepaymentExpected'
                | translate
                  : { amount: schedule.totalRepaymentExpected, currency: schedule.currency }
            }}
          </ion-card-subtitle>
        </ion-card-header>
        <ion-card-content>
          <div class="table-scroll">
            <table cdk-table [dataSource]="schedule.periods ?? []">
              <ng-container cdkColumnDef="period">
                <th cdk-header-cell *cdkHeaderCellDef>
                  {{ 'loans.apply.schedule.period' | translate }}
                </th>
                <td cdk-cell *cdkCellDef="let row">{{ row.period }}</td>
              </ng-container>
              <ng-container cdkColumnDef="dueDate">
                <th cdk-header-cell *cdkHeaderCellDef>
                  {{ 'loans.apply.schedule.dueDate' | translate }}
                </th>
                <td cdk-cell *cdkCellDef="let row">{{ row.dueDate }}</td>
              </ng-container>
              <ng-container cdkColumnDef="principalDue">
                <th cdk-header-cell *cdkHeaderCellDef class="num">
                  {{ 'loans.apply.principalLabel' | translate }}
                </th>
                <td cdk-cell *cdkCellDef="let row" class="num">{{ row.principalDue }}</td>
              </ng-container>
              <ng-container cdkColumnDef="totalDueForPeriod">
                <th cdk-header-cell *cdkHeaderCellDef class="num">
                  {{ 'loans.apply.schedule.totalDue' | translate }}
                </th>
                <td cdk-cell *cdkCellDef="let row" class="num">{{ row.totalDueForPeriod }}</td>
              </ng-container>
              <ng-container cdkColumnDef="outstandingBalance">
                <th cdk-header-cell *cdkHeaderCellDef class="num">
                  {{ 'common.table.balance' | translate }}
                </th>
                <td cdk-cell *cdkCellDef="let row" class="num">{{ row.outstandingBalance }}</td>
              </ng-container>

              <tr cdk-header-row *cdkHeaderRowDef="scheduleColumns"></tr>
              <tr cdk-row *cdkRowDef="let row; columns: scheduleColumns"></tr>
            </table>
          </div>
        </ion-card-content>
      </ion-card>
    }

    @if (store.draft(); as draft) {
      <ion-card>
        <ion-card-header>
          <ion-card-title>{{
            'loans.apply.draftTitle' | translate: { id: draft.loanId }
          }}</ion-card-title>
        </ion-card-header>
        <ion-card-content>
          <p>{{ 'loans.apply.draftHint' | translate }}</p>
          <div class="actions-row">
            <ion-button
              fill="outline"
              class="btn-secondary"
              type="button"
              [disabled]="loading()"
              (click)="modify(draft.loanId)"
            >
              {{ 'loans.apply.modifyCta' | translate }}
            </ion-button>
            <ion-button
              class="btn-danger"
              type="button"
              [disabled]="loading()"
              (click)="withdraw(draft.loanId)"
            >
              {{ 'loans.apply.withdrawCta' | translate }}
            </ion-button>
          </div>
        </ion-card-content>
      </ion-card>
    }
  `,
  styleUrls: ['../../shared/css/table.scss', '../../shared/css/actions.scss'],
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 1rem;
      padding: 1rem;
    }
    ion-card:first-of-type {
      max-width: 48rem;
      width: 100%;
      align-self: center;
    }
    .terms {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 1.25rem 1rem;
    }
    @media (max-width: 40rem) {
      .terms {
        grid-template-columns: 1fr;
      }
    }
    .actions-row {
      margin-top: 1.5rem;
    }
    ion-button.btn-secondary {
      --background: var(--surface);
      --background-hover: var(--page-bg);
      --background-hover-opacity: 1;
      --background-activated: var(--page-bg);
      --background-activated-opacity: 1;
      --border-color: var(--border);
      --border-width: 1px;
      --color: var(--ink);
    }
  `,
})
export class LoanApplyComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly notifications = inject(NotificationService);
  protected readonly store = inject(LoansStore);

  protected readonly loading = signal(false);
  protected readonly scheduleColumns = [
    'period',
    'dueDate',
    'principalDue',
    'totalDueForPeriod',
    'outstandingBalance',
  ];

  protected readonly form = this.fb.group({
    productId: [0, [Validators.required, Validators.min(1)]],
    principal: [0, [Validators.required, Validators.min(0.01)]],
    numberOfRepayments: [0, [Validators.required, Validators.min(1)]],
    repaymentEvery: [1, [Validators.required, Validators.min(1)]],
    loanTermFrequency: [0, [Validators.required, Validators.min(1)]],
    interestRatePerPeriod: [0, [Validators.required, Validators.min(0)]],
    expectedDisbursementDate: ['', Validators.required],
    submittedOnDate: ['', Validators.required],
    loanTermFrequencyType: [2],
    repaymentFrequencyType: [2],
    amortizationType: [1],
    interestType: [0],
    interestCalculationPeriodType: [1],
    transactionProcessingStrategyCode: ['mifos-standard-strategy'],
  });

  private idempotencyKey: string | null = null;

  constructor() {
    this.store.loadTemplate();
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => (this.idempotencyKey = null));
    effect(() => {
      const template = this.store.template();
      if (!template) {
        return;
      }
      this.form.patchValue({
        productId: template.productId ?? this.form.controls.productId.value,
        principal: template.principal ?? this.form.controls.principal.value,
        numberOfRepayments:
          template.numberOfRepayments ?? this.form.controls.numberOfRepayments.value,
        repaymentEvery: template.repaymentEvery ?? this.form.controls.repaymentEvery.value,
        interestRatePerPeriod:
          template.interestRatePerPeriod ?? this.form.controls.interestRatePerPeriod.value,
        loanTermFrequency: (template.numberOfRepayments ?? 0) * (template.repaymentEvery ?? 1),
        loanTermFrequencyType:
          template.repaymentFrequencyTypeId ?? this.form.controls.loanTermFrequencyType.value,
        repaymentFrequencyType:
          template.repaymentFrequencyTypeId ?? this.form.controls.repaymentFrequencyType.value,
        amortizationType: template.amortizationTypeId ?? this.form.controls.amortizationType.value,
        interestType: template.interestTypeId ?? this.form.controls.interestType.value,
        interestCalculationPeriodType:
          template.interestCalculationPeriodTypeId ??
          this.form.controls.interestCalculationPeriodType.value,
        transactionProcessingStrategyCode:
          template.transactionProcessingStrategyCode ??
          this.form.controls.transactionProcessingStrategyCode.value,
      });
    });
  }

  protected onProductChange(event: CustomEvent<{ value?: number | null }>): void {
    const productId = event.detail.value;
    if (productId != null) {
      this.store.loadTemplate(productId);
    }
  }

  protected preview(): void {
    if (this.form.invalid) {
      return;
    }
    this.run(this.store.previewSchedule(this.buildRequest()));
  }

  protected submit(): void {
    if (this.reportFirstFieldError()) {
      return;
    }
    this.idempotencyKey ??= crypto.randomUUID();
    this.run(
      this.store.submit(this.idempotencyKey, this.buildRequest()),
      () => (this.idempotencyKey = null),
    );
  }

  protected modify(loanId: number | undefined): void {
    if (loanId == null) {
      this.notifications.showError('loans.apply.error.draftUnavailable');
      return;
    }
    if (this.reportFirstFieldError()) {
      return;
    }
    this.idempotencyKey ??= crypto.randomUUID();
    this.run(
      this.store.modify(loanId, this.idempotencyKey, this.buildRequest()),
      () => (this.idempotencyKey = null),
    );
  }

  protected withdraw(loanId: number | undefined): void {
    if (loanId == null) {
      return;
    }
    this.idempotencyKey ??= crypto.randomUUID();
    this.run(
      this.store.withdraw(loanId, this.idempotencyKey, {
        withdrawnOnDate: this.form.controls.submittedOnDate.value,
      }),
      () => (this.idempotencyKey = null),
    );
  }

  private reportFirstFieldError(): boolean {
    for (const [name, key] of FIELD_ERROR_KEYS) {
      if (this.form.controls[name].invalid) {
        this.notifications.showError(key);
        return true;
      }
    }
    return false;
  }

  private buildRequest(): SubmitLoanApplicationCommandRequest {
    return this.form.getRawValue();
  }

  private run(work: Observable<unknown>, onSuccess?: () => void): void {
    this.loading.set(true);
    work.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.loading.set(false);
        onSuccess?.();
      },
      error: () => this.loading.set(false),
    });
  }
}
