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

import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  SavingsAccountListItemQueryData,
  SavingsAccountQueryData,
  SavingsApplicationTemplateQueryData,
  SavingsChargeQueryData,
  SavingsQueryControllerService,
  SavingsTransactionQueryData,
} from '@bff/client';

export interface TransactionFilter {
  fromDate?: string;
  toDate?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class SavingsStore {
  private readonly query = inject(SavingsQueryControllerService);
  private readonly destroyRef = inject(DestroyRef);

  readonly accounts = signal<SavingsAccountListItemQueryData[]>([]);
  readonly selected = signal<SavingsAccountQueryData | null>(null);
  readonly charges = signal<SavingsChargeQueryData[]>([]);
  readonly transactions = signal<SavingsTransactionQueryData[]>([]);
  readonly selectedTransaction = signal<SavingsTransactionQueryData | null>(null);
  readonly template = signal<SavingsApplicationTemplateQueryData | null>(null);
  readonly loading = signal(false);

  loadAccounts(): void {
    this.loading.set(true);
    this.query
      .listSavingsAccounts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: rows => {
          this.accounts.set(rows);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  loadAccount(savingsId: number): void {
    this.selected.set(null);
    this.loading.set(true);
    this.query
      .getSavingsAccount(savingsId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: account => {
          this.selected.set(account);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  loadCharges(savingsId: number): void {
    this.charges.set([]);
    this.query
      .getSavingsCharges(savingsId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: rows => this.charges.set(rows),
        error: () => this.charges.set([]),
      });
  }

  loadTransactions(savingsId: number, filter: TransactionFilter = {}): void {
    this.transactions.set([]);
    this.loading.set(true);
    this.query
      .searchSavingsTransactions(savingsId, filter.fromDate, filter.toDate, filter.page, filter.size)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: rows => {
          this.transactions.set(rows);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  loadTransaction(savingsId: number, transactionId: number): void {
    this.query
      .getSavingsTransaction(savingsId, transactionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(tx => this.selectedTransaction.set(tx));
  }

  loadTemplate(productId?: number): void {
    this.query
      .getSavingsApplicationTemplate(productId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(template => this.template.set(template));
  }
}
