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
import { Observable, forkJoin, map, of, switchMap } from 'rxjs';
import {
  LoanAccountListItemQueryData,
  LoansQueryControllerService,
  SavingsAccountListItemQueryData,
  SavingsQueryControllerService,
} from '@bff/client';

export interface SavingsCard {
  id: number;
  accountNo?: string;
  productName?: string;
  currency?: string;
  balance?: number;
  availableBalance?: number;
}

export interface LoanCard {
  id: number;
  accountNo?: string;
  productName?: string;
  currency?: string;
  totalOutstanding?: number;
}

@Injectable({ providedIn: 'root' })
export class SummaryStore {
  private readonly savingsApi = inject(SavingsQueryControllerService);
  private readonly loansApi = inject(LoansQueryControllerService);
  private readonly destroyRef = inject(DestroyRef);

  readonly savingsCards = signal<SavingsCard[]>([]);
  readonly loanCards = signal<LoanCard[]>([]);
  readonly loading = signal(false);

  load(): void {
    this.loading.set(true);
    forkJoin({ savings: this.loadSavingsCards(), loans: this.loadLoanCards() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ savings, loans }) => {
          this.savingsCards.set(savings);
          this.loanCards.set(loans);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  private loadSavingsCards(): Observable<SavingsCard[]> {
    return this.savingsApi.listSavingsAccounts().pipe(
      switchMap(list => {
        const withId = list.filter(
          (item): item is SavingsAccountListItemQueryData & { id: number } => item.id != null,
        );
        if (withId.length === 0) {
          return of<SavingsCard[]>([]);
        }
        return forkJoin(
          withId.map(item =>
            this.savingsApi.getSavingsAccount(item.id).pipe(
              map(detail => ({
                id: item.id,
                accountNo: item.accountNo,
                productName: item.productName,
                currency: item.currency,
                balance: detail.balance,
                availableBalance: detail.availableBalance,
              })),
            ),
          ),
        );
      }),
    );
  }

  private loadLoanCards(): Observable<LoanCard[]> {
    return this.loansApi.listLoanAccounts().pipe(
      switchMap(list => {
        const withId = list.filter(
          (item): item is LoanAccountListItemQueryData & { id: number } => item.id != null,
        );
        if (withId.length === 0) {
          return of<LoanCard[]>([]);
        }
        return forkJoin(
          withId.map(item =>
            this.loansApi.getLoanAccount(item.id).pipe(
              map(detail => ({
                id: item.id,
                accountNo: item.accountNo,
                productName: item.productName,
                currency: item.currency,
                totalOutstanding: detail.totalOutstanding,
              })),
            ),
          ),
        );
      }),
    );
  }
}
