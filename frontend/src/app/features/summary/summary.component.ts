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
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { TranslatePipe } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { SummaryStore } from './summary.store';

@Component({
  selector: 'app-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatCardModule,
    MatProgressBarModule,
    CurrencyPipe,
    TranslatePipe,
    PageHeaderComponent,
  ],
  template: `
    <app-page-header [title]="'summary.title' | translate" />

    @if (store.loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    <section>
      <h2>{{ 'summary.section.savings' | translate }}</h2>
      <div class="cards">
        @for (card of store.savingsCards(); track card.id) {
          <mat-card [routerLink]="['/savings', card.id]" role="link" tabindex="0">
            <mat-card-header>
              <mat-card-title>{{ card.productName }}</mat-card-title>
              <mat-card-subtitle>{{ card.accountNo }}</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <p class="amount">{{ card.balance | currency: card.currency }}</p>
              <p>{{ 'summary.savings.availableLabel' | translate }} {{ card.availableBalance | currency: card.currency }}</p>
            </mat-card-content>
          </mat-card>
        } @empty {
          <p>{{ 'summary.savings.empty' | translate }}</p>
        }
      </div>
    </section>

    <section>
      <h2>{{ 'summary.section.loans' | translate }}</h2>
      <div class="cards">
        @for (card of store.loanCards(); track card.id) {
          <mat-card [routerLink]="['/loans', card.id]" role="link" tabindex="0">
            <mat-card-header>
              <mat-card-title>{{ card.productName }}</mat-card-title>
              <mat-card-subtitle>{{ card.accountNo }}</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <p class="amount">{{ 'summary.loan.outstandingLabel' | translate }} {{ card.totalOutstanding | currency: card.currency }}</p>
            </mat-card-content>
          </mat-card>
        } @empty {
          <p>{{ 'summary.loan.empty' | translate }}</p>
        }
      </div>
    </section>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }
    section {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .cards {
      display: flex;
      flex-wrap: wrap;
      gap: 1rem;
    }
    mat-card {
      width: 16rem;
      cursor: pointer;
      transition:
        box-shadow 0.2s ease,
        transform 0.2s ease;
    }
    mat-card:hover,
    mat-card:focus-visible {
      box-shadow: var(--mat-sys-level3, 0 4px 12px rgba(0, 0, 0, 0.2));
      transform: translateY(-2px);
    }
    .amount {
      font-size: 1.25rem;
      font-weight: 600;
    }
  `,
})
export class SummaryComponent {
  protected readonly store = inject(SummaryStore);

  constructor() {
    this.store.load();
  }
}
