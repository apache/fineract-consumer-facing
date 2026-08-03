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
import { CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { CdkTableModule } from '@angular/cdk/table';
import {
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonProgressBar,
} from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { LoansStore } from './loans.store';

@Component({
  selector: 'app-loans-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkTableModule,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardSubtitle,
    IonCardTitle,
    IonProgressBar,
    CurrencyPipe,
    DatePipe,
    PageHeaderComponent,
    StatusBadgeComponent,
    TranslatePipe,
  ],
  template: `
    <app-page-header [title]="'loans.detail.title' | translate" />

    @if (store.loading()) {
      <ion-progress-bar type="indeterminate" />
    }

    @if (store.selected(); as loan) {
      <ion-card>
        <ion-card-header>
          <ion-card-title>{{ loan.productName }} — {{ loan.accountNo }}</ion-card-title>
          @if (loan.status) {
            <ion-card-subtitle><app-status-badge [status]="loan.status" /></ion-card-subtitle>
          }
        </ion-card-header>
        <ion-card-content>
          <p>
            {{ 'loans.detail.principalDisbursedLabel' | translate }}
            <span class="amount">{{ loan.principalDisbursed | currency: loan.currency }}</span>
          </p>
          <p>
            {{ 'loans.detail.totalOutstandingLabel' | translate }}
            <span class="amount">{{ loan.totalOutstanding | currency: loan.currency }}</span>
          </p>
          <p>
            {{ 'loans.detail.interestOutstandingLabel' | translate }}
            <span class="amount">{{ loan.interestOutstanding | currency: loan.currency }}</span>
          </p>
          <p>{{ 'loans.detail.annualInterestRateLabel' | translate }} {{ loan.annualInterestRate }}%</p>
          <p>
            {{ 'loans.detail.nextDueLabel' | translate }} {{ loan.nextDueAmount | currency: loan.currency }}
            {{ 'loans.detail.nextDueOn' | translate }} {{ loan.nextDueDate | date: 'mediumDate' }}
          </p>
        </ion-card-content>
      </ion-card>
    }

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'common.section.charges' | translate }}</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <div class="table-scroll">
        <table cdk-table [dataSource]="store.charges()">
          <ng-container cdkColumnDef="name">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.charge' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.name }}</td>
          </ng-container>
          <ng-container cdkColumnDef="amount">
            <th cdk-header-cell *cdkHeaderCellDef class="num">{{ 'common.table.amount' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row" class="num">{{ row.amount | currency: row.currency }}</td>
          </ng-container>
          <ng-container cdkColumnDef="amountOutstanding">
            <th cdk-header-cell *cdkHeaderCellDef class="num">{{ 'common.table.outstanding' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row" class="num">{{ row.amountOutstanding | currency: row.currency }}</td>
          </ng-container>

          <tr cdk-header-row *cdkHeaderRowDef="chargeColumns"></tr>
          <tr cdk-row *cdkRowDef="let row; columns: chargeColumns"></tr>
          <tr class="empty-row" *cdkNoDataRow>
            <td [attr.colspan]="chargeColumns.length">{{ 'common.table.noCharges' | translate }}</td>
          </tr>
        </table>
        </div>
      </ion-card-content>
    </ion-card>

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'common.section.guarantors' | translate }}</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <div class="table-scroll">
        <table cdk-table [dataSource]="store.guarantors()">
          <ng-container cdkColumnDef="displayName">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.name' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.displayName }}</td>
          </ng-container>
          <ng-container cdkColumnDef="guarantorType">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.type' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.guarantorType }}</td>
          </ng-container>
          <ng-container cdkColumnDef="relationship">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.relationship' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.relationship }}</td>
          </ng-container>

          <tr cdk-header-row *cdkHeaderRowDef="guarantorColumns"></tr>
          <tr cdk-row *cdkRowDef="let row; columns: guarantorColumns"></tr>
          <tr class="empty-row" *cdkNoDataRow>
            <td [attr.colspan]="guarantorColumns.length">{{ 'loans.detail.noGuarantors' | translate }}</td>
          </tr>
        </table>
        </div>
      </ion-card-content>
    </ion-card>

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'common.section.transactions' | translate }}</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <div class="table-scroll">
        <table cdk-table [dataSource]="store.transactions()">
          <ng-container cdkColumnDef="date">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.date' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.date | date: 'mediumDate' }}</td>
          </ng-container>
          <ng-container cdkColumnDef="type">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.type' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.type }}</td>
          </ng-container>
          <ng-container cdkColumnDef="amount">
            <th cdk-header-cell *cdkHeaderCellDef class="num">{{ 'common.table.amount' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row" class="num">{{ row.amount | currency: row.currency }}</td>
          </ng-container>
          <ng-container cdkColumnDef="outstandingLoanBalance">
            <th cdk-header-cell *cdkHeaderCellDef class="num">{{ 'common.table.balance' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row" class="num">{{ row.outstandingLoanBalance | currency: row.currency }}</td>
          </ng-container>

          <tr cdk-header-row *cdkHeaderRowDef="txColumns"></tr>
          <tr
            cdk-row
            *cdkRowDef="let row; columns: txColumns"
            class="clickable"
            (click)="openTransaction(row.id)"
          ></tr>
          <tr class="empty-row" *cdkNoDataRow>
            <td [attr.colspan]="txColumns.length">{{ 'common.table.noTransactions' | translate }}</td>
          </tr>
        </table>
        </div>
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: ['../../shared/css/detail-page.scss', '../../shared/css/table.scss'],
})
export class LoansDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  protected readonly store = inject(LoansStore);

  protected readonly loanId = Number(this.route.snapshot.paramMap.get('loanId'));
  protected readonly chargeColumns = ['name', 'amount', 'amountOutstanding'];
  protected readonly guarantorColumns = ['displayName', 'guarantorType', 'relationship'];
  protected readonly txColumns = ['date', 'type', 'amount', 'outstandingLoanBalance'];

  constructor() {
    this.store.loadLoan(this.loanId);
    this.store.loadCharges(this.loanId);
    this.store.loadGuarantors(this.loanId);
    this.store.loadTransactions(this.loanId);
  }

  protected openTransaction(transactionId: number | undefined): void {
    if (transactionId != null) {
      this.router.navigate(['/loans', this.loanId, 'transactions', transactionId]);
    }
  }
}
