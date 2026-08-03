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
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
} from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';
import { SavingsStore } from './savings.store';

@Component({
  selector: 'app-savings-transaction',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardSubtitle,
    IonCardTitle,
    RouterLink,
    DatePipe,
    DecimalPipe,
    TranslatePipe,
  ],
  template: `
    @if (store.selectedTransaction(); as tx) {
      <ion-card>
        <ion-card-header>
          <ion-card-title>{{ 'common.transaction.title' | translate: { id: tx.id } }}</ion-card-title>
          <ion-card-subtitle>{{ tx.date | date: 'mediumDate' }}</ion-card-subtitle>
        </ion-card-header>
        <ion-card-content>
          <p>{{ 'savings.transaction.typeLabel' | translate }} {{ tx.type }}</p>
          <p>{{ 'savings.transaction.amountLabel' | translate }} {{ tx.amount | number: '1.2-2' }}</p>
          <p>
            {{ 'savings.transaction.runningBalanceLabel' | translate }}
            {{ tx.runningBalance | number: '1.2-2' }}
          </p>
          <div class="card-actions">
            <ion-button fill="outline" [routerLink]="['/savings', savingsId]">
              {{ 'common.action.backToAccount' | translate }}
            </ion-button>
          </div>
        </ion-card-content>
      </ion-card>
    }
  `,
  styleUrls: ['../../shared/css/transaction-page.scss'],
  styles: `
    .card-actions {
      margin-top: 1rem;
    }
  `,
})
export class SavingsTransactionComponent {
  private readonly route = inject(ActivatedRoute);
  protected readonly store = inject(SavingsStore);

  protected readonly savingsId = Number(this.route.snapshot.paramMap.get('savingsId'));

  constructor() {
    const transactionId = Number(this.route.snapshot.paramMap.get('transactionId'));
    this.store.loadTransaction(this.savingsId, transactionId);
  }
}
