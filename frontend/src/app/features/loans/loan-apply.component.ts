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

import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { TranslatePipe } from '@ngx-translate/core';
import { SubmitLoanApplicationCommandRequest } from '@bff/client';
import { LoansStore } from './loans.store';

@Component({
  selector: 'app-loan-apply',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    TranslatePipe,
  ],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title>{{ 'loans.apply.title' | translate }}</mat-card-title>
      </mat-card-header>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <mat-card-content>
        <form [formGroup]="form" class="terms">
          <mat-form-field appearance="fill">
            <mat-label>{{ 'loans.apply.productLabel' | translate }}</mat-label>
            <mat-select formControlName="productId" (selectionChange)="onProductChange($event.value)">
              @for (option of store.template()?.productOptions ?? []; track option.id) {
                <mat-option [value]="option.id">{{ option.name }}</mat-option>
              }
            </mat-select>
          </mat-form-field>

          <mat-form-field appearance="fill">
            <mat-label>{{ 'loans.apply.principalLabel' | translate }}</mat-label>
            <input matInput type="number" formControlName="principal" />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>{{ 'loans.apply.numberOfRepaymentsLabel' | translate }}</mat-label>
            <input matInput type="number" formControlName="numberOfRepayments" />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>{{ 'loans.apply.repaymentEveryLabel' | translate }}</mat-label>
            <input matInput type="number" formControlName="repaymentEvery" />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>{{ 'loans.apply.loanTermFrequencyLabel' | translate }}</mat-label>
            <input matInput type="number" formControlName="loanTermFrequency" />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>{{ 'loans.apply.interestRateLabel' | translate }}</mat-label>
            <input matInput type="number" step="0.01" formControlName="interestRatePerPeriod" />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>{{ 'loans.apply.expectedDisbursementDateLabel' | translate }}</mat-label>
            <input matInput [placeholder]="'common.placeholder.date' | translate" formControlName="expectedDisbursementDate" />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>{{ 'loans.apply.submittedOnDateLabel' | translate }}</mat-label>
            <input matInput [placeholder]="'common.placeholder.date' | translate" formControlName="submittedOnDate" />
          </mat-form-field>
        </form>

        <div class="actions">
          <button mat-stroked-button type="button" [disabled]="loading() || form.invalid" (click)="preview()">
            {{ 'loans.apply.previewCta' | translate }}
          </button>
          <button mat-flat-button color="primary" type="button" [disabled]="loading() || form.invalid" (click)="submit()">
            {{ 'loans.apply.submitCta' | translate }}
          </button>
        </div>
      </mat-card-content>
    </mat-card>

    @if (store.schedulePreview(); as schedule) {
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ 'loans.apply.scheduleTitle' | translate }}</mat-card-title>
          <mat-card-subtitle>
            {{
              'loans.apply.totalRepaymentExpected'
                | translate: { amount: schedule.totalRepaymentExpected, currency: schedule.currency }
            }}
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <table mat-table [dataSource]="schedule.periods ?? []">
            <ng-container matColumnDef="period">
              <th mat-header-cell *matHeaderCellDef>{{ 'loans.apply.schedule.period' | translate }}</th>
              <td mat-cell *matCellDef="let row">{{ row.period }}</td>
            </ng-container>
            <ng-container matColumnDef="dueDate">
              <th mat-header-cell *matHeaderCellDef>{{ 'loans.apply.schedule.dueDate' | translate }}</th>
              <td mat-cell *matCellDef="let row">{{ row.dueDate }}</td>
            </ng-container>
            <ng-container matColumnDef="principalDue">
              <th mat-header-cell *matHeaderCellDef>{{ 'loans.apply.principalLabel' | translate }}</th>
              <td mat-cell *matCellDef="let row">{{ row.principalDue }}</td>
            </ng-container>
            <ng-container matColumnDef="totalDueForPeriod">
              <th mat-header-cell *matHeaderCellDef>{{ 'loans.apply.schedule.totalDue' | translate }}</th>
              <td mat-cell *matCellDef="let row">{{ row.totalDueForPeriod }}</td>
            </ng-container>
            <ng-container matColumnDef="outstandingBalance">
              <th mat-header-cell *matHeaderCellDef>{{ 'common.table.balance' | translate }}</th>
              <td mat-cell *matCellDef="let row">{{ row.outstandingBalance }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="scheduleColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: scheduleColumns"></tr>
          </table>
        </mat-card-content>
      </mat-card>
    }

    @if (store.draft(); as draft) {
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ 'loans.apply.draftTitle' | translate: { id: draft.loanId } }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <p>{{ 'loans.apply.draftHint' | translate }}</p>
          <div class="actions">
            <button mat-stroked-button type="button" [disabled]="loading() || form.invalid" (click)="modify(draft.loanId)">
              {{ 'loans.apply.modifyCta' | translate }}
            </button>
            <button mat-stroked-button color="warn" type="button" [disabled]="loading()" (click)="withdraw(draft.loanId)">
              {{ 'loans.apply.withdrawCta' | translate }}
            </button>
          </div>
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 1rem;
      padding: 1rem;
    }
    .terms {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
    }
    .actions {
      display: flex;
      gap: 0.75rem;
      margin-top: 1rem;
    }
    table {
      width: 100%;
    }
  `,
})
export class LoanApplyComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(LoansStore);

  protected readonly loading = signal(false);
  protected readonly scheduleColumns = ['period', 'dueDate', 'principalDue', 'totalDueForPeriod', 'outstandingBalance'];

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

  constructor() {
    this.store.loadTemplate();
    // When a product's template arrives, prefill the term defaults the BFF returned.
    effect(() => {
      const template = this.store.template();
      if (!template) {
        return;
      }
      this.form.patchValue({
        productId: template.productId ?? this.form.controls.productId.value,
        principal: template.principal ?? this.form.controls.principal.value,
        numberOfRepayments: template.numberOfRepayments ?? this.form.controls.numberOfRepayments.value,
        repaymentEvery: template.repaymentEvery ?? this.form.controls.repaymentEvery.value,
        interestRatePerPeriod: template.interestRatePerPeriod ?? this.form.controls.interestRatePerPeriod.value,
        loanTermFrequency: (template.numberOfRepayments ?? 0) * (template.repaymentEvery ?? 1),
        loanTermFrequencyType: template.repaymentFrequencyTypeId ?? this.form.controls.loanTermFrequencyType.value,
        repaymentFrequencyType: template.repaymentFrequencyTypeId ?? this.form.controls.repaymentFrequencyType.value,
        amortizationType: template.amortizationTypeId ?? this.form.controls.amortizationType.value,
        interestType: template.interestTypeId ?? this.form.controls.interestType.value,
        interestCalculationPeriodType:
          template.interestCalculationPeriodTypeId ?? this.form.controls.interestCalculationPeriodType.value,
        transactionProcessingStrategyCode:
          template.transactionProcessingStrategyCode ?? this.form.controls.transactionProcessingStrategyCode.value,
      });
    });
  }

  protected onProductChange(productId: number): void {
    this.store.loadTemplate(productId);
  }

  protected preview(): void {
    if (this.form.invalid) {
      return;
    }
    this.run(this.store.previewSchedule(this.buildRequest()));
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.run(this.store.submit(this.buildRequest()));
  }

  protected modify(loanId: number | undefined): void {
    if (loanId == null || this.form.invalid) {
      return;
    }
    this.run(this.store.modify(loanId, this.buildRequest()));
  }

  protected withdraw(loanId: number | undefined): void {
    if (loanId == null) {
      return;
    }
    this.run(this.store.withdraw(loanId, { withdrawnOnDate: this.form.controls.submittedOnDate.value }));
  }

  private buildRequest(): SubmitLoanApplicationCommandRequest {
    return this.form.getRawValue();
  }

  private run(work: Observable<unknown>): void {
    this.loading.set(true);
    work.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.loading.set(false),
      error: () => this.loading.set(false),
    });
  }
}
