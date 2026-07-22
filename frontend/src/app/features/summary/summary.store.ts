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
import { SummaryQueryControllerService } from '@bff/client';

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
  private readonly summaryApi = inject(SummaryQueryControllerService);
  private readonly destroyRef = inject(DestroyRef);

  readonly savingsCards = signal<SavingsCard[]>([]);
  readonly loanCards = signal<LoanCard[]>([]);
  readonly loading = signal(false);

  load(): void {
    this.loading.set(true);
    this.summaryApi
      .getAccountsSummary()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: summary => {
          this.savingsCards.set(
            (summary.savings ?? [])
              .filter(item => item.id != null)
              .map(item => ({
                id: item.id!,
                accountNo: item.accountNo,
                productName: item.productName,
                currency: item.currency,
                balance: item.accountBalance ?? 0,
                availableBalance: item.availableBalance ?? 0,
              })),
          );
          this.loanCards.set(
            (summary.loans ?? [])
              .filter(item => item.id != null)
              .map(item => ({
                id: item.id!,
                accountNo: item.accountNo,
                productName: item.productName,
                currency: item.currency,
                totalOutstanding: item.loanBalance ?? 0,
              })),
          );
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }
}
