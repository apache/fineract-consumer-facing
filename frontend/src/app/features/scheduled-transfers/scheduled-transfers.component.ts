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
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { CdkTableModule } from '@angular/cdk/table';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonIcon,
  IonProgressBar,
  IonRouterLink,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { add, arrowBack } from 'ionicons/icons';
import { TranslatePipe } from '../../core/adapters';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { BeneficiariesStore } from '../beneficiaries/beneficiaries.store';
import { SavingsStore } from '../savings/savings.store';
import { accountLabels, labelFor } from './account-labels';
import { ScheduledTransfersStore } from './scheduled-transfers.store';

@Component({
  selector: 'app-scheduled-transfers',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkTableModule,
    IonButton,
    IonCard,
    IonCardContent,
    IonIcon,
    IonProgressBar,
    IonRouterLink,
    RouterLink,
    PageHeaderComponent,
    DatePipe,
    DecimalPipe,
    TranslatePipe,
  ],
  template: `
    <app-page-header [title]="'scheduledTransfers.title' | appTranslate">
      <ion-button fill="outline" routerLink="/transfers">
        <ion-icon slot="start" name="arrow-back" aria-hidden="true" />
        {{ 'transfers.backCta' | appTranslate }}
      </ion-button>
      @if (!store.disabled()) {
        <ion-button [routerLink]="['/transfers', 'scheduled', 'new']">
          <ion-icon slot="start" name="add" aria-hidden="true" />
          {{ 'scheduledTransfers.newCta' | appTranslate }}
        </ion-button>
      }
    </app-page-header>

    <ion-card>
      @if (loading()) {
        <ion-progress-bar type="indeterminate" />
      }

      <ion-card-content>
        @if (store.disabled()) {
          <p class="disabled">{{ 'scheduledTransfers.disabled' | appTranslate }}</p>
        } @else {
          <div class="table-scroll">
            <table cdk-table [dataSource]="store.transfers()">
              <ng-container cdkColumnDef="fromAccount">
                <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.filter.from' | appTranslate }}</th>
                <td cdk-cell *cdkCellDef="let row">{{ label(row.fromAccountId) }}</td>
              </ng-container>
              <ng-container cdkColumnDef="toAccount">
                <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.filter.to' | appTranslate }}</th>
                <td cdk-cell *cdkCellDef="let row">{{ label(row.toAccountId) }}</td>
              </ng-container>
              <ng-container cdkColumnDef="amount">
                <th cdk-header-cell *cdkHeaderCellDef class="num">
                  {{ 'common.table.amount' | appTranslate }}
                </th>
                <td cdk-cell *cdkCellDef="let row" class="num">
                  {{ row.amount | number: '1.2-2' }}
                </td>
              </ng-container>
              <ng-container cdkColumnDef="frequency">
                <th cdk-header-cell *cdkHeaderCellDef>
                  {{ 'scheduledTransfers.frequency.label' | appTranslate }}
                </th>
                <td cdk-cell *cdkCellDef="let row">
                  {{ 'scheduledTransfers.frequency.' + row.frequency | appTranslate }}
                </td>
              </ng-container>
              <ng-container cdkColumnDef="startDate">
                <th cdk-header-cell *cdkHeaderCellDef>
                  {{ 'scheduledTransfers.startDate' | appTranslate }}
                </th>
                <td cdk-cell *cdkCellDef="let row">{{ row.startDate | date: 'mediumDate' }}</td>
              </ng-container>
              <ng-container cdkColumnDef="endDate">
                <th cdk-header-cell *cdkHeaderCellDef>
                  {{ 'scheduledTransfers.endDate' | appTranslate }}
                </th>
                <td cdk-cell *cdkCellDef="let row">
                  @if (row.endDate) {
                    {{ row.endDate | date: 'mediumDate' }}
                  } @else {
                    {{ 'scheduledTransfers.noEndDate' | appTranslate }}
                  }
                </td>
              </ng-container>
              <ng-container cdkColumnDef="actions">
                <th cdk-header-cell *cdkHeaderCellDef></th>
                <td cdk-cell *cdkCellDef="let row" class="num">
                  <ion-button
                    size="small"
                    fill="clear"
                    [routerLink]="['/transfers', 'scheduled', row.publicId]"
                  >
                    {{ 'scheduledTransfers.list.viewCta' | appTranslate }}
                  </ion-button>
                </td>
              </ng-container>

              <tr cdk-header-row *cdkHeaderRowDef="columns"></tr>
              <tr cdk-row *cdkRowDef="let row; columns: columns"></tr>
              <tr class="empty-row" *cdkNoDataRow>
                <td [attr.colspan]="columns.length">
                  {{ 'scheduledTransfers.list.empty' | appTranslate }}
                </td>
              </tr>
            </table>
          </div>
        }
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: ['../../shared/css/table.scss'],
  styles: `
    .disabled {
      margin: 0;
    }
  `,
})
export class ScheduledTransfersComponent {
  protected readonly store = inject(ScheduledTransfersStore);
  private readonly savingsStore = inject(SavingsStore);
  private readonly beneficiariesStore = inject(BeneficiariesStore);

  protected readonly columns = [
    'fromAccount',
    'toAccount',
    'amount',
    'frequency',
    'startDate',
    'endDate',
    'actions',
  ];
  protected readonly loading = signal(true);

  private readonly labels = computed(() =>
    accountLabels(this.savingsStore.accounts(), this.beneficiariesStore.beneficiaries()),
  );

  constructor() {
    addIcons({ add, arrowBack });
    this.savingsStore.loadAccounts();
    this.beneficiariesStore.load().pipe(takeUntilDestroyed()).subscribe();
    this.store
      .load()
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: () => this.loading.set(false),
        error: () => this.loading.set(false),
      });
  }

  protected label(accountId: number | undefined): string {
    return labelFor(this.labels(), accountId);
  }
}
