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
import { SummaryStore } from './summary.store';

@Component({
  selector: 'app-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardSubtitle,
    IonCardTitle,
    IonProgressBar,
    CurrencyPipe,
    TranslatePipe,
    PageHeaderComponent,
  ],
  template: `
    <app-page-header [title]="'summary.title' | translate" />

    @if (store.loading()) {
      <ion-progress-bar type="indeterminate" />
    }

    <section>
      <h2>{{ 'summary.section.savings' | translate }}</h2>
      <div class="cards">
        @for (card of store.savingsCards(); track card.id) {
          <ion-card [routerLink]="['/savings', card.id]" role="link" tabindex="0">
            <ion-card-header>
              <ion-card-title>{{ card.productName }}</ion-card-title>
              <ion-card-subtitle>{{ card.accountNo }}</ion-card-subtitle>
            </ion-card-header>
            <ion-card-content>
              <p class="amount">{{ card.balance | currency: card.currency }}</p>
            </ion-card-content>
          </ion-card>
        } @empty {
          <p class="empty">{{ 'summary.savings.empty' | translate }}</p>
        }
      </div>
    </section>

    <section>
      <h2>{{ 'summary.section.loans' | translate }}</h2>
      <div class="cards">
        @for (card of store.loanCards(); track card.id) {
          <ion-card [routerLink]="['/loans', card.id]" role="link" tabindex="0">
            <ion-card-header>
              <ion-card-title>{{ card.productName }}</ion-card-title>
              <ion-card-subtitle>{{ card.accountNo }}</ion-card-subtitle>
            </ion-card-header>
            <ion-card-content>
              <p class="amount">{{ card.totalOutstanding | currency: card.currency }}</p>
            </ion-card-content>
          </ion-card>
        } @empty {
          <p class="empty">{{ 'summary.loan.empty' | translate }}</p>
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
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr));
      gap: 1rem;
    }
    ion-card ion-card-content {
      padding-top: 1rem;
      padding-bottom: 1.75rem;
    }
    ion-card {
      cursor: pointer;
      transition:
        box-shadow var(--dur-base) var(--ease-out),
        transform var(--dur-base) var(--ease-out);
    }
    ion-card:hover,
    ion-card:focus-visible {
      box-shadow: var(--shadow-md);
      transform: translateY(-2px);
    }
    .empty {
      grid-column: 1 / -1;
      padding: 2rem 1rem;
      text-align: center;
      color: var(--slate-400);
    }
    .amount {
      font-size: var(--text-xl);
      font-weight: 600;
      letter-spacing: var(--tracking-snug);
      color: var(--ink);
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class SummaryComponent {
  protected readonly store = inject(SummaryStore);

  constructor() {
    this.store.load();
  }
}
