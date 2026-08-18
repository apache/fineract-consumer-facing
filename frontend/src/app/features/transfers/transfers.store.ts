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
  ConfirmTransferCommandRequest,
  InitiateTransferCommandRequest,
  TransferChallengeCommandData,
  TransferCommandData,
  TransferQueryData,
  TransfersCommandControllerService,
  TransfersQueryControllerService,
} from '@bff/client';
import { deviceFingerprint } from '../../core/auth/device-fingerprint';

@Injectable({ providedIn: 'root' })
export class TransfersStore {
  private readonly command = inject(TransfersCommandControllerService);
  private readonly query = inject(TransfersQueryControllerService);
  private readonly destroyRef = inject(DestroyRef);

  readonly challenge = signal<TransferChallengeCommandData | null>(null);
  readonly result = signal<TransferCommandData | null>(null);
  readonly history = signal<TransferQueryData[]>([]);
  readonly historyPage = signal(0);
  readonly historySize = signal(0);
  readonly historyTotalElements = signal(0);
  readonly historyTotalPages = signal(0);
  readonly historyLoading = signal(false);

  initiate(request: InitiateTransferCommandRequest): Observable<TransferChallengeCommandData> {
    return this.command
      .initiateTransfer(deviceFingerprint(), request)
      .pipe(tap((challenge) => this.challenge.set(challenge)));
  }

  confirm(
    idempotencyKey: string,
    request: ConfirmTransferCommandRequest,
  ): Observable<TransferCommandData> {
    return this.command.confirmTransfer(deviceFingerprint(), idempotencyKey, request).pipe(
      tap((result) => {
        this.result.set(result);
        this.challenge.set(null);
      }),
    );
  }

  loadHistory(page: number, size: number): void {
    this.history.set([]);
    this.historyLoading.set(true);
    this.query
      .listTransfers(undefined, undefined, page, size)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.history.set(response.content ?? []);
          this.historyPage.set(response.page ?? 0);
          this.historySize.set(response.size ?? 0);
          this.historyTotalElements.set(response.totalElements ?? 0);
          this.historyTotalPages.set(response.totalPages ?? 0);
          this.historyLoading.set(false);
        },
        error: () => this.historyLoading.set(false),
      });
  }
}
