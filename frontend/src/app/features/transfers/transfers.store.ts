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

import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  ConfirmTransferCommandRequest,
  InitiateTransferCommandRequest,
  TransferChallengeCommandData,
  TransferCommandData,
  TransfersCommandControllerService,
} from '@bff/client';
import { deviceFingerprint } from '../../core/auth/device-fingerprint';

@Injectable({ providedIn: 'root' })
export class TransfersStore {
  private readonly command = inject(TransfersCommandControllerService);

  readonly challenge = signal<TransferChallengeCommandData | null>(null);
  readonly result = signal<TransferCommandData | null>(null);

  initiate(request: InitiateTransferCommandRequest): Observable<TransferChallengeCommandData> {
    return this.command
      .initiateTransfer(deviceFingerprint(), request)
      .pipe(tap(challenge => this.challenge.set(challenge)));
  }

  confirm(request: ConfirmTransferCommandRequest): Observable<TransferCommandData> {
    return this.command.confirmTransfer(deviceFingerprint(), request).pipe(
      tap(result => {
        this.result.set(result);
        this.challenge.set(null);
      }),
    );
  }
}
