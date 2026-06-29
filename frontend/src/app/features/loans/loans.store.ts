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
import { Observable, tap } from 'rxjs';
import {
  ConfirmLoanChargePaymentCommandRequest,
  LoanAccountListItemQueryData,
  LoanAccountQueryData,
  LoanApplicationCommandData,
  LoanApplicationTemplateQueryData,
  LoanChargePaymentChallengeCommandData,
  LoanChargePaymentCommandData,
  LoanChargeQueryData,
  LoanGuarantorQueryData,
  LoanScheduleQueryData,
  LoanSchedulePreviewQueryRequest,
  LoanTransactionQueryData,
  LoansCommandControllerService,
  LoansQueryControllerService,
  ModifyLoanApplicationCommandRequest,
  SubmitLoanApplicationCommandRequest,
  WithdrawLoanApplicationCommandRequest,
} from '@bff/client';
import { deviceFingerprint } from '../../core/auth/device-fingerprint';

@Injectable({ providedIn: 'root' })
export class LoansStore {
  private readonly query = inject(LoansQueryControllerService);
  private readonly command = inject(LoansCommandControllerService);
  private readonly destroyRef = inject(DestroyRef);

  readonly loans = signal<LoanAccountListItemQueryData[]>([]);
  readonly selected = signal<LoanAccountQueryData | null>(null);
  readonly charges = signal<LoanChargeQueryData[]>([]);
  readonly guarantors = signal<LoanGuarantorQueryData[]>([]);
  readonly transactions = signal<LoanTransactionQueryData[]>([]);
  readonly selectedTransaction = signal<LoanTransactionQueryData | null>(null);
  readonly template = signal<LoanApplicationTemplateQueryData | null>(null);
  readonly schedulePreview = signal<LoanScheduleQueryData | null>(null);
  readonly draft = signal<LoanApplicationCommandData | null>(null);
  readonly chargePaymentChallenge = signal<LoanChargePaymentChallengeCommandData | null>(null);
  readonly loading = signal(false);

  loadLoans(): void {
    this.loading.set(true);
    this.query
      .listLoanAccounts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: rows => {
          this.loans.set(rows);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  loadLoan(loanId: number): void {
    this.selected.set(null);
    this.loading.set(true);
    this.query
      .getLoanAccount(loanId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: account => {
          this.selected.set(account);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  loadCharges(loanId: number): void {
    this.charges.set([]);
    this.query
      .getLoanCharges(loanId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: rows => this.charges.set(rows),
        error: () => this.charges.set([]),
      });
  }

  loadGuarantors(loanId: number): void {
    this.guarantors.set([]);
    this.query
      .getLoanGuarantors(loanId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: rows => this.guarantors.set(rows),
        error: () => this.guarantors.set([]),
      });
  }

  loadTransactions(loanId: number): void {
    this.transactions.set([]);
    this.query
      .listLoanTransactions(loanId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: rows => this.transactions.set(rows),
        error: () => this.transactions.set([]),
      });
  }

  loadTransaction(loanId: number, transactionId: number): void {
    this.query
      .getLoanTransaction(loanId, transactionId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(tx => this.selectedTransaction.set(tx));
  }

  loadTemplate(productId?: number): void {
    this.query
      .getLoanApplicationTemplate(productId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(template => this.template.set(template));
  }

  previewSchedule(request: LoanSchedulePreviewQueryRequest): Observable<LoanScheduleQueryData> {
    return this.query
      .previewLoanSchedule(request)
      .pipe(tap(schedule => this.schedulePreview.set(schedule)));
  }

  submit(request: SubmitLoanApplicationCommandRequest): Observable<LoanApplicationCommandData> {
    return this.command.submitLoanApplication(request).pipe(tap(draft => this.draft.set(draft)));
  }

  modify(
    loanId: number,
    request: ModifyLoanApplicationCommandRequest,
  ): Observable<LoanApplicationCommandData> {
    return this.command
      .modifyLoanApplication(loanId, request)
      .pipe(tap(draft => this.draft.set(draft)));
  }

  withdraw(
    loanId: number,
    request: WithdrawLoanApplicationCommandRequest,
  ): Observable<LoanApplicationCommandData> {
    return this.command
      .withdrawLoanApplication(loanId, 'withdraw', request)
      .pipe(tap(() => this.draft.set(null)));
  }

  initiateChargePayment(
    loanId: number,
    chargeId: number,
  ): Observable<LoanChargePaymentChallengeCommandData> {
    return this.command
      .initiateLoanChargePayment(deviceFingerprint(), loanId, chargeId)
      .pipe(tap(challenge => this.chargePaymentChallenge.set(challenge)));
  }

  confirmChargePayment(
    loanId: number,
    chargeId: number,
    request: ConfirmLoanChargePaymentCommandRequest,
  ): Observable<LoanChargePaymentCommandData> {
    return this.command
      .confirmLoanChargePayment(deviceFingerprint(), loanId, chargeId, request)
      .pipe(tap(() => this.chargePaymentChallenge.set(null)));
  }
}
