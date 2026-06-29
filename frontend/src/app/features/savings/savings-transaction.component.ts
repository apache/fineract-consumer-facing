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
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { SavingsStore } from './savings.store';

@Component({
  selector: 'app-savings-transaction',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatCardModule, RouterLink, DatePipe, DecimalPipe],
  template: `
    @if (store.selectedTransaction(); as tx) {
      <mat-card>
        <mat-card-header>
          <mat-card-title>Transaction {{ tx.id }}</mat-card-title>
          <mat-card-subtitle>{{ tx.date | date: 'mediumDate' }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <p>Type: {{ tx.type }}</p>
          <p>Amount: {{ tx.amount | number: '1.2-2' }}</p>
          <p>Running balance: {{ tx.runningBalance | number: '1.2-2' }}</p>
        </mat-card-content>
        <mat-card-actions>
          <a mat-button [routerLink]="['/savings', savingsId]">Back to account</a>
        </mat-card-actions>
      </mat-card>
    }
  `,
  styles: `
    :host {
      display: block;
      padding: 1rem;
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
