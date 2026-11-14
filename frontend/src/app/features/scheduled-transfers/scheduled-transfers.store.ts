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

import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, catchError, of, tap, throwError } from 'rxjs';
import { ConsumerApiError } from '../../api/consumer-api-error';
import {
  ConfirmCancelScheduledTransferCommandRequest,
  ConfirmScheduledTransferCommandRequest,
  InitiateScheduledTransferCommandRequest,
  SCHEDULED_TRANSFER_API,
  SCHEDULED_TRANSFERS_DISABLED_CODE,
  ScheduledTransferChallengeCommandData,
  ScheduledTransferCommandData,
  ScheduledTransferQueryData,
  ScheduledTransferRunQueryData,
} from '../../core/adapters';
import { AuditService } from '../../core/audit/audit.service';

function isDisabled(error: unknown): boolean {
  return (
    error instanceof HttpErrorResponse &&
    (error.error as ConsumerApiError | null)?.code === SCHEDULED_TRANSFERS_DISABLED_CODE
  );
}

@Injectable({ providedIn: 'root' })
export class ScheduledTransfersStore {
  private readonly api = inject(SCHEDULED_TRANSFER_API);
  private readonly audit = inject(AuditService);
  private readonly destroyRef = inject(DestroyRef);

  readonly transfers = signal<ScheduledTransferQueryData[]>([]);
  /** True once a load has answered, so "not in the list" can be told apart from "not loaded". */
  readonly loaded = signal(false);
  /** The BFF said this deployment has scheduled transfers turned off. */
  readonly disabled = signal(false);
  readonly challenge = signal<ScheduledTransferChallengeCommandData | null>(null);
  readonly result = signal<ScheduledTransferCommandData | null>(null);

  readonly runs = signal<ScheduledTransferRunQueryData[]>([]);
  readonly runsPage = signal(0);
  readonly runsSize = signal(0);
  readonly runsTotalElements = signal(0);
  readonly runsTotalPages = signal(0);
  readonly runsLoading = signal(false);

  /**
   * Loads the active scheduled transfers. A "turned off" answer is not an error here: it sets
   * `disabled` and completes with an empty list.
   */
  load(): Observable<ScheduledTransferQueryData[]> {
    return this.api.list().pipe(
      tap((rows) => {
        this.transfers.set(rows);
        this.disabled.set(false);
        this.loaded.set(true);
      }),
      catchError((error: unknown) => {
        if (!isDisabled(error)) {
          return throwError(() => error);
        }
        this.transfers.set([]);
        this.disabled.set(true);
        this.loaded.set(true);
        return of([]);
      }),
    );
  }

  initiate(
    request: InitiateScheduledTransferCommandRequest,
  ): Observable<ScheduledTransferChallengeCommandData> {
    this.audit.record('SENSITIVE_ACTION', { action: 'SCHEDULED_TRANSFER_CREATE' });
    return this.api.initiate(request).pipe(tap((challenge) => this.challenge.set(challenge)));
  }

  confirm(
    idempotencyKey: string,
    request: ConfirmScheduledTransferCommandRequest,
  ): Observable<ScheduledTransferCommandData> {
    return this.api.confirm(idempotencyKey, request).pipe(
      tap((result) => {
        this.result.set(result);
        this.challenge.set(null);
      }),
    );
  }

  initiateCancel(publicId: string): Observable<ScheduledTransferChallengeCommandData> {
    this.audit.record('SENSITIVE_ACTION', { action: 'SCHEDULED_TRANSFER_CANCEL' });
    return this.api
      .initiateCancel(publicId)
      .pipe(tap((challenge) => this.challenge.set(challenge)));
  }

  confirmCancel(
    publicId: string,
    idempotencyKey: string,
    request: ConfirmCancelScheduledTransferCommandRequest,
  ): Observable<unknown> {
    return this.api.confirmCancel(publicId, idempotencyKey, request).pipe(
      tap(() => {
        this.challenge.set(null);
        this.transfers.update((rows) => rows.filter((row) => row.publicId !== publicId));
      }),
    );
  }

  loadRuns(publicId: string, page: number, size: number): void {
    this.runs.set([]);
    this.runsLoading.set(true);
    this.api
      .runs(publicId, page, size)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.runs.set(result.content);
          this.runsPage.set(result.page);
          this.runsSize.set(result.size);
          this.runsTotalElements.set(result.totalElements);
          this.runsTotalPages.set(result.totalPages);
          this.runsLoading.set(false);
        },
        error: () => this.runsLoading.set(false),
      });
  }
}
