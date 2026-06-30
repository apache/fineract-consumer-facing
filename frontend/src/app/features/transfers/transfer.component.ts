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
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';
import { OtpComponent } from '../../shared/otp/otp.component';
import { TransfersStore } from './transfers.store';

@Component({
  selector: 'app-transfer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    DecimalPipe,
    TranslatePipe,
    OtpComponent,
  ],
  template: `
    <mat-card class="transfer-card">
      <mat-card-header>
        <mat-card-title>{{ 'transfers.title' | translate }}</mat-card-title>
      </mat-card-header>

      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <mat-card-content>
        @switch (step()) {
          @case ('form') {
            <form [formGroup]="form" (ngSubmit)="initiate()">
              <mat-form-field appearance="fill">
                <mat-label>{{ 'transfers.form.fromAccountLabel' | translate }}</mat-label>
                <input matInput type="number" formControlName="fromAccountId" />
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'transfers.form.toAccountLabel' | translate }}</mat-label>
                <input matInput type="number" formControlName="toAccountId" />
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'transfers.form.toAccountTypeLabel' | translate }}</mat-label>
                <mat-select formControlName="toAccountType">
                  @for (type of accountTypes; track type.value) {
                    <mat-option [value]="type.value">{{ type.labelKey | translate }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'transfers.form.amountLabel' | translate }}</mat-label>
                <input matInput type="number" step="0.01" formControlName="amount" />
              </mat-form-field>
              <div class="actions">
                <button
                  mat-flat-button
                  color="primary"
                  type="submit"
                  [disabled]="loading() || form.invalid"
                >
                  <mat-icon>swap_horiz</mat-icon>
                  {{ 'transfers.form.submitCta' | translate }}
                </button>
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
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    :host {
      display: flex;
      justify-content: center;
      padding: 2rem 1rem;
    }
    .transfer-card {
      width: 100%;
      max-width: 28rem;
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .actions {
      display: flex;
      justify-content: flex-end;
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
