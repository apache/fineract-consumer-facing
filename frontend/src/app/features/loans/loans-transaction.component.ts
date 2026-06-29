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
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { LoansStore } from './loans.store';

@Component({
  selector: 'app-loans-transaction',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatCardModule, RouterLink, CurrencyPipe, DatePipe],
  template: `
    @if (store.selectedTransaction(); as tx) {
      <mat-card>
        <mat-card-header>
          <mat-card-title>Transaction {{ tx.id }}</mat-card-title>
          <mat-card-subtitle>{{ tx.date | date: 'mediumDate' }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <p>Type: {{ tx.type }}</p>
          <p>Amount: {{ tx.amount | currency: tx.currency }}</p>
          <p>Outstanding loan balance: {{ tx.outstandingLoanBalance | currency: tx.currency }}</p>
        </mat-card-content>
        <mat-card-actions>
          <a mat-button [routerLink]="['/loans', loanId]">Back to loan</a>
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
export class LoansTransactionComponent {
  private readonly route = inject(ActivatedRoute);
  protected readonly store = inject(LoansStore);

  protected readonly loanId = Number(this.route.snapshot.paramMap.get('loanId'));

  constructor() {
    const transactionId = Number(this.route.snapshot.paramMap.get('transactionId'));
    this.store.loadTransaction(this.loanId, transactionId);
  }
}
