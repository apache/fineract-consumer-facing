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
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IonCard, IonProgressBar } from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { SummaryStore } from './summary.store';

@Component({
  selector: 'app-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    IonCard,
    IonProgressBar,
    CurrencyPipe,
    TranslatePipe,
    PageHeaderComponent,
    StatusBadgeComponent,
  ],
  template: `
    <app-page-header [title]="'summary.title' | translate" />

    @if (store.loading()) {
      <ion-progress-bar type="indeterminate" />
    }

    <ion-card>
      <header class="section-header">
        <h2>{{ 'summary.section.savings' | translate }}</h2>
        <span class="count">
          {{ 'summary.accountsCount' | translate: { count: store.savingsCount() } }}
        </span>
      </header>

      @for (row of store.topSavings(); track row.id) {
        <a class="account-row" [routerLink]="['/savings', row.id]">
          <span class="row-main">
            <span class="product-line">
              <span class="product">{{ row.productName }}</span>
              @if (row.status) {
                <app-status-badge [status]="row.status" />
              }
            </span>
            <span class="account-no">{{ row.accountNo }}</span>
          </span>
          <span class="row-end">
            <span class="amount">{{ row.balance | currency: row.currency }}</span>
          </span>
        </a>
      } @empty {
        <p class="empty">{{ 'summary.savings.empty' | translate }}</p>
      }

      @if (store.savingsCount() > 0) {
        <a class="view-all" routerLink="/savings">
          {{ 'summary.viewAll' | translate: { count: store.savingsCount() } }} &rarr;
        </a>
      }
    </ion-card>

    <ion-card>
      <header class="section-header">
        <h2>{{ 'summary.section.loans' | translate }}</h2>
        <span class="count">
          {{ 'summary.accountsCount' | translate: { count: store.loansCount() } }}
        </span>
      </header>

      @for (row of store.topLoans(); track row.id) {
        <a class="account-row" [routerLink]="['/loans', row.id]">
          <span class="row-main">
            <span class="product-line">
              <span class="product">{{ row.productName }}</span>
              @if (row.status) {
                <app-status-badge [status]="row.status" />
              }
            </span>
            <span class="account-no">{{ row.accountNo }}</span>
          </span>
          <span class="row-end">
            <span class="amount">{{ row.totalOutstanding | currency: row.currency }}</span>
          </span>
        </a>
      } @empty {
        <p class="empty">{{ 'summary.loan.empty' | translate }}</p>
      }

      @if (store.loansCount() > 0) {
        <a class="view-all" routerLink="/loans">
          {{ 'summary.viewAll' | translate: { count: store.loansCount() } }} &rarr;
        </a>
      }
    </ion-card>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    ion-card {
      margin: 0;
    }
    .section-header {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      gap: 1rem;
      padding: 1rem 1.25rem;
      border-bottom: 1px solid var(--border);
    }
    .section-header h2 {
      margin: 0;
      font-size: var(--text-lg);
      font-weight: 600;
      letter-spacing: var(--tracking-snug);
      color: var(--ink);
    }
    .count {
      font-size: var(--text-xs);
      color: var(--slate-400);
      white-space: nowrap;
    }
    .account-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.75rem 1.25rem;
      text-decoration: none;
      cursor: pointer;
      transition: background-color var(--dur-base) var(--ease-out);
    }
    .account-row + .account-row {
      border-top: 1px solid var(--border);
    }
    .account-row:hover,
    .account-row:focus-visible {
      background-color: var(--page-bg);
    }
    .row-main {
      display: flex;
      flex-direction: column;
      gap: 0.125rem;
      min-width: 0;
    }
    .product-line {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      min-width: 0;
    }
    .product {
      font-weight: 600;
      font-size: var(--text-base);
      color: var(--ink);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      min-width: 0;
    }
    .product-line app-status-badge {
      flex-shrink: 0;
    }
    .account-no {
      font-size: var(--text-xs);
      color: var(--slate-400);
      font-variant-numeric: tabular-nums;
    }
    .row-end {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      flex-shrink: 0;
    }
    .amount {
      font-size: var(--text-base);
      font-weight: 600;
      letter-spacing: var(--tracking-snug);
      color: var(--ink);
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .empty {
      margin: 0;
      padding: 2rem 1rem;
      text-align: center;
      color: var(--slate-400);
    }
    .view-all {
      display: block;
      padding: 0.75rem 1.25rem;
      border-top: 1px solid var(--border);
      font-size: var(--text-sm);
      font-weight: 600;
      color: var(--cobalt-600);
      text-decoration: none;
      cursor: pointer;
      transition: background-color var(--dur-base) var(--ease-out);
    }
    .view-all:hover,
    .view-all:focus-visible {
      background-color: var(--cobalt-50);
      color: var(--cobalt-700);
    }
  `,
})
export class SummaryComponent {
  protected readonly store = inject(SummaryStore);

  constructor() {
    this.store.load();
  }
}
