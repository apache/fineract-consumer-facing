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
import { CdkTableModule } from '@angular/cdk/table';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonDatetime,
  IonDatetimeButton,
  IonInput,
  IonModal,
  IonProgressBar,
} from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { toIsoDate } from '../../shared/utils/date';
import { SavingsStore } from './savings.store';

@Component({
  selector: 'app-savings-detail',
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
    IonDatetime,
    IonDatetimeButton,
    IonInput,
    IonModal,
    IonProgressBar,
    TranslatePipe,
    DatePipe,
    DecimalPipe,
    PageHeaderComponent,
    StatusBadgeComponent,
  ],
  template: `
    <app-page-header [title]="'savings.detail.title' | translate" />

    @if (store.loading()) {
      <ion-progress-bar type="indeterminate" />
    }

    @if (store.selected(); as account) {
      <ion-card>
        <ion-card-header>
          <ion-card-title>{{ account.productName }} — {{ account.accountNo }}</ion-card-title>
          @if (account.status) {
            <ion-card-subtitle><app-status-badge [status]="account.status" /></ion-card-subtitle>
          }
        </ion-card-header>
        <ion-card-content>
          <p>
            {{ 'savings.detail.balanceLabel' | translate }}
            <span class="amount">{{ account.balance | number: '1.2-2' }}</span>
          </p>
          <p>
            {{ 'savings.detail.availableLabel' | translate }}
            <span class="amount">{{ account.availableBalance | number: '1.2-2' }}</span>
          </p>
          <p>
            {{ 'savings.detail.interestRateLabel' | translate }}
            {{ account.nominalAnnualInterestRate }}%
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
            <td cdk-cell *cdkCellDef="let row" class="num">{{ row.amount | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container cdkColumnDef="amountOutstanding">
            <th cdk-header-cell *cdkHeaderCellDef class="num">{{ 'common.table.outstanding' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row" class="num">{{ row.amountOutstanding | number: '1.2-2' }}</td>
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
        <ion-card-title>{{ 'common.section.transactions' | translate }}</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <form class="filter" [formGroup]="filterForm" (ngSubmit)="applyFilter()">
          <!-- Ionic has no range picker (design.md §4): two date fields, same fromDate/toDate output -->
          <div class="field">
            <span class="field-label">{{ 'common.filter.from' | translate }}</span>
            <ion-datetime-button datetime="savingsFromDate" />
            <ion-modal [keepContentsMounted]="true">
              <ng-template>
                <ion-datetime
                  id="savingsFromDate"
                  presentation="date"
                  formControlName="fromDate"
                  [showDefaultButtons]="true"
                  [showClearButton]="true"
                />
              </ng-template>
            </ion-modal>
          </div>
          <div class="field">
            <span class="field-label">{{ 'common.filter.to' | translate }}</span>
            <ion-datetime-button datetime="savingsToDate" />
            <ion-modal [keepContentsMounted]="true">
              <ng-template>
                <ion-datetime
                  id="savingsToDate"
                  presentation="date"
                  formControlName="toDate"
                  [showDefaultButtons]="true"
                  [showClearButton]="true"
                />
              </ng-template>
            </ion-modal>
          </div>
          <ion-input
            type="number"
            min="0"
            formControlName="page"
            fill="outline"
            labelPlacement="stacked"
            [label]="'common.filter.page' | translate"
          />
          <ion-input
            type="number"
            min="1"
            formControlName="size"
            fill="outline"
            labelPlacement="stacked"
            [label]="'common.filter.size' | translate"
          />
          <ion-button type="submit">{{ 'common.action.applyFilter' | translate }}</ion-button>
        </form>

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
            <td cdk-cell *cdkCellDef="let row" class="num">{{ row.amount | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container cdkColumnDef="runningBalance">
            <th cdk-header-cell *cdkHeaderCellDef class="num">{{ 'common.table.balance' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row" class="num">{{ row.runningBalance | number: '1.2-2' }}</td>
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
  styleUrls: [
    '../../shared/css/detail-page.scss',
    '../../shared/css/table.scss',
    '../../shared/css/filter-bar.scss',
  ],
  styles: `
    ion-datetime-button::part(native) {
      font-size: var(--text-base);
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
    fromDate: this.fb.control<string | null>(null),
    toDate: this.fb.control<string | null>(null),
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
