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

import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
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
import { add, calendar } from 'ionicons/icons';
import { TranslatePipe } from '../../core/adapters';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { TransfersStore } from './transfers.store';

const HISTORY_PAGE_SIZE = 10;

@Component({
  selector: 'app-transfer',
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
    <app-page-header [title]="'transfers.history.title' | appTranslate">
      <ion-button fill="outline" [routerLink]="['/transfers', 'scheduled']">
        <ion-icon slot="start" name="calendar" aria-hidden="true" />
        {{ 'scheduledTransfers.title' | appTranslate }}
      </ion-button>
      <ion-button [routerLink]="['/transfers', 'new']">
        <ion-icon slot="start" name="add" aria-hidden="true" />
        {{ 'transfers.newCta' | appTranslate }}
      </ion-button>
    </app-page-header>

    <ion-card>
      @if (store.historyLoading()) {
        <ion-progress-bar type="indeterminate" />
      }

      <ion-card-content>
        <div class="table-scroll">
          <table cdk-table [dataSource]="store.history()">
            <ng-container cdkColumnDef="date">
              <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.date' | appTranslate }}</th>
              <td cdk-cell *cdkCellDef="let row">{{ row.date | date: 'mediumDate' }}</td>
            </ng-container>
            <ng-container cdkColumnDef="fromAccount">
              <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.filter.from' | appTranslate }}</th>
              <td cdk-cell *cdkCellDef="let row">{{ row.fromAccount }}</td>
            </ng-container>
            <ng-container cdkColumnDef="toAccount">
              <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.filter.to' | appTranslate }}</th>
              <td cdk-cell *cdkCellDef="let row">{{ row.toAccount }}</td>
            </ng-container>
            <ng-container cdkColumnDef="amount">
              <th cdk-header-cell *cdkHeaderCellDef class="num">
                {{ 'common.table.amount' | appTranslate }}
              </th>
              <td cdk-cell *cdkCellDef="let row" class="num">
                {{ row.amount | number: '1.2-2' }} {{ row.currency }}
              </td>
            </ng-container>
            <ng-container cdkColumnDef="direction">
              <th cdk-header-cell *cdkHeaderCellDef>
                {{ 'transfers.history.direction.label' | appTranslate }}
              </th>
              <td cdk-cell *cdkCellDef="let row">
                {{
                  row.direction
                    ? ('transfers.history.direction.' + row.direction | appTranslate)
                    : ''
                }}
              </td>
            </ng-container>

            <tr cdk-header-row *cdkHeaderRowDef="historyColumns"></tr>
            <tr cdk-row *cdkRowDef="let row; columns: historyColumns"></tr>
            <tr class="empty-row" *cdkNoDataRow>
              <td [attr.colspan]="historyColumns.length">
                {{ 'transfers.history.empty' | appTranslate }}
              </td>
            </tr>
          </table>
        </div>

        <div class="pager">
          <span class="showing">
            {{ 'common.pagination.showing' | appTranslate: showingParams() }}
          </span>
          <ion-button
            size="small"
            fill="outline"
            [disabled]="historyPrevDisabled()"
            (click)="historyPrev()"
          >
            {{ 'common.action.prev' | appTranslate }}
          </ion-button>
          <ion-button
            size="small"
            fill="outline"
            [disabled]="historyNextDisabled()"
            (click)="historyNext()"
          >
            {{ 'common.action.next' | appTranslate }}
          </ion-button>
        </div>
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: ['../../shared/css/table.scss', '../../shared/css/pager.scss'],
})
export class TransferComponent {
  protected readonly store = inject(TransfersStore);

  protected readonly historyColumns = ['date', 'fromAccount', 'toAccount', 'amount', 'direction'];

  protected readonly historyPrevDisabled = computed(
    () => this.store.historyLoading() || this.store.historyPage() === 0,
  );
  protected readonly historyNextDisabled = computed(
    () =>
      this.store.historyLoading() ||
      this.store.historyTotalPages() === 0 ||
      this.store.historyPage() >= this.store.historyTotalPages() - 1,
  );
  protected readonly showingParams = computed(() => {
    const total = this.store.historyTotalElements();
    if (total === 0) {
      return { from: 0, to: 0, total: 0 };
    }
    const page = this.store.historyPage();
    const size = this.store.historySize();
    return { from: page * size + 1, to: Math.min((page + 1) * size, total), total };
  });

  constructor() {
    addIcons({ add, calendar });
    this.loadPage(0);
  }

  protected historyPrev(): void {
    this.loadPage(this.store.historyPage() - 1);
  }

  protected historyNext(): void {
    this.loadPage(this.store.historyPage() + 1);
  }

  private loadPage(page: number): void {
    if (page < 0) {
      return;
    }
    this.store.loadHistory(page, HISTORY_PAGE_SIZE);
  }
}
