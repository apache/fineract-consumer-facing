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

import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { TranslatePipe } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { SavingsStore } from './savings.store';

function toIsoDate(value: Date | null): string | undefined {
  if (!value) {
    return undefined;
  }
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

@Component({
  selector: 'app-savings-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [provideNativeDateAdapter()],
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatTableModule,
    TranslatePipe,
    DatePipe,
    DecimalPipe,
    PageHeaderComponent,
    StatusBadgeComponent,
  ],
  template: `
    <app-page-header [title]="'savings.detail.title' | translate" />

    @if (store.loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (store.selected(); as account) {
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ account.productName }} — {{ account.accountNo }}</mat-card-title>
          @if (account.status) {
            <mat-card-subtitle><app-status-badge [status]="account.status" /></mat-card-subtitle>
          }
        </mat-card-header>
        <mat-card-content>
          <p>{{ 'savings.detail.balanceLabel' | translate }} {{ account.balance | number: '1.2-2' }}</p>
          <p>
            {{ 'savings.detail.availableLabel' | translate }}
            {{ account.availableBalance | number: '1.2-2' }}
          </p>
          <p>
            {{ 'savings.detail.interestRateLabel' | translate }}
            {{ account.nominalAnnualInterestRate }}%
          </p>
        </mat-card-content>
      </mat-card>
    }

    <mat-card>
      <mat-card-header>
        <mat-card-title>{{ 'common.section.charges' | translate }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <table mat-table [dataSource]="store.charges()">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.charge' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.name }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef class="num">{{ 'common.table.amount' | translate }}</th>
            <td mat-cell *matCellDef="let row" class="num">{{ row.amount | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="amountOutstanding">
            <th mat-header-cell *matHeaderCellDef class="num">{{ 'common.table.outstanding' | translate }}</th>
            <td mat-cell *matCellDef="let row" class="num">{{ row.amountOutstanding | number: '1.2-2' }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="chargeColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: chargeColumns"></tr>
          <tr class="empty-row" *matNoDataRow>
            <td [attr.colspan]="chargeColumns.length">{{ 'common.table.noCharges' | translate }}</td>
          </tr>
        </table>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-header>
        <mat-card-title>{{ 'common.section.transactions' | translate }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <form class="filter" [formGroup]="filterForm" (ngSubmit)="applyFilter()">
          <mat-form-field appearance="fill">
            <mat-label>{{ 'common.filter.from' | translate }}</mat-label>
            <input matInput [matDatepicker]="fromPicker" formControlName="fromDate" />
            <mat-datepicker-toggle matIconSuffix [for]="fromPicker" />
            <mat-datepicker #fromPicker />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>{{ 'common.filter.to' | translate }}</mat-label>
            <input matInput [matDatepicker]="toPicker" formControlName="toDate" />
            <mat-datepicker-toggle matIconSuffix [for]="toPicker" />
            <mat-datepicker #toPicker />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>{{ 'common.filter.page' | translate }}</mat-label>
            <input matInput type="number" min="0" formControlName="page" />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>{{ 'common.filter.size' | translate }}</mat-label>
            <input matInput type="number" min="1" formControlName="size" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit">{{ 'common.action.applyFilter' | translate }}</button>
        </form>

        <table mat-table [dataSource]="store.transactions()">
          <ng-container matColumnDef="date">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.date' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.date | date: 'mediumDate' }}</td>
          </ng-container>
          <ng-container matColumnDef="type">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.type' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.type }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef class="num">{{ 'common.table.amount' | translate }}</th>
            <td mat-cell *matCellDef="let row" class="num">{{ row.amount | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="runningBalance">
            <th mat-header-cell *matHeaderCellDef class="num">{{ 'common.table.balance' | translate }}</th>
            <td mat-cell *matCellDef="let row" class="num">{{ row.runningBalance | number: '1.2-2' }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="txColumns"></tr>
          <tr
            mat-row
            *matRowDef="let row; columns: txColumns"
            class="clickable"
            (click)="openTransaction(row.id)"
          ></tr>
          <tr class="empty-row" *matNoDataRow>
            <td [attr.colspan]="txColumns.length">{{ 'common.table.noTransactions' | translate }}</td>
          </tr>
        </table>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
      padding: 2rem;
    }
    table {
      width: 100%;
    }
    .filter {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: center;
      margin-bottom: 1rem;
    }
    .clickable {
      cursor: pointer;
    }
  `,
})
export class SavingsDetailComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly store = inject(SavingsStore);

  protected readonly savingsId = Number(this.route.snapshot.paramMap.get('savingsId'));
  protected readonly chargeColumns = ['name', 'amount', 'amountOutstanding'];
  protected readonly txColumns = ['date', 'type', 'amount', 'runningBalance'];

  protected readonly filterForm = this.fb.group({
    fromDate: this.fb.control<Date | null>(null),
    toDate: this.fb.control<Date | null>(null),
    page: this.fb.control<number>(0),
    size: this.fb.control<number>(20),
  });

  constructor() {
    this.store.loadAccount(this.savingsId);
    this.store.loadCharges(this.savingsId);
    this.store.loadTransactions(this.savingsId, { page: 0, size: 20 });
  }

  protected applyFilter(): void {
    const { fromDate, toDate, page, size } = this.filterForm.getRawValue();
    this.store.loadTransactions(this.savingsId, {
      fromDate: toIsoDate(fromDate),
      toDate: toIsoDate(toDate),
      page,
      size,
    });
  }

  protected openTransaction(transactionId: number | undefined): void {
    if (transactionId != null) {
      this.router.navigate(['/savings', this.savingsId, 'transactions', transactionId]);
    }
  }
}
