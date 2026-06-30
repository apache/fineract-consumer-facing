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

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { TranslatePipe } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { ChargePaymentComponent } from './charge-payment.component';
import { LoansStore } from './loans.store';

@Component({
  selector: 'app-loans-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule,
    MatCardModule,
    MatProgressBarModule,
    MatTableModule,
    CurrencyPipe,
    DatePipe,
    PageHeaderComponent,
    StatusBadgeComponent,
    ChargePaymentComponent,
    TranslatePipe,
  ],
  template: `
    <app-page-header [title]="'loans.detail.title' | translate" />

    @if (store.loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (store.selected(); as loan) {
      <mat-card>
        <mat-card-header>
          <mat-card-title>{{ loan.productName }} — {{ loan.accountNo }}</mat-card-title>
          @if (loan.status) {
            <mat-card-subtitle><app-status-badge [status]="loan.status" /></mat-card-subtitle>
          }
        </mat-card-header>
        <mat-card-content>
          <p>{{ 'loans.detail.principalDisbursedLabel' | translate }} {{ loan.principalDisbursed | currency: loan.currency }}</p>
          <p>{{ 'loans.detail.totalOutstandingLabel' | translate }} {{ loan.totalOutstanding | currency: loan.currency }}</p>
          <p>{{ 'loans.detail.interestOutstandingLabel' | translate }} {{ loan.interestOutstanding | currency: loan.currency }}</p>
          <p>{{ 'loans.detail.annualInterestRateLabel' | translate }} {{ loan.annualInterestRate }}%</p>
          <p>
            {{ 'loans.detail.nextDueLabel' | translate }} {{ loan.nextDueAmount | currency: loan.currency }}
            {{ 'loans.detail.nextDueOn' | translate }} {{ loan.nextDueDate | date: 'mediumDate' }}
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
            <td mat-cell *matCellDef="let row" class="num">{{ row.amount | currency: row.currency }}</td>
          </ng-container>
          <ng-container matColumnDef="amountOutstanding">
            <th mat-header-cell *matHeaderCellDef class="num">{{ 'common.table.outstanding' | translate }}</th>
            <td mat-cell *matCellDef="let row" class="num">{{ row.amountOutstanding | currency: row.currency }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef></th>
            <td mat-cell *matCellDef="let row">
              <button mat-button color="primary" (click)="pay(row.id)">{{ 'common.action.pay' | translate }}</button>
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="chargeColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: chargeColumns"></tr>
          <tr class="empty-row" *matNoDataRow>
            <td [attr.colspan]="chargeColumns.length">{{ 'common.table.noCharges' | translate }}</td>
          </tr>
        </table>

        @if (payingCharge(); as charge) {
          <app-charge-payment
            [loanId]="loanId"
            [charge]="charge"
            (paid)="onChargePaid()"
            (cancelled)="payingChargeId.set(null)"
          />
        }
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-header>
        <mat-card-title>{{ 'common.section.guarantors' | translate }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <table mat-table [dataSource]="store.guarantors()">
          <ng-container matColumnDef="displayName">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.name' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.displayName }}</td>
          </ng-container>
          <ng-container matColumnDef="guarantorType">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.type' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.guarantorType }}</td>
          </ng-container>
          <ng-container matColumnDef="relationship">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.relationship' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.relationship }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="guarantorColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: guarantorColumns"></tr>
          <tr class="empty-row" *matNoDataRow>
            <td [attr.colspan]="guarantorColumns.length">{{ 'loans.detail.noGuarantors' | translate }}</td>
          </tr>
        </table>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-header>
        <mat-card-title>{{ 'common.section.transactions' | translate }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
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
            <td mat-cell *matCellDef="let row" class="num">{{ row.amount | currency: row.currency }}</td>
          </ng-container>
          <ng-container matColumnDef="outstandingLoanBalance">
            <th mat-header-cell *matHeaderCellDef class="num">{{ 'common.table.balance' | translate }}</th>
            <td mat-cell *matCellDef="let row" class="num">{{ row.outstandingLoanBalance | currency: row.currency }}</td>
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
    .clickable {
      cursor: pointer;
    }
  `,
})
export class LoansDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly store = inject(LoansStore);

  protected readonly loanId = Number(this.route.snapshot.paramMap.get('loanId'));
  protected readonly chargeColumns = ['name', 'amount', 'amountOutstanding', 'actions'];
  protected readonly guarantorColumns = ['displayName', 'guarantorType', 'relationship'];
  protected readonly txColumns = ['date', 'type', 'amount', 'outstandingLoanBalance'];

  protected readonly payingChargeId = signal<number | null>(null);
  protected readonly payingCharge = computed(() =>
    this.store.charges().find(charge => charge.id === this.payingChargeId()),
  );

  constructor() {
    this.store.loadLoan(this.loanId);
    this.store.loadCharges(this.loanId);
    this.store.loadGuarantors(this.loanId);
    this.store.loadTransactions(this.loanId);
  }

  protected pay(chargeId: number | undefined): void {
    if (chargeId != null) {
      this.payingChargeId.set(chargeId);
    }
  }

  protected onChargePaid(): void {
    this.payingChargeId.set(null);
    this.store.loadCharges(this.loanId);
  }

  protected openTransaction(transactionId: number | undefined): void {
    if (transactionId != null) {
      this.router.navigate(['/loans', this.loanId, 'transactions', transactionId]);
    }
  }
}
