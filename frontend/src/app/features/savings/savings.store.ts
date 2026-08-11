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
import { byAccountNo } from '../../shared/utils/account-sort';

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
  readonly transactionsPage = signal(0);
  readonly transactionsSize = signal(0);
  readonly transactionsTotalElements = signal(0);
  readonly transactionsTotalPages = signal(0);
  readonly selectedTransaction = signal<SavingsTransactionQueryData | null>(null);
  readonly template = signal<SavingsApplicationTemplateQueryData | null>(null);
  readonly loading = signal(false);

  loadAccounts(): void {
    this.accounts.set([]);
    this.loading.set(true);
    this.query
      .listSavingsAccounts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.accounts.set([...rows].sort(byAccountNo));
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
        next: (account) => {
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
        next: (rows) => this.charges.set(rows),
        error: () => {},
      });
  }

  loadTransactions(savingsId: number, filter: TransactionFilter = {}): void {
    this.transactions.set([]);
    this.loading.set(true);
    this.query
      .searchSavingsTransactions(
        savingsId,
        filter.fromDate,
        filter.toDate,
        filter.page,
        filter.size,
      )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.transactions.set(response.content ?? []);
          this.transactionsPage.set(response.page ?? 0);
          this.transactionsSize.set(response.size ?? 0);
          this.transactionsTotalElements.set(response.totalElements ?? 0);
          this.transactionsTotalPages.set(response.totalPages ?? 0);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  loadTransaction(savingsId: number, transactionId: number): void {
    this.selectedTransaction.set(null);
    this.query
      .getSavingsTransaction(savingsId, transactionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (tx) => this.selectedTransaction.set(tx),
        error: () => {},
      });
  }

  loadTemplate(productId?: number): void {
    this.template.set(null);
    this.query
      .getSavingsApplicationTemplate(productId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (template) => this.template.set(template),
        error: () => {},
      });
  }
}
